# 假期模块（HD-01~HD-08）实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 subagent-driven-development（推荐）或 executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 按 `docs/zhidieyun_oa_requirements.md` 第 5 节补齐假期模块 8 个功能点：假期管理（部门树+员工余额）、余额详情、余额日志、假期类型 CRUD、授予/调整余额、数据导出，并为全部余额体系引入**时长单位（天/小时/半天）**。

**架构：** 沿用现有「三账本」（leave_type 额度账 / leave_balance 余额账 / leave_transaction 发生账）+ 审批联动（freeze/consume/release）。本计划：① 数值列 INT→DECIMAL(10,2)、类型加 `unit` 字段，单位隔离不做跨单位换算（需求 5.5 规则 5）；② 流水扩展 operator_id/txn_type/remark 满足 HD-03 日志五要素；③ 新增 `/api/leave/admin/**` 管理端点（服务端校验 `leave:manage` **权限码**，复用 PermissionSeeder 已种权限，403 拒绝）；④ 前端「考勤/假期」两导航项合并为一个「考勤&假期」五 Tab 导航（我的考勤/全部考勤/考勤设置/假期管理/假期类型），「我的假期」单位感知化并按权限切换视图；⑤ 预置 9 类假期（需求 5.3.2 实测值）并让请假模板表单类型选项与类型表同源联动。

**技术栈：** Java 21 / Spring Boot 3.2.10 / Spring Data JPA / Flyway（common + h2 + mysql 三目录）/ React 18 + Vite + Tailwind / Flowable 7.0.1（审批联动回归）

**规格：** `docs/zhidieyun_oa_requirements.md` 第 5 节（5.1~5.6，HD-01~HD-08 与验收清单 10 条）

**范围裁决（Ruling，吸收了已归档 holiday 计划的 4 条更优决策）：**
1. **五 Tab 合并导航**（规格 5.6-⑤/4.3.0）：AppShell 的「考勤打卡」「我的假期」两导航项合并为一个「考勤&假期」（`/attendance`）；五 Tab = 我的考勤（现有 Attendance.tsx 不动）/ 全部考勤（**简化只读版**：月份+人员筛选，无状态/IP 列）/ 考勤设置（**占位页** EmptyState「规划中」）/ 假期管理（`/leave`，按 `leave:manage` 权限切换：有权限→LeaveManagement，无→MyLeave）/ 假期类型（`/leave/types`，仅 `leave:manage` 可见）。CK-02/CK-03 完整能力（状态分类/IP/设置持久化）属考勤模块后续计划。
2. **权限校验用 `leave:manage` 权限码**（而非 ADMIN 角色）：服务端取当前用户权限码（PermissionService 现成能力），前端 ProtectedRoute/导航/Tab 同款判定。
3. **流水直接存 `instance_id` 列**（审批联动时写入），日志链接直接用，免单号反查。
4. **不移动演示用户的部门归属**（4 人留在技术部）：V22 只播种 4 个新部门（0 人），部门树显示全部 5 部门及真实人数。
5. **流程条件面板复选组**（规格 3.4.5/5.5 规则②后半）属审批设置模块 AS-05，不在本计划；本计划仅覆盖表单「请假类型」下拉同源联动。

**注：** 竞争方案 `2026-10-05-holiday-module-gap-implementation.md` 已移入 `plans/archive/`（其 V22 与本计划 V22 版本冲突，不得执行）。

## 全局约束

- **双库 DDL 兼容**：H2 2.x 与 MySQL 8。`common/` 迁移不得含 ENGINE/CHARSET；H2 2.x **不支持 `MODIFY COLUMN`**（用 `ALTER COLUMN ... SET DATA TYPE`）；`TINYINT` 不写精度；`CREATE INDEX` 独立语句；`DATETIME(6)`。列类型变更（INT→DECIMAL）走 vendor 目录，新列/播种走 `common/`。
- **迁移版本**：`common/V22__leave_unit_and_log.sql`（新列+播种）+ `h2/V23__leave_decimal.sql` + `mysql/V23__leave_decimal.sql`（同版本号可并存于不同 vendor 目录，沿用 V5/V13 先例）。FlywayBootstrapTest 断言最新版本号 `"23"`。
- **单位隔离**：天/小时/半天按类型独立，**禁止跨单位换算**；余额数值一律 BigDecimal（DECIMAL(10,2)）。
- **测试 DB 共享**：所有 `@SpringBootTest` 共享同一 H2（`DB_CLOSE_DELAY=-1`）——新测试断言必须用 diff（前后对比）或 code 过滤，不得断言全局总量。
- **权限惯例**：本项目管理端点此前仅前端 `perm` 门控；本计划的 `/api/leave/admin/**` 全部**服务端**校验当前用户角色含 `ADMIN`（复用 `authService.getCurrentUser().getRoleCodes()`，403 拒绝）。
- **账实一致恒等式**：`剩余 = quota − used − frozen`；每次余额变动必须同事务写一条流水（含 operator）。
- **提交节奏**：每任务一个 commit；后端测试 `docker run ... maven:3.9-eclipse-temurin-21 mvn test`（从 `/tmp/oa-backend-test/backend`，rsync 排除 target/data.sql）；前端 `npx tsc --noEmit`。

## 审查重点（Review Focus）

| # | 输入/失败模式 | 期望行为 | 钉住的测试（加入任务） |
|---|---|---|---|
| 1 | 半天/小时类型申请 1.5、0.5 等非整数时长 | 余额精确扣减，无截断、无负数 | T1 `LeaveTests.decimal_units`、T5 `LeaveProcessTests.fractional_hour_leave` |
| 2 | 删除已有余额/流水数据的假期类型 | 400 拒绝并提示，数据不丢 | T2 `LeaveTypeCrudTests.delete_blocked_when_data_exists` |
| 3 | 对已禁用/已删除（软删）员工授予余额 | 400 拒绝 | T3 `LeaveAdminGrantTests.grant_to_inactive_user_rejected` |
| 4 | 同一请假实例状态反复流转（approve→reject→…） | 幂等：不重复扣/冲，账恒等式不破 | T5 `LeaveProcessTests`（保留现有 3 例回归） |
| 5 | 时长单位为「小时」的类型，表单提交 `days: 2` | 按该类型单位扣 2（小时），不做换算 | T5 `LeaveProcessTests.unit_follows_type` |

