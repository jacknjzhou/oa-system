# 假期模块（holiday）实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 subagent-driven-development（推荐）或 executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 按 `docs/zhidieyun_oa_requirements.md` 第 5 章（HD-01~HD-08）补齐假期模块：假期管理（部门树+员工列表）、员工假期余额页（编辑剩余时长/假期时长/日志）、假期类型 CRUD、授予留痕、导出、三时长单位、9 类预置、与请假审批联动，并按 4.3.0/5.4.1 重构为「考勤&假期」五 Tab 导航。

**架构：** 后端在现有三账本（`leave_type` / `leave_balance` / `leave_transaction`）上做增量：V22 迁移给类型加 `unit`、给流水加操作人/类型/备注/关联实例；`LeaveService` 增加管理员查询与两类绝对值授予（setRemaining/setQuota）；新增假期类型 CRUD 端点与 `leave:manage` 权限守卫。前端新增三页面（假期管理/余额详情/假期类型）并重构导航。所有余额数字均为 INT，语义 = 该类型的 `unit`（天/小时/半天），不跨单位换算。

**技术栈：** Spring Boot 3.2 / Flowable / JPA / Flyway（common 迁移，H2+MySQL 双兼容）；React 18 + Vite + Tailwind（无前端测试框架，用 tsc + E2E 把关）。

**规格：** `docs/zhidieyun_oa_requirements.md` 第 5 章（358-541 行）、第 4.3.0（330-332 行）、第 8 章规则 9/10/11。

## 现状与差距

| 需求 | 现状 | 差距 |
|---|---|---|
| HD-01 假期管理（部门树+员工表+查看） | ❌ 无 | 全缺 |
| HD-02 员工余额页（时长/已用/剩余/单位/三按钮） | 部分（仅本人、仅天） | 管理员视角 + 单位 + 三按钮 |
| HD-03 余额日志（操作人/接受人/类型/说明/备注/关联流程） | 部分（delta/reason/ref） | 缺操作人/类型/备注/实例链接 |
| HD-04~06 类型 CRUD | ❌ 无（6 类种子，无管理页） | 全缺 |
| HD-07 授予/调整（编辑剩余时长/假期时长，不限额禁止授予） | 部分（grant 增量、仅本人） | 绝对值语义 + 任意员工 + 不限额拦截 |
| HD-08 导出 | 部分（本人流水 CSV） | 管理列表导出 |
| 9 类预置（限额/权重/单位） | 6 类，权重 100+ 递增 | 需重排 + 补 4 类 + 单位 |
| 单位（天/小时/半天） | 无（INT 天） | 类型 unit + 表单动态单位 |
| 类型→审批联动（表单选项） | 种子硬编码 6 选项 | 动态取 enable 类型 |
| 五 Tab 导航 | 两个独立导航项 | 重构 |

## 已定决策（Rulings，执行时不再讨论）

1. **单位语义**：`leave_balance` 的 quota/used/frozen 与请假时长（businessData 的 `days` 键）均为 INT，含义 = 该类型 `unit`（`day`/`hour`/`halfDay`）。预置类型中唯一有限额的年假=天；事假/调休=小时但**不限额**（只记流水不校验余额）；病假=半天且不限额。因此余额计算不会出现小数。UI 文案：天/小时/半天。
2. **预置 10 类**（9 规格类 + 保留特休）：`ANNUAL` 年假 fixed day 99 / `PERSONAL` 事假 none hour 98 / `SICK` 病假 none halfDay 97 / `COMP` 调休假 none hour 96（新）/ `MARRIAGE` 婚假 none day 95 / `MATERNITY` 产假 none day 94（新）/ `PATERNAL` 陪产假 none day 93 / `BEREAVEMENT` 丧假 none day 92（新）/ `REGULAR` 例假 none day 0（新）/ `SPECIAL` 特殊假 none day 91（保留，公司类）。种子每次启动幂等同步（缺则建、内置码更新 unit/weight/quotaType）；演示授额改为**仅 ANNUAL 10**（事假转不限额后取消 SEED-PERSONAL 授予）。
3. **权重排序**：`findByEnabledTrueOrderByWeightDescIdDesc`（99 在前，规格 5.4.3 ②）。`toTypeMap` 增加 `unit`/`weight`/`enabled` 字段。
4. **授予语义（HD-07）**：`setRemaining` = 设定目标剩余（`quota = amount + used + frozen`，保证 available 恰等于输入值）；`setQuota` = 设定总额度。均要求 `amount ≥ 0`；`quotaType=none` 的类型 `setRemaining` 返回 400「不限额类型不支持授予剩余时长」，`setQuota` 允许（规格分支 5a）。
5. **流水字段**：V22 给 `leave_transaction` 加 `operator_id`/`txn_type`/`remark`/`instance_id`。`txn_type` 取值：`remaining`（编辑剩余时长）/ `quota`（假期时长设定）/ `consume`（审批扣减）/ `reverse`（冲销）/ `freeze` / `release` / `adjust`（存量默认）。说明文案由服务端生成，含单位（如「审批通过，扣减 8 小时」）。审批触发时 `instance_id` + 备注=实例标题，前端渲染「查看关联流程」链接。
6. **请假联动零破坏**：businessData 仍用 `days` 键；`parseLeaveRequest` 不变；单位差异由 `LeaveService` 按类型 unit 体现在文案与校验。存量在途实例（天单位）升级后照常处理。
7. **类型 CRUD**：表单 4 字段必填（名称/限额是-否/时长单位/权重）；`code` 自动生成 `C` + 自增 id；删除时若存在 `leave_balance`（used/quota/frozen 任一 >0）或任意 `leave_transaction` → 400「该假期类型已有余额/流水数据，禁止删除」，否则物理删除。
8. **权限**：管理端点（员工列表/余额/授予/类型 CRUD）后端校验 `leave:manage`（PermissionSeeder 已种），403 拒绝；前端路由用 `ProtectedRoute perm` 与导航可见性。非管理员访问 `/leave` 时渲染原「我的假期」自视图（内容按权限切换，保留 P3 功能）。
9. **导航**：AppShell 两个导航项（/attendance、/leave）合并为「考勤&假期」→ 五 Tab（我的考勤 / 全部考勤 / 考勤设置 / 假期管理 / 假期类型）。CK-02/CK-03 属考勤模块、不在本计划：**全部考勤 = 简化只读版**（月份+人员筛选，无状态/IP 列，列为考勤后续计划）；**考勤设置 = 占位页**（EmptyState「规划中」）。
10. **部门**：V22 幂等补 4 个部门（市场部 MARKETING / 运营部 OPERATIONS / 行政部 ADMIN_DEPT / 人事部 HR，沿用 V3 的 `SELECT ... FROM DUAL WHERE NOT EXISTS` 模式）；**不改动**现有用户的 org 归属（演示用户留在技术部）。
11. **范围边界**：5.5 联动规则 2 中「流程条件面板复选组（3.4.5）」属审批设置模块（AS-05 条件分支），**不在本计划**；本计划仅覆盖表单「请假类型」下拉的同源联动（验收 9 的余额页/筛选下拉/表单选项三处）。CK-02/CK-03 完整能力（状态分类/IP/设置持久化）同属考勤后续计划，本计划仅交付全部考勤简化版与占位页。