---

## 文件结构

**后端（`backend/src/main/java/com/oa/`）**
- 修改 `entity/LeaveType.java`：+ `unit`（String: `day`/`hour`/`half_day`）；派生 `limited()`（`quotaType != "none"`）
- 修改 `entity/LeaveBalance.java`：`quota/used/frozen` Integer→BigDecimal
- 修改 `entity/LeaveTransaction.java`：`delta`→BigDecimal；+ `operatorId`/`txnType`/`remark`
- 修改 `repository/LeaveTypeRepository.java`、`repository/LeaveBalanceRepository.java`、`repository/LeaveTransactionRepository.java`：decimal 兼容 + 新查询（按类型计数、日志分页、instanceNo 解析）
- 修改 `service/LeaveService.java`：全部运算 decimal 化；+ `setQuota`/`setRemaining`/`listBalances`/`log`/`resolveTypePublic` 等
- 修改 `controller/LeaveController.java`：+ admin 端点组 + ADMIN 校验
- 修改 `controller/UserController.java`：listUsers Map + `orgId`/`orgName`
- 修改 `config/LeaveBalanceSeeder.java`：9 类预置（需求属性）+ 部门/用户归属播种
- 修改 `config/ProcessTemplateSeeder.java`：leave 模板 formConfig 选项动态化
- 修改 `service/ProcessService.java`：`parseLeaveRequest` BigDecimal 化
- 创建 `dto/LeaveTypeRequest.java`、`dto/LeaveAdminGrantRequest.java`
- 修改 `resources/db/migration/common/V22__leave_unit_and_log.sql`（新）+ `h2/V23`、`mysql/V23`（新）

**前端（`frontend/src/`）**
- 修改 `api/leave.ts`：LeaveType +`unit/limited/weight`；新增 admin api 组
- 修改 `pages/Leave.tsx`：单位感知展示 + 流水类型/关联单链接
- 创建 `pages/LeaveManage.tsx`（HD-01/02/07/08）
- 创建 `pages/LeaveTypes.tsx`（HD-04~06）
- 修改 `pages/App.tsx`（路由）、`components/AppShell.tsx`（导航+面包屑）
- 修改 `types/index.ts`：LeaveType 相关类型（如需）

**测试（`backend/src/test/java/com/oa/`）**
- 修改 `LeaveTests.java`（decimal 兼容 + 9 类型断言）
- 修改 `LeaveProcessTests.java`（decimal 断言 + 新增 T5 两例）
- 创建 `LeaveTypeCrudTests.java`、`LeaveAdminGrantTests.java`、`LeaveManageTests.java`
- 修改 `FlywayBootstrapTest.java`（版本号 23）

---

### 任务 1：数据层——单位/小数/日志字段/9 类型播种

**文件：**
- 创建：`backend/src/main/resources/db/migration/common/V22__leave_unit_and_log.sql`
- 创建：`backend/src/main/resources/db/migration/h2/V23__leave_decimal.sql`、`mysql/V23__leave_decimal.sql`
- 修改：`entity/LeaveType.java`、`entity/LeaveBalance.java`、`entity/LeaveTransaction.java`、三个 repository、`service/LeaveService.java`（签名 decimal 化）
- 修改：`config/LeaveBalanceSeeder.java`、`config/ProcessTemplateSeeder.java`（leave 模板选项动态化——为 T5 铺路）
- 修改：`controller/UserController.java`（listUsers + orgId/orgName）
- 修改：`test/.../LeaveTests.java`、`FlywayBootstrapTest.java`
- 创建：`repository/OrganizationRepository.java`（若不存在）

**Interfaces（后续任务依赖）：**
```java
// LeaveType 新增
String unit;                       // "day" | "hour" | "half_day"，DB 默认 'day'
public boolean limited()           // return !"none".equals(quotaType);

// LeaveBalance / LeaveTransaction
BigDecimal quota;  BigDecimal used;  BigDecimal frozen;  // Integer → BigDecimal
BigDecimal delta;  Long operatorId;  String txnType;  String remark;  Long instanceId;  // txnType 枚举值：
// "GRANT"(授予) "ADJUST"(调整) "QUOTA"(额度设定) "CONSUME"(请假扣减) "REVERSE"(冲销) "FREEZE"(冻结) "RELEASE"(释放)

// LeaveService 关键签名（T2/T3/T5 使用）
List<LeaveType> listTypes();                     // 替代 List<Map>，enabled 排序 weight desc, id asc
LeaveType requireType(String codeOrLabel);       // 提升为 public（T5 用），不存在抛 400
Map<String,Object> toBalanceMap(LeaveBalance, LeaveType);  // 加 unit/limited/weight 字段，数值 BigDecimal
void grant(Long userId, String typeCode, BigDecimal amount, String reason, String ref, Long operatorId);
// 上列 3 个方法的 int/旧参版本全部删除，调用方同步改

// toTypeMap 改 toType：返回实体列表；DTO 序列化由 Controller 用 Map 组装
//   {id, code, name, category, quotaType, limited, annualQuota, unit, weight, enabled}
```

- [ ] **步骤 1：V22 common 迁移**