## 文件结构

| 文件 | 职责 |
|---|---|
| `backend/src/main/resources/db/migration/common/V22__leave_v2.sql` | unit 列 + 流水 4 列 + 4 部门种子 |
| `backend/src/main/java/com/oa/entity/LeaveType.java` | + `unit` 字段 |
| `backend/src/main/java/com/oa/entity/LeaveTransaction.java` | + operatorId/txnType/remark/instanceId |
| `backend/src/main/java/com/oa/repository/LeaveTypeRepository.java` | 权重降序查询（替换升序方法） |
| `backend/src/main/java/com/oa/repository/LeaveBalanceRepository.java` | + 按类型统计（删除保护用） |
| `backend/src/main/java/com/oa/repository/LeaveTransactionRepository.java` | + countByLeaveTypeId / 管理员流水查询 |
| `backend/src/main/java/com/oa/config/LeaveBalanceSeeder.java` | 10 类幂等同步 + 仅 ANNUAL 演示授额 |
| `backend/src/main/java/com/oa/service/LeaveService.java` | 管理查询 / setRemaining / setQuota / 流水留痕 / 单位文案 |
| `backend/src/main/java/com/oa/service/LeaveTypeService.java`（新） | 类型 CRUD + 删除保护 |
| `backend/src/main/java/com/oa/controller/LeaveController.java` | 类型 CRUD + admin 端点 + leave:manage 守卫 |
| `backend/src/main/java/com/oa/controller/CheckController.java` | + GET /api/check/all |
| `backend/src/main/java/com/oa/service/CheckService.java` | + listAll(month, userId) |
| `backend/src/main/java/com/oa/config/ProcessTemplateSeeder.java` | 请假表单 optionsFrom/unitFrom + 存量 formConfig 修复 |
| `frontend/src/pages/LeaveManagement.tsx`（新） | HD-01：部门树 + 员工表 + 筛选 + 导出 |
| `frontend/src/pages/LeaveBalanceDetail.tsx`（新） | HD-02/03/07：余额表 + 三按钮 + 日志 |
| `frontend/src/pages/LeaveTypes.tsx`（新） | HD-04~06：类型列表 + 创建/编辑/删除 |
| `frontend/src/pages/MyLeave.tsx`（由 Leave.tsx 移动改造） | 非管理员的 /leave 内容 |
| `frontend/src/pages/Attendance.tsx` | 内容不动（我的考勤 Tab） |
| `frontend/src/pages/AllAttendance.tsx`（新） | 简化全部考勤 |
| `frontend/src/components/AttendanceTabs.tsx`（新） | 五 Tab 栏 |
| `frontend/src/components/AppShell.tsx` | 导航合并 |
| `frontend/src/App.tsx` | 路由 |
| `frontend/src/components/DynamicForm.tsx` | optionsFrom/unitFrom |
| `frontend/src/api/leave.ts` / `api/check.ts` / `types/index.ts` | 端点 + 类型 |