```sql
-- 单位与日志字段（H2/MySQL 通用语法）
ALTER TABLE leave_type ADD COLUMN unit VARCHAR(8) NOT NULL DEFAULT 'day';
ALTER TABLE leave_transaction ADD COLUMN operator_id BIGINT;
ALTER TABLE leave_transaction ADD COLUMN txn_type VARCHAR(16) NOT NULL DEFAULT 'GRANT';
ALTER TABLE leave_transaction ADD COLUMN remark VARCHAR(255);
ALTER TABLE leave_transaction ADD COLUMN instance_id BIGINT;   -- 审批实例直存（日志关联链接）

-- 预置 9 类（需求 5.3.2 实测值；limited 映射：是→fixed，否→none；幂等）
INSERT INTO leave_type (code, name, category, quota_type, annual_quota, weight, enabled, unit, created_at, updated_at)
SELECT 'ANNUAL','年假','法定','fixed',5,99,1,'day',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM leave_type WHERE code='ANNUAL');
-- 其余 8 类（quota_type 一律 'none'，annual_quota 0）：
-- COMPENSATORY 调休假 96 hour / MARRIAGE 婚假 95 day / MATERNITY 产假 94 day /
-- PATERNITY 陪产假 93 day / BEREAVEMENT 丧假 92 day / MENSTRUUAL… 注意 code 用 MENSTRUAL 例假 0 day
-- 对既有 6 类做属性修正（幂等 UPDATE）：
UPDATE leave_type SET quota_type='fixed', weight=99, unit='day' WHERE code='ANNUAL';
UPDATE leave_type SET quota_type='none',  weight=98, unit='hour'    WHERE code='PERSONAL';
UPDATE leave_type SET quota_type='none',  weight=97, unit='half_day' WHERE code='SICK';
UPDATE leave_type SET quota_type='none',  weight=95, unit='day'     WHERE code='MARRIAGE';
UPDATE leave_type SET quota_type='none',  weight=93, unit='day'     WHERE code='PATERNITY';
UPDATE leave_type SET weight=90, unit='day' WHERE code='SPECIAL';   -- 保留既有「特殊假」

-- 部门播种（需求 5.3.1 截图 5 部门；幂等，不移动用户归属——裁决 4）
-- MARKET 市场部 / OPERATIONS 运营部 / ADMIN_DEPT 行政部 / HR 人事部（org_type='DEPT'，parent_id 空，path '/'）
-- 4 个演示用户全部留在既有 TECH（部门树预期：TECH(4)、其余 4 部门(0)）

-- 流水索引（按员工+时间查日志）已存在 idx_leave_txn_user_time，无需新增
```

注意：`INSERT...WHERE NOT EXISTS` 沿用 V3 幂等写法；`FROM DUAL` 双库可写。新部门 path 用 `'/' || id || '/'` 子查询或固定值（无父级时固定 `/`）。

- [ ] **步骤 2：V23 vendor 迁移（列类型变更）**

`h2/V23__leave_decimal.sql`：
```sql
ALTER TABLE leave_balance ALTER COLUMN quota SET DATA TYPE NUMERIC(10,2);
ALTER TABLE leave_balance ALTER COLUMN used  SET DATA TYPE NUMERIC(10,2);
ALTER TABLE leave_balance ALTER COLUMN frozen SET DATA TYPE NUMERIC(10,2);
ALTER TABLE leave_transaction ALTER COLUMN delta SET DATA TYPE NUMERIC(10,2);
```
`mysql/V23__leave_decimal.sql`：同 4 列用 `MODIFY COLUMN ... DECIMAL(10,2) NOT NULL DEFAULT 0`。

- [ ] **步骤 3：实体 + repository 适配**

LeaveType +`unit`（`@Column(nullable=false, length=8)`，默认 `"day"`）+ `limited()`；LeaveBalance 三列改 `BigDecimal`（`available()` 改 BigDecimal 运算，null→ZERO）；LeaveTransaction +`operatorId`(Long, name="operator_id")、`txnType`(String, length=16, 默认 "GRANT")、`remark`(String, length=255)。LeaveTypeRepository 排序改 `findByEnabledTrueOrderByWeightDescIdAsc`（需求：权重 99→0 递减排序）。LeaveBalanceRepository 加 `findByUserIdAndLeaveTypeIdIn(Long, List<Long>)`；LeaveTransactionRepository 加：
```java
long countByLeaveTypeId(Long leaveTypeId);
long countByLeaveTypeIdAndOperatorId...（T2 删除校验用 countByLeaveTypeId 即可）
List<LeaveTransaction> findTop100ByUserIdAndLeaveTypeIdOrderByCreatedAtDescIdDesc(...);
Optional<LeaveTransaction> findFirstByUserIdAndLeaveTypeIdAndRefInstanceNoOrderByCreatedAtDesc(...);
@Query("select t from LeaveTransaction t where (t.userId = :userId or :userId is null) " +
       "and (t.leaveTypeId = :typeId or :typeId is null) order by t.createdAt desc, t.id desc")
List<LeaveTransaction> findLog(@Param("userId") Long userId, @Param("typeId") Long typeId);
```
（`or :x is null` 的 JPQL 参数写法在 H2/MySQL 均成立；若实现者遇绑定异常，改用两个查询分支。）

- [ ] **步骤 4：LeaveService decimal 化（行为不变回归）**

`grant/consume/reverse/freeze/release` 参数与内部运算全部 BigDecimal（`nvl(BigDecimal)`）；所有 `recordTransaction` 调用补 `operatorId`（来自调用方：审批联动传发起人 id，grant 传操作者 id）与 `txnType`（freeze→"FREEZE"，release→"RELEASE"，consume→"CONSUME"，reverse→"REVERSE"，grant→"GRANT"）。`toBalanceMap` 输出加 `unit/limited/weight`。新增私有 `toTxnMap(LeaveTransaction)`（**T4 的 adminLogs 复用**）输出键：`id/typeCode/typeName/unit/delta/txnType/typeLabel/operatorId/operatorName/acceptName/reason/remark/refInstanceNo/instanceId/createdAt`——`typeLabel` 映射表：`{GRANT:授予, ADJUST:调整, QUOTA:额度设定, CONSUME:请假扣减, REVERSE:冲销, FREEZE:冻结, RELEASE:释放}`；`acceptName`=流水所属员工 realName；`instanceId` 直存（审批联动流水写入实例 id，其余 null）；operatorName 按 operatorId 查 user（null 时回退 acceptName）。`ledger()` 与 `ledger(type)` 改走 `toTxnMap`。`recordTransaction` 新增 `instanceId` 参数（审批联动传实例 id，管理操作传 null）。`ProcessService.parseLeaveRequest` 的 `days` 改保留原始文本（`String.valueOf`），调用方 `new BigDecimal`（去掉 intValue 截断）。`ProcessTemplateSeeder.seedLeaveTemplate`：formConfig 中请假类型 `options` 改为从 `leaveTypeRepository.findByEnabledTrueOrderByWeightDescIdAsc()` 动态映射显示名，`days` 字段 label 改「请假时长」。LeaveBalanceSeeder：TYPES 常量每项加第 6 位 unit（`{code,name,quotaType,annualQuota,weight,unit}`），9 类 + SPECIAL 与 V22 一致；**演示授额改为仅 ANNUAL**（manager 5 / employee 10；PERSONAL 已 none 化，取消 SEED-PERSONAL 授予——裁决：不限额类型不做演示授额）。UserController.listUsers Map + `orgId`/`orgName`（`u.getOrg()` 判空）。

- [ ] **步骤 5：写失败测试（decimal 与 9 类型）**

`LeaveTests` 更新 + 新增：
```java
@Test void seeded_nine_types_with_units() {
    var types = leaveService.listTypes();
    assertThat(types).extracting(LeaveType::getCode)
        .contains("ANNUAL","PERSONAL","SICK","COMPENSATORY","MARRIAGE","MATERNITY","PATERNITY","BEREAVEMENT","MENSTRUAL");
    assertThat(leaveService.requireType("年假")).satisfies(t -> {
        assertThat(t.isLimited()).isTrue(); assertThat(t.getUnit()).isEqualTo("day"); assertThat(t.getWeight()).isEqualTo(99);
    });
    assertThat(leaveService.requireType("SICK").getUnit()).isEqualTo("half_day");
}

@Test void decimal_units_no_truncation() {
    // 对 hour 类（PERSONAL）授予 2.5，扣 1.5，余 1.0
    loginAs("employee");
    var after = leaveService.grant(employeeId(), "PERSONAL", new BigDecimal("2.5"), "演示", null, adminId());
    assertThat(after.get("available")).isEqualByComparingTo("2.5");
    leaveService.consume(employeeId(), "PERSONAL", new BigDecimal("1.5"), "T-DEC", "测试扣减");
    var b = leaveService.balance(employeeId(), "PERSONAL");
    assertThat((BigDecimal) b.get("available")).isEqualByComparingTo("1.0");
}
```
（若 PERSONAL 已被演示授额 5，测试用 diff 语义断言可用值增量，或先断言 quota 增量——实现者按 diff 写。）

`FlywayBootstrapTest`：断言版本 `"23"`。

- [ ] **步骤 6：运行验证**

运行：`mvn test -Dtest=LeaveTests,LeaveProcessTests,FlywayBootstrapTest`（先预期失败：列类型/新字段缺失）
实现后：`mvn test`（全量）预期：全绿（现有 75 例 + 新增）

- [ ] **步骤 7：Commit**

```bash
git add backend/
git commit -m "feat(leave): 时长单位(天/时/半天)+小数账本+日志字段+预置9类假期"
```

---

### 任务 2：假期类型 CRUD（HD-04~HD-06）

**文件：**
- 创建：`dto/LeaveTypeRequest.java`、`controller/LeaveTypeAdminController.java`（独立控制器，映射 `/api/leave/types` 写操作）
- 修改：`service/LeaveService.java`（+ `createType/updateType/deleteType`）
- 测试：创建 `LeaveTypeCrudTests.java`

**Interfaces：**
```java
// dto/LeaveTypeRequest.java —— 四字段全部必填（需求 5.3.5）
record LeaveTypeRequest(
    String code,                             // 可选，缺省=名称
    @NotBlank @Size(max=32) String name,
    @NotNull boolean limited,                 // 限额 是/否
    @NotBlank @Pattern(regexp="day|hour|half_day") String unit,
    @NotNull @Min(0) @Max(9999) Integer weight) {}

// LeaveService 新增（均 @Transactional，先 ADMIN 校验在 Controller）
Map<String,Object> createType(LeaveTypeRequest req, Long operatorId);   // code 规则：req.code 缺省时默认取名称（code 列 VARCHAR(32) 存中文无碍；balance 表按 id 引用，code 仅作展示/查找键）
Map<String,Object> updateType(Long id, LeaveTypeRequest req);
void deleteType(Long id);   // 有余额或流水 → 400 "该类型下存在余额/流水数据，禁止删除"

// Controller 端点（全部 @PreAuthorize 不可用——沿用手动校验）：
POST   /api/leave/types        body=LeaveTypeRequest
PUT    /api/leave/types/{id}   body=LeaveTypeRequest
DELETE /api/leave/types/{id}
// 读端点沿用任务 1 的 GET /api/leave/types
```
- `limited` → `quotaType` 映射：`limited ? "fixed" : "none"`（编辑时保留原 accrual 语义：仅当 limited 翻转时改写，否则不动 quotaType——避免误伤）。
- 名称唯一校验（重名 400）；`code` 缺省时自动取名称。
- **权限**：三个端点先 `requireLeaveManage()`（当前用户权限码不含 `leave:manage` → 403；admin 属 SYSTEM_ADMIN 组=全部权限 → 通过）。`requireLeaveManage()` 私有 helper 注入 `PermissionService`，复用其权限码集合方法。