## 全局约束

- Flyway 新版本 = **V22**；`FlywayBootstrapTest` 断言更新为 `"22"`。
- common 迁移必须 H2 2.x + MySQL 8 双兼容：无 ENGINE/CHARSET、裸 `TINYINT`、独立 `CREATE INDEX`；部门种子沿用 V3 的 `SELECT ... FROM DUAL WHERE NOT EXISTS` 幂等模式。
- unit 取值 `day`/`hour`/`halfDay`（后端原值），UI 显示 天/小时/半天。
- txn_type 取值：`remaining`/`quota`/`consume`/`reverse`/`freeze`/`release`/`adjust`。
- businessData 的 `days` 键名不变；`parseLeaveRequest` 不改。
- 后端测试必须从 `/tmp/oa-backend-test` 副本跑（Synology 目录限制）：`rsync -a --delete --exclude target --exclude data.sql <root>/backend/ /tmp/oa-backend-test/backend/ && cd /tmp/oa-backend-test/backend && rm -rf target && docker run --rm -v $PWD:/app -v $HOME/.m2/repository:/root/.m2/repository -w /app maven:3.9-eclipse-temurin-21 mvn test`（timeout 420s）。
- H2 测试库跨测试类共享（`DB_CLOSE_DELAY=-1`）：断言用 code 过滤/前后差值，不得断言全表总数；直接起流程的测试必须完成/撤回任务并 `deleteDeployment(id, true)`。
- 前端门槛：`cd frontend && npx tsc --noEmit` 零错误。
- 部署：`./build.sh all`（rsync 到 APFS → build → up），禁止在云同步目录直接 `up --build`。
- 权限码 `leave:manage`/`leave:view`/`leave:apply` 已由 PermissionSeeder 播种，勿改码值。

## 审查重点（Review Focus）

1. **非管理员越权**：employee 角色调用 admin 端点（授予/改额度/删类型）→ 必须 403（任务 2/3 各加一例）。
2. **在途请假（frozen）时编辑剩余时长**：`setRemaining` 重算 `quota = amount + used + frozen`，available 恰等于输入且审批流转不超扣（任务 3 测试）。
3. **不限额类型（小时/半天）请假**：不校验余额、不报超扣、流水带正确单位（任务 3 测试）。
4. **删除有数据的假期类型** → 400 且行未变；无数据 → 成功（任务 2 测试）。
5. **存量天单位在途实例升级后审批** → 扣减/流水正常（任务 4 复用 LeaveProcessTests 断言）。

---

### 任务 1：数据模型 V22 + 9 类种子同步

**文件：**
- 创建：`backend/src/main/resources/db/migration/common/V22__leave_v2.sql`
- 修改：`backend/src/main/java/com/oa/entity/LeaveType.java`、`LeaveTransaction.java`
- 修改：`backend/src/main/java/com/oa/repository/LeaveTypeRepository.java`、`LeaveBalanceRepository.java`、`LeaveTransactionRepository.java`
- 修改：`backend/src/main/java/com/oa/config/LeaveBalanceSeeder.java`
- 修改：`backend/src/test/java/com/oa/FlywayBootstrapTest.java`（断言 `"21"` → `"22"`）
- 修改：`backend/src/test/java/com/oa/LeaveTests.java`

- [ ] **步骤 1：更新失败的测试**

改 `LeaveTests.seeded_leave_types_and_demo_balances`：

```java
List<Map<String, Object>> types = leaveService.listTypes();
var codes = types.stream().map(t -> (String) t.get("code")).toList();
// 9 规格类 + 特休，共 10 类启用
assertTrue(codes.containsAll(List.of("ANNUAL","PERSONAL","SICK","COMP","MARRIAGE","MATERNITY","PATERNAL","BEREAVEMENT","REGULAR","SPECIAL")));
// 权重降序：99 在前
assertEquals("ANNUAL", types.get(0).get("code"));
Map<String, Object> annual = types.stream().filter(t -> "ANNUAL".equals(t.get("code"))).findFirst().orElseThrow();
assertEquals("fixed", annual.get("quotaType"));
assertEquals("day", annual.get("unit"));
assertEquals(99, ((Number) annual.get("weight")).intValue());
Map<String, Object> sick = types.stream().filter(t -> "SICK".equals(t.get("code"))).findFirst().orElseThrow();
assertEquals("halfDay", sick.get("unit"));
assertEquals("none", sick.get("quotaType"));
// 演示余额：仅年假（10 天）
Map<String, Object> balance = leaveService.balance(1L, "ANNUAL");
assertEquals(10, ((Number) balance.get("quota")).intValue());
```

（`listTypes` 的 map 需含 unit/weight —— 见步骤 3。）另加一例：

```java
@Test
void unit_and_txn_fields_persisted() {
    leaveService.grant(1L, "ANNUAL", 1, "单元测试", "TXN-FIELDS-1");
    // 通过 ledger 验证流水携带新字段（ledger 的 transactions 元素含 txnType/operatorId/remark/instanceId 键，值可为 null）
    List<Map<String, Object>> ledger = leaveService.ledger(1L, "ANNUAL");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> txns = (List<Map<String, Object>>) ledger.get(0).get("transactions");
    Map<String, Object> t = txns.stream().filter(x -> "TXN-FIELDS-1".equals(x.get("refInstanceNo"))).findFirst().orElseThrow();
    assertNotNull(t.get("txnType"));
}
```