- [ ] **步骤 1：写失败测试** `LeaveTypeCrudTests`（loginAs("admin")）：
```java
@Test void create_edit_and_list() {
    var created = leaveService.createType(new LeaveTypeRequest(null, "测试假", true, "hour", 50), adminId());
    assertThat((String) created.get("code")).isNotBlank();
    var updated = leaveService.updateType((Long) created.get("id"), new LeaveTypeRequest(null, "测试假改", false, "day", 60));
    assertThat((boolean) updated.get("limited")).isFalse();
    assertThat(leaveService.listTypes().stream().anyMatch(t -> t.getName().equals("测试假改"))).isTrue();
}
@Test void create_validates() {
    assertThatThrownBy(() -> leaveService.createType(new LeaveTypeRequest("x", true, "week", 0), adminId()))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("单位");   // 在 Controller 校验；service 侧断言单位白名单即可
    assertThatThrownBy(() -> leaveService.createType(new LeaveTypeRequest("年假", true, "day", 1), adminId()))
        .hasMessageContaining("名称已存在");
}
@Test void delete_blocked_when_data_exists() {   // 审查重点 #2
    leaveService.grant(employeeId(), "ANNUAL", BigDecimal.ONE, "演示", null, adminId());
    var annual = leaveService.requireType("ANNUAL");
    assertThatThrownBy(() -> leaveService.deleteType(annual.getId()))
        .hasMessageContaining("禁止删除");
    assertThat(leaveBalanceRepository.count()).isEqualTo(beforeCount);  // 数据未动（diff）
}
@Test void delete_allowed_when_empty() {
    var t = leaveService.createType(new LeaveTypeRequest(null, "临时假", false, "day", 1), adminId());
    leaveService.deleteType((Long) t.get("id"));
    assertThat(leaveTypeRepository.findByCode((String) t.get("code"))).isEmpty();
}
```

- [ ] **步骤 2：运行确认失败**（方法不存在）
- [ ] **步骤 3：实现** LeaveService 三方法 + Controller 三端点（ADMIN 校验：`getCurrentUser().getRoleCodes()` 不含 `ADMIN` → 403；`deleteType` 前 `countByLeaveTypeId(id) > 0 || leaveBalanceRepository.countByLeaveTypeId(id) > 0` → 400；LeaveBalanceRepository 补 `countByLeaveTypeId`）
- [ ] **步骤 4：运行 `mvn test -Dtest=LeaveTypeCrudTests` 通过**
- [ ] **步骤 5：Commit** `git commit -m "feat(leave): 假期类型 CRUD（四必填+单位白名单+有数据禁删）"`

---

### 任务 3：授予 / 调整余额（HD-07）

**文件：**
- 创建：`dto/LeaveAdminGrantRequest.java`
- 修改：`controller/LeaveController.java`（+ admin 授予端点）、`service/LeaveService.java`（+ `setQuota`/`setRemaining`）
- 修改：`repository/LeaveBalanceRepository.java`（`findByUserIdAndLeaveTypeId` 已有）
- 测试：创建 `LeaveAdminGrantTests.java`

**Interfaces：**
```java
record LeaveAdminGrantRequest(
    @NotNull Long userId,
    @NotBlank String typeCode,
    @NotBlank @Pattern(regexp="SET_QUOTA|SET_REMAINING") String action,
    @NotNull @DecimalMin("0") BigDecimal amount,
    String remark) {}

// LeaveService（均 ADMIN 调用，operatorId = 当前用户）
Map<String,Object> setQuota(Long userId, String typeCode, BigDecimal newQuota, String remark, Long operatorId);
Map<String,Object> setRemaining(Long userId, String typeCode, BigDecimal newRemaining, String remark, Long operatorId);
// 端点：POST /api/leave/admin/grant  body=LeaveAdminGrantRequest（按 action 分发）
```
**规则（需求 5.4.2 + 5.4.4）：**
- 目标用户必须 `status == ACTIVE`（否则 400）。
- `SET_QUOTA`：`quota = newQuota`；流水 `delta = newQuota − 旧quota`，`txnType="QUOTA"`，remark 必填时写入。
- `SET_REMAINING`：**不限额类型 → 400「不限额类型无剩余时长概念」**（分支 5a）；限额类型：`quota = used + frozen + newRemaining`（修正总额度使剩余达标）；流水 `delta = 新quota − 旧quota`，`txnType="ADJUST"`。
- 两者恒保证 `quota − used − frozen >= 0`；流水 `operatorId=操作者`，`reason` = `SET_QUOTA ? "额度设定" : "剩余时长调整"`。
- 幂等：管理操作不做 ref 幂等（每次都是显式操作，全部留痕）。