- [ ] **步骤 2：运行确认 RED**

运行全量（命令见全局约束）。预期：`seeded_leave_types_and_demo_balances` FAIL（9 类/降序/unit 未实现）；`unit_and_txn_fields_persisted` FAIL。

- [ ] **步骤 3：实现**

V22__leave_v2.sql：

```sql
ALTER TABLE leave_type ADD COLUMN unit VARCHAR(8) NOT NULL DEFAULT 'day';
ALTER TABLE leave_transaction ADD COLUMN operator_id BIGINT;
ALTER TABLE leave_transaction ADD COLUMN txn_type VARCHAR(16) NOT NULL DEFAULT 'adjust';
ALTER TABLE leave_transaction ADD COLUMN remark VARCHAR(256);
ALTER TABLE leave_transaction ADD COLUMN instance_id BIGINT;

INSERT INTO organization (org_code, org_name, org_type, sort_order, path, status, created_at, updated_at)
SELECT 'MARKETING', '市场部', 'DEPT', 2, '/1/', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM organization WHERE org_code = 'MARKETING');
INSERT INTO organization (org_code, org_name, org_type, sort_order, path, status, created_at, updated_at)
SELECT 'OPERATIONS', '运营部', 'DEPT', 3, '/1/', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM organization WHERE org_code = 'OPERATIONS');
INSERT INTO organization (org_code, org_name, org_type, sort_order, path, status, created_at, updated_at)
SELECT 'ADMIN_DEPT', '行政部', 'DEPT', 4, '/1/', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM organization WHERE org_code = 'ADMIN_DEPT');
INSERT INTO organization (org_code, org_name, org_type, sort_order, path, status, created_at, updated_at)
SELECT 'HR', '人事部', 'DEPT', 5, '/1/', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM organization WHERE org_code = 'HR');
```

实体：`LeaveType` + `private String unit = "day";`（`@Column(name = "unit", nullable = false, length = 8)`）；`LeaveTransaction` + `operatorId`（Long）、`txnType`（String，默认 `"adjust"`，length 16 not null）、`remark`（String，length 256）、`instanceId`（Long）。

`LeaveTypeRepository`：`findByEnabledTrueOrderByWeightAscIdAsc` **替换为** `findByEnabledTrueOrderByWeightDescIdDesc`。
`LeaveBalanceRepository` + `long countByLeaveTypeIdAndUsedGreaterThan0OrQuotaGreaterThan0OrFrozenGreaterThan0(Long leaveTypeId);`（Spring Data 解析 `Or` 嵌套易错，改为显式 `@Query`：`SELECT COUNT(1) FROM leave_balance b WHERE b.leave_type_id = :typeId AND (b.used > 0 OR b.quota > 0 OR b.frozen > 0)`）。
`LeaveTransactionRepository` + `long countByLeaveTypeId(Long leaveTypeId);` 与 `List<LeaveTransaction> findTop200ByUserIdAndLeaveTypeIdOrderByCreatedAtDescIdDesc`（已有则复用）。

`LeaveBalanceSeeder`：`TYPES` 改为 10 类（code/name/category/quotaType/unit 五元组，权重按 Ruling 2）；同步逻辑：内置码存在 → 更新 quotaType/unit/weight（name 不改）；不存在 → 创建。演示授额：仅 `ANNUAL` 10（保留 `SEED-ANNUAL` 幂等 ref）；删除 PERSONAL 授予。

`LeaveService` 最小配套：`toTypeMap` 加 `unit`/`weight`/`enabled`；`toBalanceMap` 加 `unit`；`recordTransaction` 增加 operator/txnType/remark/instanceId 参数（内部方法，调用方逐步在任务 3 补齐；本任务默认 operator=null、txnType 按调用点传 `remaining`/`consume`/`reverse`/`freeze`/`release`）；`ledger` 的 transactions 元素加 `txnType`/`operatorId`/`remark`/`instanceId` 键。`listTypes` 排序随仓储方法变降序。

- [ ] **步骤 4：运行确认 GREEN**（全量测试 + `FlywayBootstrapTest` 过）

- [ ] **步骤 5：Commit**

```bash
git add backend/ && git commit -m "feat(holiday): V22 时长单位/流水字段 + 9 类假期预置与权重降序"
```

### 任务 2：假期类型 CRUD（HD-04~06）

**文件：**
- 创建：`backend/src/main/java/com/oa/service/LeaveTypeService.java`
- 创建：`backend/src/main/java/com/oa/dto/LeaveTypeRequest.java`
- 修改：`backend/src/main/java/com/oa/controller/LeaveController.java`
- 测试：`backend/src/test/java/com/oa/LeaveTypeCrudTests.java`（新）

**接口（前后端契约，任务 5 依赖）：**
- `GET /api/leave/types?all=true` → 全部类型（含 disabled，管理列表用）；缺省 = 仅 enabled（表单下拉/余额页用）→ `[{id, code, name, category, quotaType, unit, annualQuota, weight, enabled}]`
- `POST /api/leave/types` body `LeaveTypeRequest{name, hasQuota, unit, weight}` → 200 DTO（code 自动生成 `C`+id）
- `PUT /api/leave/types/{id}` 同 body（code 不可改）
- `DELETE /api/leave/types/{id}` → 200 / 400（有数据）/ 403（无权限）

- [ ] **步骤 1：写失败测试** `LeaveTypeCrudTests`

```java
@SpringBootTest
class LeaveTypeCrudTests {
    // 注入 LeaveTypeService、LeaveController 依赖的 AuthService 模式（参照 LeaveTypeService 暴露 service 层测试 + 一例 controller 守卫测试）

    @Test
    void create_edit_and_list() {
        loginAs("admin");
        LeaveTypeRequest req = new LeaveTypeRequest();
        req.setName("育儿假"); req.setHasQuota(true); req.setUnit("hour"); req.setWeight(50);
        var created = leaveTypeService.create(req);
        assertTrue(created.getCode().startsWith("C"));
        assertEquals("hour", created.getUnit());
        assertEquals("fixed", created.getQuotaType()); // hasQuota 是 → fixed
        LeaveTypeRequest edit = new LeaveTypeRequest();
        edit.setName("育儿假(修)"); edit.setHasQuota(false); edit.setUnit("day"); edit.setWeight(51);
        assertEquals("none", leaveTypeService.update(created.getId(), edit).getQuotaType());
        // 列表含新类型（降序：weight 51 排在 0 的 REGULAR 前）
        var list = leaveTypeService.listAll();
        assertTrue(list.stream().anyMatch(t -> t.getId().equals(created.getId())));
        // 清理
        leaveTypeService.delete(created.getId());
    }

    @Test
    void delete_with_data_blocked() {
        loginAs("admin");
        leaveService.grant(1L, "ANNUAL", 1, "测试", "DEL-GUARD-1");
        long annualId = leaveTypeRepository.findByCode("ANNUAL").orElseThrow().getId();
        assertThrows(ResponseStatusException.class, () -> leaveTypeService.delete(annualId));
        assertTrue(leaveTypeRepository.findByCode("ANNUAL").isPresent());
    }

    @Test
    void non_admin_forbidden() {
        loginAs("employee");
        assertThrows(ResponseStatusException.class, () -> leaveTypeService.create(/* 任意 */));
    }
}
```

（守卫放在 service 层方法首行，controller 不重复 —— 与「controller 直接调 authService」现有惯例一致。）

- [ ] **步骤 2：运行确认 RED**

- [ ] **步骤 3：实现**

`LeaveTypeRequest`（`@Data`，字段 `name` `@NotBlank`/`hasQuota` `@NotNull` Boolean/`unit` `@NotBlank` 且校验 ∈ day,hour,halfDay/`weight` `@NotNull` Integer）。

`LeaveTypeService`：
- 注入 `LeaveTypeRepository`/`LeaveBalanceRepository`/`LeaveTransactionRepository`/`PermissionService`/`AuthService`
- 私有 `requireManage()`：当前用户无 `leave:manage` → 403（PermissionService 取权限码集合判断）
- `create`：requireManage；code = 临时占位保存后回填 `"C" + id`（两次 save）；category=`"company"`
- `update(id, req)`：requireManage；改 name/quotaType(由 hasQuota 映射)/unit/weight；code 不动
- `delete(id)`：requireManage；`countByLeaveTypeId`（balance 任一>0 的 @Query）+ `transactionRepository.countByLeaveTypeId` 任一 >0 → 400 Ruling 7 文案；否则删除
- `listAll()`：`findAll` 按 weight 降序 id 降序（含 disabled）

`LeaveController`：加 4 个端点（薄转发，DTO 用 Map 或复用 `toTypeMap` 结构）。

- [ ] **步骤 4：运行确认 GREEN**（含全量回归）

- [ ] **步骤 5：Commit** `git commit -m "feat(holiday): 假期类型 CRUD 与删除保护（HD-04~06）"`

### 任务 3：管理员假期账本 —— 余额查询 / 授予调整 / 流水（HD-02/03/07 + 单位）

**文件：**
- 修改：`backend/src/main/java/com/oa/service/LeaveService.java`
- 修改：`backend/src/main/java/com/oa/controller/LeaveController.java`
- 测试：`backend/src/test/java/com/oa/LeaveAdminTests.java`（新）

**接口契约：**
- `GET /api/leave/admin/employees?typeCode=&name=` → `[{id, username, realName, orgName, balances: BalanceRow[]}]`（name 模糊；typeCode 过滤 balances 行；仅 ACTIVE 用户）
- `BalanceRow = {code, name, unit, quota, used, frozen, remaining, unlimited}`（remaining = available；unlimited = quotaType==none）
- `POST /api/leave/admin/{userId}/balances/{typeCode}/remaining` body `{amount: int ≥0, remark?}` → BalanceRow
- `POST /api/leave/admin/{userId}/balances/{typeCode}/quota` 同上
- `GET /api/leave/admin/{userId}/transactions?typeCode=` → `[{id, code, name, unit, txnType, delta, description, remark, operatorName, recipientName, instanceId, createdAt}]`（typeCode 缺省 = 全部类型）