- [ ] **步骤 1：写失败测试** `LeaveAdminGrantTests`（employee 有年假 10）：
```java
@Test void set_quota_writes_delta_and_quota() {
    var b = leaveService.setQuota(employeeId(), "ANNUAL", new BigDecimal("15"), "年中追加", adminId());
    assertThat((BigDecimal) b.get("quota")).isEqualByComparingTo("15");
    var logs = leaveTransactionRepository.findLog(employeeId(), annualId());
    assertThat(logs.get(0).getTxnType()).isEqualTo("QUOTA");
    assertThat(logs.get(0).getOperatorId()).isEqualTo(adminId());
    assertThat(logs.get(0).getDelta()).isEqualByComparingTo("5");
}
@Test void set_remaining_backs_out_quota() {
    // 年假 quota 10, used 0：设剩余 3 → quota 变 3，used 不动
    var b = leaveService.setRemaining(employeeId(), "ANNUAL", BigDecimal.valueOf(3), "修正", adminId());
    assertThat((BigDecimal) b.get("available")).isEqualByComparingTo("3");
    assertThat((BigDecimal) b.get("used")).isEqualByComparingTo("0");
}
@Test void set_remaining_rejected_for_unlimited() {   // 分支 5a
    assertThatThrownBy(() -> leaveService.setRemaining(employeeId(), "SICK", BigDecimal.ONE, "x", adminId()))
        .hasMessageContaining("不限额");
}
@Test void grant_to_inactive_user_rejected() {        // 审查重点 #3
    // 建一个 INACTIVE 用户（直接 repository.save）
    assertThatThrownBy(() -> leaveService.setQuota(inactiveId(), "ANNUAL", BigDecimal.ONE, "x", adminId()))
        .hasMessageContaining("非在职");
}
@Test void admin_role_required_at_controller() {
    // employee（无 leave:manage 权限码）调 controller 端点 → 403（getCurrentUser 走 SecurityContext，loginAs 已设）
}
@Test void admin_passes_permission_check() {   // admin 属 SYSTEM_ADMIN 组=全部权限
    loginAs("admin");
    leaveController.grant(...);   // 不抛 403
}
```
- [ ] **步骤 2：运行确认失败**
- [ ] **步骤 3：实现**（LeaveService 两方法 + 端点 + 复用 T2 的 `requireLeaveManage()` 权限码校验）
- [ ] **步骤 4：`mvn test -Dtest=LeaveAdminGrantTests` 通过**
- [ ] **步骤 5：Commit** `git commit -m "feat(leave): 管理员授予/调整余额（额度设定/剩余修正+日志留痕+ADMIN）"`

---

### 任务 4：假期管理查询 + 日志 + 导出（HD-01/02/03/08）

**文件：**
- 修改：`controller/LeaveController.java`（admin 查询端点组）、`service/LeaveService.java`（查询方法）、`repository/OrganizationRepository.java`（确认存在；补 `findByStatusOrderBySortOrderAsc`）
- 测试：创建 `LeaveManageTests.java`

**Interfaces（端点全部 ADMIN）：**
```java
GET /api/leave/admin/departments
  → [{id, orgCode, orgName, memberCount}]   // 全部 DEPT 类型 + ACTIVE 用户计数

GET /api/leave/admin/employees?orgId=&typeCode=&keyword=
  → [{id, username, realName, orgId, orgName, position,
      balances:[{code,name,unit,limited,quota,used,frozen,available}]}]
  // keyword 匹配 realName/username；orgId 缺省=全部；balances 按类型 weight 降序，
  // 不限额类型 quota/used/frozen 返回 null（前端显示"不限额"）

GET /api/leave/admin/balances/{userId}
  → 同 employees 的 balances 结构（HD-02 余额页数据源）

GET /api/leave/admin/logs?userId=&typeCode=&page=0&size=50
  → {items:[{id, typeCode, typeName, unit, delta, txnType, typeLabel,   // typeLabel 中文：授予/调整/额度设定/请假扣减/冲销/冻结/释放
             operatorId, operatorName, acceptName, reason, remark,
             refInstanceNo, instanceId, createdAt}], total}
  // instanceId 直存（裁决 3；审批联动流水写入实例 id，管理操作 null）

GET /api/leave/admin/export?orgId=&typeCode=&keyword=
  → CSV 文本（复用 csv 组装：员工/部门/假期类型/额度/已用/冻结/剩余/单位），Content-Disposition 下载
```
- [ ] **步骤 1：写失败测试** `LeaveManageTests`（部门人数按裁决 4：4 演示用户全在 TECH，新 4 部门 0 人）：
```java
@Test void departments_with_member_counts() {
    var depts = leaveService.adminDepartments();
    assertThat(depts).extracting("orgCode").contains("TECH","MARKET","OPERATIONS","ADMIN_DEPT","HR");
    var tech = depts.stream().filter(d -> "TECH".equals(d.get("orgCode"))).findFirst().orElseThrow();
    assertThat((Number) tech.get("memberCount")).isEqualTo(4);      // 4 演示用户未移动
    var hr = depts.stream().filter(d -> "HR".equals(d.get("orgCode"))).findFirst().orElseThrow();
    assertThat((Number) hr.get("memberCount")).isEqualTo(0);        // 新部门空
}
@Test void employees_filter_by_org_and_type() {
    var techId = leaveService.adminDepartments().stream().filter(d -> "TECH".equals(d.get("orgCode"))).findFirst().orElseThrow().get("id");
    var list = leaveService.adminEmployees(techId, "ANNUAL", null);
    assertThat(list).hasSize(4).allMatch(e -> techId.equals(e.get("orgId")));
    assertThat(list.get(0).get("balances")).hasSize(1);             // 只含 ANNUAL
    var all = leaveService.adminEmployees(null, null, "张");        // 命中某个种子 realName
    assertThat(all).isNotEmpty();
}
@Test void balances_row_shape_and_unlimited_null() {
    var rows = leaveService.adminBalances(employeeId());
    var sick = rows.stream().filter(r -> "SICK".equals(r.get("code"))).findFirst().orElseThrow();
    assertThat(sick.get("quota")).isNull();                          // 不限额 → null
    assertThat((String) sick.get("unit")).isEqualTo("half_day");
}
@Test void logs_include_operator_and_ref_resolution() {
    // employee 发起请假→经理通过（复用 LeaveProcessTests 的实例创建 helper 思路）
    var logs = leaveService.adminLogs(employeeId(), "ANNUAL", 0, 50);
    assertThat(logs.items()).isNotEmpty();
    assertThat(logs.items().get(0)).satisfies(it -> {
        assertThat(it.get("typeLabel")).isIn("请假扣减","冻结","释放","冲销","授予","调整","额度设定");
        assertThat(it.get("operatorName")).isNotBlank();
        assertThat(it.get("instanceId")).isNotNull();
    });
}
```
- [ ] **步骤 2：运行确认失败**
- [ ] **步骤 3：实现**（LeaveService：`adminDepartments/adminEmployees/adminBalances/adminLogs/adminExport`；`instanceId` 直取流水列，`typeLabel`/`operatorName` 组装复用 T1 的 `toTxnMap`）
- [ ] **步骤 4：`mvn test -Dtest=LeaveManageTests` 通过；全量 `mvn test` 全绿**
- [ ] **步骤 5：Commit** `git commit -m "feat(leave): 假期管理查询/余额日志/CSV导出（部门树+筛选+ADMIN）"`