- [ ] **步骤 1：写失败测试** `LeaveAdminTests`

```java
@SpringBootTest
class LeaveAdminTests {
    // 注入 LeaveService / LeaveController 依赖，loginAs 模式同 FlowDesignerTests

    @Test
    void setRemaining_withFrozen_recomputesQuota() {
        loginAs("admin");
        leaveService.freeze(1L, "ANNUAL", 3, "FREEZE-X");        // frozen 3
        var row = leaveService.setRemaining(1L, "ANNUAL", 8, "校准");
        assertEquals(8, ((Number) row.get("remaining")).intValue());
        LeaveBalance b = leaveBalanceRepository.findByUserIdAndLeaveTypeId(1L, /*ANNUAL id*/).orElseThrow();
        assertEquals(8 + b.getUsed() + 3, b.getQuota());          // quota = 目标 + used + frozen
    }

    @Test
    void setRemaining_on_unlimited_type_rejected() {
        loginAs("admin");
        assertThrows(ResponseStatusException.class, () -> leaveService.setRemaining(1L, "SICK", 5, "x"));
    }

    @Test
    void setQuota_allowed_on_unlimited_type() {
        loginAs("admin");
        var row = leaveService.setQuota(1L, "PERSONAL", 20, "设定");
        assertEquals(20, ((Number) row.get("quota")).intValue());
    }

    @Test
    void adminLedger_carriesOperatorAndInstance() {
        loginAs("admin");
        leaveService.consume(3L, "ANNUAL", 2, "LEAVE-ADMIN-1", "请假审批通过");
        // 用新签名的 consume（operator/instanceId/remark）调用方见步骤 3；断言流水含 operatorName、txnType=consume、remark
        var txns = leaveService.adminLedger(3L, null);
        var t = txns.stream().filter(x -> "LEAVE-ADMIN-1".equals(x.get("refInstanceNo"))).findFirst().orElseThrow();
        assertEquals("consume", t.get("txnType"));
        assertNotNull(t.get("operatorName"));
    }

    @Test
    void employees_list_withOrgAndFilter() {
        loginAs("admin");
        var all = leaveService.adminEmployees(null, null);
        assertFalse(all.isEmpty());
        all.forEach(u -> assertNotNull(u.get("orgName")));
        var filtered = leaveService.adminEmployees(null, "admin");
        assertEquals(1, filtered.size());
    }

    @Test
    void non_admin_forbidden() {
        loginAs("employee");
        assertThrows(ResponseStatusException.class, () -> leaveService.setRemaining(1L, "ANNUAL", 1, "x"));
        assertThrows(ResponseStatusException.class, () -> leaveService.adminEmployees(null, null));
    }
}
```

- [ ] **步骤 2：运行确认 RED**

- [ ] **步骤 3：实现 `LeaveService` 管理段**

- `setRemaining(userId, typeCode, amount, remark)`：requireManage；`amount < 0` → 400；none 类型 → 400 Ruling 4 文案；`balance = ensureBalance`；`delta = amount - available()`；`quota += delta`；记流水 `txnType=remaining`，`description = "编辑剩余时长为 " + amount + unitLabel(type)`，remark 透传，operator = 当前用户；返回 BalanceRow
- `setQuota(...)`：同上，`quota = amount`，`txnType=quota`，description「设定假期时长为 N 单位」；none 类型允许
- `adminBalance(userId)` → `List<Map>`：全部 enabled 类型 × ensureBalance（**新入职自动出现行**）；map 键 = BalanceRow（`remaining` = available；unlimited 标记）
- `adminLedger(userId, typeCode?)`：类型列表 × `findTop200By...`；每条 map 键 = 接口契约所列；**`description` 取既有 `reason` 列**（服务端写流水时生成含单位文案，如「审批通过，扣减 8 小时」，存量流水为旧文案）；`remark` 取新列；operatorName/recipientName 由 `userRepository.findAllById` 批量解析（Map 缓存，避免 N+1）
- `adminEmployees(typeCode?, name?)`：ACTIVE 用户；orgName = `u.getOrg() != null ? u.getOrg().getOrgName() : null`；balances = adminBalance（typeCode 过滤）
- **单位文案**：私有 `unitLabel(type)`：day→天 / hour→小时 / halfDay→半天；`consume`/`reverse`/`freeze`/`release` 的既有文案（「天」硬编码处）改为 `unitLabel`；`consume` 的 reason 参数调用方仍传「请假审批通过」，流水 description 由服务端拼 `unitLabel`
- **签名扩展**（内部方法加参，保持旧调用兼容用重载）：`consume(userId, codeOrLabel, amount, ref, reason, operatorId, instanceId)`；`freeze/release` 同理加 operatorId/instanceId；`recordTransaction` 加 txnType/operatorId/remark/instanceId 参数。`applyLeaveLedger`（ProcessService L759）调用点改传 approver/实例 id + remark=实例 title —— 属本任务（联动不破坏：days 键不变）
- 守卫私有方法 `requireManage()`（同任务 2 模式）