---

### 任务 5：审批联动小数化 + 流水类型 + 模板选项动态化

**文件：**
- 修改：`service/ProcessService.java`（`applyLeaveLedger`/`parseLeaveRequest` BigDecimal；ledger 流水 txnType 由 T1 覆盖此处调用）
- 修改：`config/ProcessTemplateSeeder.java`（leave 模板 formConfig 动态选项 + days label）
- 测试：修改 `LeaveProcessTests.java`

**Interfaces：**
```java
// parseLeaveRequest 返回 String[]{code, daysText}，daysText 保留原始小数文本
// applyLeaveLedger 内 BigDecimal days = new BigDecimal(leave[1])
```
- [ ] **步骤 1：新增/更新测试**（LeaveProcessTests 现有 3 例断言改 BigDecimal；新增两例——审查重点 #1/#5）：
```java
@Test void unit_follows_type() {     // 事假 unit=hour，提交 days=2 → 扣 2（小时），无换算
    // employee 发起 leave 实例 businessData={"leaveType":"事假","days":"2"} → 经理通过
    // 断言 SICK? 不，事假=PERSONAL：balance(PERSONAL).available 减少 2，流水 delta=-2 且 unit=hour
}
@Test void fractional_hour_leave() { // days="1.5" → 扣 1.5，余额精确
}
@Test void half_day_leave_consumes_halfday() { // 病假 half_day，days="2" → 扣 2 个半天
}
```
- [ ] **步骤 2：运行确认失败**（当前 intValue 截断/无 unit 字段）
- [ ] **步骤 3：实现**（decimal 化；确认 T1 已把 recordTransaction 的 txnType 接好；模板 options 动态化：`leaveTypeRepository.findByEnabledTrueOrderByWeightDescIdAsc().map(LeaveType::getName).toList()` 注入 formConfig JSON——seeder 已有 objectMapper）
- [ ] **步骤 4：`mvn test -Dtest=LeaveProcessTests,LeaveTests,LeaveManageTests` 通过**
- [ ] **步骤 5：Commit** `git commit -m "feat(leave): 审批扣减小数化+流水类型标注+请假表单类型选项同源联动"`

---

### 任务 6：五 Tab 导航框架 + 我的假期（MyLeave）单位感知 + 全部考勤

**文件：**
- 创建：`pages/MyLeave.tsx`（现 Leave.tsx 内容搬入 + 单位感知改造）
- 创建：`pages/AllAttendance.tsx`（简化只读版）、`components/AttendanceTabs.tsx`（五 Tab 栏，NavLink）
- 重写：`pages/Leave.tsx`（权限切换包装；**T6 阶段先恒渲染 MyLeave**，T7 加 canManage 分支）
- 修改：`pages/Attendance.tsx`（顶部包 AttendanceTabs）、`components/AppShell.tsx`（导航两项 → 一个「考勤&假期」`/attendance` + 面包屑）、`pages/App.tsx`（路由 ×4）
- 修改：`api/leave.ts`（LeaveType +`unit/limited/weight`；LeaveTransaction +`txnType/typeLabel/operatorName/acceptName/unit/instanceId`；LeaveBalance +`unit/limited`）、`api/check.ts`（+`listAll`）、`types/index.ts`
- 后端小改：`CheckService.listAll(month, userId?)`（join 用户姓名，按 user+月份查）+ `CheckController` `GET /api/check/all?month=&userId=`；测试 `AttendanceTests` +1 例（month 过滤 + 含 userName，RED→GREEN）

**路由与 Tab 结构（裁决 1）：**
- `/attendance` → 我的考勤（Attendance 内容不变，加 Tab 栏；NavLink `end` 精确匹配）
- `/attendance/all` → 全部考勤：月份选择 + 人员下拉（userApi.list）+ 表（姓名/打卡时间/类型 in-out 文案/日期）+ 说明条「状态与 IP 统计将在考勤模块增强中提供」
- `/attendance/settings` → 占位 EmptyState「考勤设置规划中」
- `/leave` → `canManage = hasPerm('leave:manage')` → LeaveManagement（T7）/ MyLeave；Tab「假期管理」恒显示
- `/leave/types` → LeaveTypes（T8）；Tab「假期类型」仅 canManage；「全部考勤/考勤设置」所有登录用户可见

**MyLeave 单位感知：** `UNIT_LABEL = { day:'天', hour:'小时', half_day:'半天' }`；卡片可用值带单位；进度条 used/quota（unlimited=0）；流水行 typeLabel（delta 符号着色）+ 操作人 + 单位，`instanceId` 非 null → 链接 `/tracking/{instanceId}`；导出 CSV 列 = 假期类型/单位/变动/类型/操作人/原因/备注/关联单号/时间；unlimited 展示保留。

- [ ] **步骤 1：后端 `CheckService.listAll` RED→GREEN**（AttendanceTests 先加失败例再实现）
- [ ] **步骤 2：前端 api 类型 + 五 Tab 框架 + 三考勤路由**
- [ ] **步骤 3：MyLeave 单位感知 + AllAttendance + 占位页 + Leave.tsx 包装（恒 MyLeave）**
- [ ] **步骤 4：`npx tsc --noEmit` 通过 + 后端 full green**
- [ ] **步骤 5：Commit** `git commit -m "feat(attendance-leave): 考勤&假期五Tab导航框架+我的假期单位感知+全部考勤简化版"`