`LeaveController` 加 5 个 admin 端点（薄转发；DTO record：`SetRemainingRequest{int amount, String remark}` 等）。

- [ ] **步骤 4：运行确认 GREEN**（全量；`LeaveTests` 三例与 `LeaveProcessTests` 三例须保持绿 —— 签名重载保证）

- [ ] **步骤 5：Commit** `git commit -m "feat(holiday): 管理员假期账本——余额查询/编辑剩余时长/假期时长/流水留痕（HD-02/03/07）"`

### 任务 4：请假审批联动 —— 动态类型选项 + 单位

**文件：**
- 修改：`backend/src/main/java/com/oa/config/ProcessTemplateSeeder.java`（请假表单 formConfig + 存量修复）
- 修改：`frontend/src/components/DynamicForm.tsx`
- 修改：`frontend/src/api/leave.ts`（types 返回类型加 unit/weight/enabled）
- 修改：`frontend/src/types/index.ts`（Field 加 `optionsFrom?`/`unitFrom?`）
- 测试：`backend/src/test/java/com/oa/LeaveProcessTests.java`（扩展）

- [ ] **步骤 1：写失败测试**（LeaveProcessTests 加两例；登录/起流程/清理模式沿用该文件既有助手）

```java
@Test
void unlimited_hour_leave_only_records() {
    loginAs("employee");
    startLeave("事假", 8); // 内部：defKey=leave，businessData {"leaveType":"事假","days":"8","reason":"x"}
    // 发起成功（事假=none 不校验余额）
    // 主管（manager）审批通过
    // 断言：事假流水 1 条 txnType=consume、delta=-8；事假余额行 used/quota 保持 0；流水文案含「小时」
}

@Test
void leave_form_options_are_dynamic() {
    // 经 ProcessService.getDefinition 取 leave 定义 formConfig：leaveType 字段 optionsFrom="leaveTypes"；days 字段 unitFrom="leaveType" 且 label="请假时长"
}
```

- [ ] **步骤 2：运行确认 RED**

- [ ] **步骤 3：实现**

- Seeder：`seedLeaveTemplate` 的 formConfig 改为：

```json
{"fields":[
  {"key":"leaveType","label":"请假类型","type":"radio","required":true,"optionsFrom":"leaveTypes"},
  {"key":"leaveRange","label":"请假时段","type":"dateRange","required":true},
  {"key":"days","label":"请假时长","type":"number","required":true,"min":0,"max":60,"unitFrom":"leaveType"},
  {"key":"reason","label":"请假事由","type":"textarea","required":true,"placeholder":"请说明请假原因"}
]}
```

  `run()` 中对已存在 leave 定义做 formConfig 修复（幂等：仅当 leaveType 字段无 `optionsFrom` 时替换该字段配置，参照既有 reimbursement form heal 模式）。
- `DynamicForm`：
  - 字段 `optionsFrom === 'leaveTypes'`：useEffect 调 `leaveApi.types()`（enabled 且 weight 降序）注入 options（显示 name）
  - 字段 `unitFrom === 'leaveType'`：监听同表单 `leaveType` 值 → 从 types 列表找 unit → 渲染单位文案（天/小时/半天），未选时显示「天」
- `types/index.ts`：`FormField` 加 `optionsFrom?: string; unitFrom?: string`

- [ ] **步骤 4：验证**：后端全量绿（LeaveProcessTests 5 例）+ `npx tsc --noEmit` 零错误

- [ ] **步骤 5：Commit** `git commit -m "feat(holiday): 请假表单假期类型动态化与时长单位联动"`

### 任务 5：前端三页面 + 五 Tab 导航（HD-01/02/03/04 界面 + 5.4.1）

**文件：**
- 创建：`frontend/src/pages/LeaveManagement.tsx`、`LeaveBalanceDetail.tsx`、`LeaveTypes.tsx`、`MyLeave.tsx`、`AllAttendance.tsx`
- 创建：`frontend/src/components/AttendanceTabs.tsx`
- 修改：`frontend/src/pages/Leave.tsx`（内容移入 MyLeave.tsx，Leave.tsx 变权限切换包装）
- 修改：`frontend/src/components/AppShell.tsx`（两个导航项 → 一个「考勤&假期」`/attendance`）
- 修改：`frontend/src/App.tsx`（路由）
- 修改：`frontend/src/api/leave.ts`、`api/check.ts`、`types/index.ts`
- 后端小改：`CheckService` + `listAll(month, userId?)`（join 用户姓名，`CheckRecordRepository` 按 user+月份查）、`CheckController` + `GET /api/check/all?month=&userId=`（`month=YYYY-MM`）

**路由：** `/attendance`（我的考勤）· `/attendance/all` · `/attendance/settings`（占位 EmptyState）· `/leave`（权限切换：leave:manage → LeaveManagement，否则 MyLeave）· `/leave/balance/:userId` · `/leave/types`（`ProtectedRoute perm="leave:manage"`）

- [ ] **步骤 1：后端小改 + 测试**

`CheckService.listAll` 测试：`AttendanceTests` 加一例「month 过滤 + 返回含 userName」。RED → GREEN。

- [ ] **步骤 2：前端实现**（无前端测试框架，逐页面实现后统一 tsc）

- `AttendanceTabs`：五 Tab（NavLink，`end` 精确匹配 `/attendance`）；「假期类型」Tab 仅 `hasPerm('leave:manage')` 时显示；「全部考勤/考勤设置」所有角色可见（内容占位）
- `LeaveManagement`（HD-01）：
  - 数据：`GET /leave/admin/employees`（轮询不引入；进入页 + 筛选变化时拉取）
  - 左：部门树 = 从 rows 的 orgName 去重构建（含「全部」节点；节点显示人数 = 过滤后计数）；点击过滤
  - 右表：姓名/部门/操作（查看 → `/leave/balance/{id}`）；空态 EmptyState
  - 顶栏：假期类型下拉（leaveApi.types enabled）+ 姓名搜索 + `导出` 按钮（`downloadCsv`：列 = 姓名/部门/假期类型/假期时长/已用时长/剩余时长/时长单位；不限额行三时长列写「不限额」；文件名含日期）
- `LeaveBalanceDetail`（HD-02/03/07）：
  - 标题 `假期余额 - {realName} {orgName}`（从 employees 接口按 userId 取）
  - 表列：名称/假期时长/已用时长/剩余时长/时长单位/操作；unlimited 行三时长列渲染 `不限额`
  - 操作三按钮：`编辑剩余时长`（unlimited 行 disabled + title 提示）/`假期时长`/`日志`
  - 模态：剩余时长/假期时长 = 数字输入（后缀单位文案）+ 备注（可选）→ POST；成功后刷新
  - 日志模态：`GET .../transactions?type=` 表：类型/说明/备注/操作人/时间；`instanceId` 存在时说明列附「查看关联流程」链接 → `/tracking/{instanceId}`
- `LeaveTypes`（HD-04~06）：表 名称/限额(是-否)/时长单位/权重/操作(编辑/删除)；`+ 创建新类型` 红按钮 → 模态 4 必填字段（限额、单位用单选组）；删除二次确认，400 时 toast 后端文案
- `MyLeave`：原 Leave.tsx 内容（三账本卡片 + 流水 + 导出），types 接口现已含 unit —— 卡片单位文案随之修正
- `AllAttendance`：月份选择 + 人员下拉（userApi.list）+ 表（姓名/打卡时间/类型 in-out 文案/日期）+ 说明条「状态与 IP 统计将在考勤模块增强中提供」
- `Leave.tsx` 改造：`const canManage = hasPerm('leave:manage')` → 渲染 LeaveManagement 或 MyLeave

- [ ] **步骤 3：验证** `cd frontend && npx tsc --noEmit` 零错误 + 后端全量绿（CheckService 变更）

- [ ] **步骤 4：Commit** `git commit -m "feat(holiday): 假期管理/余额详情/假期类型三页面与考勤&假期五 Tab 导航"`

### 任务 6：部署 + E2E + README

**文件：** 无新增（`/tmp/oa-holiday-e2e.sh` 临时脚本 + `README.md`）

- [ ] **步骤 1：重建部署** `./build.sh all`，等 backend healthy

- [ ] **步骤 2：E2E**（脚本 `/tmp/oa-holiday-e2e.sh`，真实 MySQL，断言逐条打印）：
  1. admin 登录 → `GET /api/leave/types`：10 类、ANNUAL 居首（99）、含 unit/weight 键
  2. `GET /api/leave/admin/employees`：4 用户含 orgName；`?name=fin` 过滤 1 条
  3. employee 调 `POST /api/leave/admin/1/balances/ANNUAL/remaining` → **403**
  4. admin `setRemaining` employee ANNUAL 8 → 200，remaining=8；再 `GET admin/3/balances` 验证
  5. admin 对 SICK 调 `setRemaining` → **400**（不限额）
  6. admin `setQuota` employee ANNUAL 15 → 200；`GET .../transactions` 含 txnType=remaining/quota 各 1 条、operatorName=admin
  7. admin 创建类型「育儿假」hour 50 → 200 且 `GET types` 含之；再删除（无数据）→ 200
  8. admin 删除 ANNUAL → **400**（有流水）
  9. employee 发起请假：leave 定义 businessData `{"leaveType":"事假","days":"8"}` → 200；manager 审批通过 → employee `GET /api/leave/ledger?typeCode=PERSONAL` 含 txnType=consume、delta=-8、operatorName=manager；余额 used 仍 0（none 类型）
  10. `GET /api/check/all?month=`（当月）→ 200 数组
  11. 前端页面可达：`curl -s http://localhost/ | grep -q "考勤"`（SPA index 兜底即可）

- [ ] **步骤 3：README 假期章节更新**（三账本 → 十类型/单位/管理三页面/授予语义/权限码）

- [ ] **步骤 4：Commit** `git commit -m "docs(holiday): README 假期模块 + E2E 验证"` 并 push