---

### 任务 7：前端「假期管理」+「余额详情」页（HD-01/02/07/08）

**文件：**
- 创建：`pages/LeaveManagement.tsx`（HD-01）、`pages/LeaveBalanceDetail.tsx`（HD-02/03/07，路由 `/leave/balance/:userId`，`ProtectedRoute perm="leave:manage"`）
- 修改：`pages/Leave.tsx`（canManage 分支渲染 LeaveManagement）、`api/leave.ts`（admin 组：`departments/employees/balances/transactions/grant/export`，对齐 T4）、`components/AppShell.tsx`（面包屑）

**LeaveManagement：** 筛选区（类型下拉+姓名搜索+`导出`→CSV，不限额行写「不限额」，文件名含日期）；左部门树（`adminDepartments()`，`orgName(memberCount)`，顶部「全部」，点击过滤）；右员工表（姓名/部门/职位+余额列）行尾 `查看` → `navigate('/leave/balance/'+id)`。

**LeaveBalanceDetail：** 标题 `假期余额 - {realName} {orgName}`；表列 名称/假期时长/已用/剩余/时长单位/操作（unlimited 行三列「不限额」）；三按钮 `编辑剩余时长`（blue，unlimited 禁用+title）/`假期时长`（red）→ 弹窗数值（step：day/half_day=1，hour=0.5）+备注 → `adminGrant`；`日志`（red）→ 弹窗表（名称/操作人/接受人/类型/说明/备注），`instanceId` 非 null 附「查看关联流程」→ `/tracking/{instanceId}`。

- [ ] **步骤 1：`api/leave.ts` 补 admin 组**
- [ ] **步骤 2：实现两页面 + Leave.tsx 权限分支 + 面包屑**
- [ ] **步骤 3：`npx tsc --noEmit` 通过**
- [ ] **步骤 4：Commit** `git commit -m "feat(leave-ui): 假期管理页+余额详情页（三按钮/日志/导出/权限切换）"`

---

### 任务 8：前端「假期类型」页（HD-04~06）

**文件：**
- 创建：`pages/LeaveTypes.tsx`
- 修改：`pages/App.tsx`（路由 `/leave/types`，`ProtectedRoute perm="leave:manage"`）、`components/AppShell.tsx`（面包屑）、`api/leave.ts`（`createType/updateType/deleteType`）

**页面（需求 5.3.4/5.3.5）：** 右上 `+ 创建新类型`（red）；表格列 = 名称/限额(是|否)/时长单位/权重/操作(编辑/删除)；弹窗表单四必填：名称、限额(单选 否/是)、时长单位(单选 天/小时/半天)、权重(数字默认 0)；删除：有数据时后端 400 → toast 展示后端 message。

- [ ] **步骤 1：`api/leave.ts` 补类型 CRUD 调用**
- [ ] **步骤 2：实现 `LeaveTypes.tsx` + 路由**
- [ ] **步骤 3：`npx tsc --noEmit` 通过**
- [ ] **步骤 4：Commit** `git commit -m "feat(leave-ui): 假期类型管理页（CRUD+四必填+单位/权重）"`

---

### 任务 9：全量回归 + 部署 + E2E + 文档

- [ ] **步骤 1：后端全量** `rsync ... /tmp/oa-backend-test/backend/ && mvn test` 预期全绿（记录总数）
- [ ] **步骤 2：前端** `npx tsc --noEmit` 干净
- [ ] **步骤 3：部署** `./build.sh all`（Synology 目录禁 `up --build`）
- [ ] **步骤 4：E2E 脚本**（`/tmp/oa-leave-e2e.sh`，admin/manager 登录，断言）：
  1. `GET /api/leave/types` → 9+1 类，单位/权重/限额与需求表一致（年假 是/99/天，事假 否/98/小时，病假 否/97/半天…）
  2. `GET /api/leave/admin/departments` → 5 部门：TECH(4)，MARKET/OPERATIONS/ADMIN_DEPT/HR 各 0（演示用户未移动）
  3. 员工余额：`GET /api/leave/admin/balances/{employeeId}` → 年假有数值、病假显示 null（不限额）
  4. 授予：`POST /api/leave/admin/grant`（employee ANNUAL SET_QUOTA 20）→ 余额更新 + 日志 1 条 typeLabel=额度设定
  5. 审批联动：employee 发起 leave（`{"leaveType":"年假","days":"1.5"}`）→ manager 通过 → ANNUAL 余额 −1.5，日志含 instanceId
  6. 无权限：employee（无 `leave:manage`）调 `POST /api/leave/admin/grant` → 403
  7. 导出：`GET /api/leave/admin/export` → CSV 含 BOM 与表头
  8. 类型 CRUD：创建「测试假」→ 编辑 → 删除（空数据成功；对年假删除 → 400）
- [ ] **步骤 5：README** 假期模块章节（导航/权限/三账本/单位语义/9 类型/授予操作）
- [ ] **步骤 6：Commit + push** `git commit -m "chore(leave): 假期模块 E2E 验证 + README"` → `git push origin develop`

---

## 执行顺序与依赖

T1（数据层，其余全依赖）→ T2 ∥ T3（同改 LeaveController；T3 依赖 T1 grant 签名 + T2 的 requireLeaveManage）→ T4（依赖 T1/T3 日志字段）→ T5（依赖 T1）→ T6（导航框架，独立）→ T7（依赖 T6 的 Leave.tsx 包装 + T4 响应）→ T8（依赖 T6 的 Tab）→ T9。

**预计测试增量**：约 +15 例（LeaveTests +2、Crud +5、Grant +6、Manage +4、Process +3、Attendance +1，按实际）；全量 75 → 约 90。
