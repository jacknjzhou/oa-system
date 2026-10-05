# 人员-部门关联管理 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 subagent-driven-development（推荐）或 executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 补齐规格 §6.3.0/6.3.1/6.3.2/6.4 的「人员与部门关联关系和管理功能」——部门 CRUD（带人数树）+ 员工创建/调岗/性别/职称关联 + 系统模块六 Tab 导航 + 跨模块联动验收。

**架构：** 部门 CRUD 新建 `OrganizationService`/`OrganizationController`（树形结构用 `organization.parent_id`，人数实时派生自 `sys_user.status=ACTIVE`，不做冗余计数）；员工侧扩展 `UserCreateRequest` + `UserUpdateRequest`（orgId 调岗/性别/职称），`/users` 增加 status/orgId/keyword 筛选（默认仍只返回 ACTIVE，人员选择器语义不变）；前端系统模块合并为 `/system/*` 六 Tab 栏（复用考勤&假期五 Tab 模式），部门页 = 左树 + 右员工表。

**技术栈：** Spring Boot 3.2 + Spring Data JPA + Flyway（common/V24）+ React 18/TypeScript/Vite/Tailwind

**规格：** `docs/zhidieyun_oa_requirements.md` §6.3.0（六 Tab）、§6.3.1（员工管理）、§6.3.2（部门管理）、§6.3.3/6.3.4（职级/职称）、§6.4（R1-R7、6.4.3 联动、6.4.4 维护流程、6.4.5 验收要点）。§5 假期部分已由 2026-10-05-leave-module 计划实现，不在本计划范围。

## 全局约束

- 迁移文件放 `backend/src/main/resources/db/migration/common/`，必须 H2 2.x + MySQL 8 双兼容：无 ENGINE/CHARSET 表选项、`TINYINT` 不带精度、独立 `CREATE INDEX`、`DATETIME(6)`。
- 新列全部 **nullable**，不触碰存量 4 个演示用户的数据；Hibernate `ddl-auto: update`（H2 profile）会自动补列，MySQL 依赖 V24。
- 权限码新增值**只追加**：`hr:dept`（人事组），进 `PermissionSeeder.PERMS`；仅 SYSTEM_ADMIN 组获得，NORMAL 组基础 13 项不含它（R7）。权限校验写在 **Controller 层**（`requireDeptManage()`，缺权限 403）。
- Controller 直接调用测试约定：只读端点 `@Transactional(readOnly=true)`，写端点 `@Transactional`（解 OIV）；测试用 `loginAs(username)` 设 SecurityContext；业务错误 = `ResponseStatusException(HttpStatus.BAD_REQUEST, 中文消息)`，断言用 `ex.getStatusCode()`（Spring 6.1 无 getStatus）。
- **共享 H2 库纪律**（`DB_CLOSE_DELAY=-1`，所有 @SpringBootTest 共用）：临时用户必须 `org=技术部`（TECH）；断言用 diff 或按 code/username 过滤，不依赖全局计数；`@AfterEach` 清 SecurityContext；改动的演示账号状态必须还原。
- 前端 `apiClient` 响应拦截已解包 `{code,message,data}` → 调用方直接得 `data`；前端验收门 = `npx tsc --noEmit` 干净。
- 部署 = 仓库根目录 **无参 `./build.sh`**（`up` 子命令不重建镜像！）；E2E 走 `http://localhost:8080/api`，登录 `admin/admin123` 等。
- 审批「主管」节点（R5）已由 `AssignSupervisorDelegate` 读 `user.supervisorId` 实现，本计划只保证"主管仅可选在职员工"（创建/编辑校验 + 选择器过滤），不改 delegate。
- 偏离说明：① 6.3.3/6.3.4 截图中的"描述"列在 `job_level`/`job_title` 表无对应列，本计划不新增 description 列（YAGNI），字典页展示 名称/代码/状态；② 6.3.1 的「选择员工状态 ▾」下拉与四子 Tab 功能重叠，只保留子 Tab（YAGNI）；③ 6.4.3 第 1/2 行（流程设置发起人部门多选、审批人部门选择）依赖流程设计规范扩展，属审批设置模块另立计划，**不在本计划范围**；本计划只保证「主管」链路（R5，已有 delegate）与选择器/详情页联动。
- 员工永不硬删（回收站模式，规则 14）：审批历史/假期日志的姓名解析（按 user id join）在禁用/软删后仍有效，"历史记录保留"（6.4.4）由软删天然保证，不需要额外快照列。

## 审查重点（Review Focus）

1. **重名用户名创建** → 400「用户名已存在」且无任何部分写入（事务内 username 唯一）→ T3 测试 `create_dup_username_400`
2. **删除有子部门/在职员工的部门** → 400 明确消息（"请先删除子部门" / "仍有 N 名在职员工"），历史数据不受影响 → T2 测试 `delete_protection`
3. **部门树环**（父指向自己或自己的后代）→ 400「部门树存在环」→ T2 测试 `update_cycle_rejected`
4. **主管选已禁用/已删除/不存在员工** → 400「主管必须为在职员工」；创建/编辑弹窗主管下拉只列 ACTIVE → T3 测试 `create_invalid_supervisor_400`
5. **禁用/删除员工** 不进部门人数、不进任何人员选择器，但审批历史/假期日志保留（6.4.5-7）→ T2 `member_count_excludes_disabled` + T3 `list_status_filter`
6. **非法字典值不可录入**（职称/职级/部门 id 不存在）→ 400（6.4.5-6）→ T3 `create_invalid_dict_400`
7. **调岗后人数实时变化**（6.4.5-2）：创建/调岗/禁用后 `GET /organizations/tree` 的两个部门 memberCount 随之增减 → T3 `transfer_updates_tree_count`

---

### 任务 T1：数据层（V24 迁移 + 实体字段 + 仓库查询）

**文件：**
- 修改：`backend/src/main/resources/db/migration/common/V24__user_org_fields.sql`（创建）
- 修改：`backend/src/main/java/com/oa/entity/User.java`
- 修改：`backend/src/main/java/com/oa/repository/OrganizationRepository.java`
- 修改：`backend/src/main/java/com/oa/repository/UserRepository.java`
- 修改：`backend/src/test/java/com/oa/FlywayBootstrapTest.java`（版本 23 → 24）
- 测试：`backend/src/test/java/com/oa/UserOrgDataTests.java`（创建）

- [ ] **步骤 1：先提交需求文档**（计划依赖它，且工作树挂着用户改动）

```bash
git add docs/zhidieyun_oa_requirements.md && git commit -m "docs: 需求文档新增员工与部门关联关系（SY-02/§6.4）"
```

- [ ] **步骤 2：编写失败的测试** `UserOrgDataTests.java`

```java
package com.oa;

import com.oa.entity.Organization;
import com.oa.enums.UserStatus;
import com.oa.repository.OrganizationRepository;
import com.oa.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 人员-部门关联数据层：V24 字段 + 组织/用户仓库查询。 */
@SpringBootTest
class UserOrgDataTests {

    @Autowired private UserRepository userRepository;
    @Autowired private OrganizationRepository organizationRepository;

    @Test
    void org_repository_tree_queries() {
        // 5 个 DEPT（TECH + V22 四部门），共享库可能更多，只断言已知 code 存在且 parent 查询可用
        List<Organization> tech = organizationRepository.findByOrgCode("TECH")
                .map(List::of).orElse(List.of());
        assertFalse(tech.isEmpty(), "TECH 部门应存在");
        List<Organization> children = organizationRepository.findByParentId(tech.get(0).getId());
        assertNotNull(children);
    }

    @Test
    void user_new_fields_roundtrip() {
        // 演示账号默认值为空，不污染共享库：只断言字段可读
        var admin = userRepository.findByUsername("admin").orElseThrow();
        assertNull(admin.getGender());
        assertNull(admin.getJobTitleId());
        // TECH 在职人数 >= 4（共享库纪律：不精确断言）
        long count = userRepository.countByOrgIdAndStatus(admin.getOrg().getId(), UserStatus.ACTIVE);
        assertTrue(count >= 4, "TECH 在职人数应 >= 4, 实际 " + count);
    }
}
```

- [ ] **步骤 3：运行测试验证失败**

运行：`rsync -a --delete --exclude target --exclude data.sql backend/ /tmp/oa-backend-test/backend/ && cd /tmp/oa-backend-test/backend && rm -rf target && docker run --rm -v $PWD:/app -v $HOME/.m2/repository:/root/.m2/repository -w /app maven:3.9-eclipse-temurin-21 mvn test -Dtest=UserOrgDataTests,FlywayBootstrapTest`
预期：FAIL（编译错误：`getGender`/`findByParentId`/`countByOrgIdAndStatus` 不存在）

- [ ] **步骤 4：实现数据层**

`V24__user_org_fields.sql`（common/，双兼容）：

```sql
ALTER TABLE sys_user ADD COLUMN gender VARCHAR(8) NULL;
ALTER TABLE sys_user ADD COLUMN job_title_id BIGINT NULL;
CREATE INDEX idx_sys_user_job_title ON sys_user (job_title_id);
```

`User.java` 新增（`org` 字段旁）：

```java
@Column(name = "gender", length = 8)
private String gender;

@Column(name = "job_title_id")
private Long jobTitleId;
```

`OrganizationRepository.java` 新增：`List<Organization> findByParentId(Long parentId);`
`UserRepository.java` 新增：`long countByOrgIdAndStatus(Long orgId, UserStatus status);` 与 `List<User> findByStatus(UserStatus status);`
`FlywayBootstrapTest.java`：期望版本 `"23"` → `"24"`。

- [ ] **步骤 5：运行测试验证通过**

运行：同步骤 3 命令
预期：PASS，`Tests run: 4, Failures: 0, Errors: 0`（2 新 + 2 旧 bootstrap 断言）

- [ ] **步骤 6：全量回归**

运行：`cd /tmp/oa-backend-test/backend && docker run --rm -v $PWD:/app -v $HOME/.m2/repository:/root/.m2/repository -w /app maven:3.9-eclipse-temurin-21 mvn test`
预期：BUILD SUCCESS，`Tests run: 102`（100 + 2 新），0 失败

- [ ] **步骤 7：Commit**

```bash
git add backend && git commit -m "feat(user): V24 性别/职称字段 + 组织树/在职人数仓库查询（人员-部门关联数据层）"
```

---

### 任务 T2：部门管理后端（SY-02：树/创建/编辑/删除 + hr:dept）

**文件：**
- 创建：`backend/src/main/java/com/oa/service/OrganizationService.java`
- 创建：`backend/src/main/java/com/oa/controller/OrganizationController.java`
- 创建：`backend/src/main/java/com/oa/dto/OrganizationRequest.java`
- 修改：`backend/src/main/java/com/oa/config/PermissionSeeder.java`
- 测试：`backend/src/test/java/com/oa/OrganizationTests.java`（创建）

**接口契约（后续任务依赖）：**
- `GET /api/organizations/tree` → `List<Map>`，节点 = `{id, orgCode, orgName, parentId, sortOrder, status, memberCount, children: List<同结构>}`，按 sortOrder 升序；`memberCount` = 该部门**直属**在职（ACTIVE）员工数。只读，任意登录用户（`@Transactional(readOnly=true)`）。
- `POST /api/organizations` body `{orgName*（@NotBlank）, orgCode?, parentId?, sortOrder?}` → 建 `orgType="DEPT"`、`status=ENABLED`、orgCode 缺省=orgName。orgCode 已存在 → 400「部门代码已存在」；parentId 指向不存在部门 → 400。写权限 `hr:dept`。
- `PUT /api/organizations/{id}` 同 body 部分更新（null 不动）；父=自己或自己的后代 → 400「部门树存在环」；orgCode 与他人重复 → 400。
- `DELETE /api/organizations/{id}`：有子部门 → 400「该部门下仍有子部门，请先删除子部门」；有在职员工 → 400「该部门仍有 N 名在职员工，请先调整部门归属」；否则**硬删**。
- 权限 helper：`requireDeptManage()`（无 `hr:dept` 且非 ADMIN → 403「无部门管理权限（hr:dept）」），只加在写端点。

- [ ] **步骤 1：编写失败的测试** `OrganizationTests.java`（骨架照 `UserManagementTests`：@SpringBootTest + `loginAs` + @AfterEach 清 SecurityContext；临时部门 code 用 `T2_` 前缀，@AfterAll 硬删清理）

```java
package com.oa;

// imports 省略：OrganizationController, OrganizationRepository, Organization, ApiResponse,
// User, UserRepository, AuthService, ResponseStatusException, HttpStatus, ...

/** 部门管理（SY-02）：树/CRUD/删除保护/权限。 */
@SpringBootTest
@MethodOrderer(MethodOrderer.MethodName.class)
class OrganizationTests {

    @Autowired private OrganizationController organizationController;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AuthService authService;

    @AfterEach
    void tearDown() { SecurityContextHolder.clearContext(); }

    @Test
    void a_tree_departments_with_member_count() {
        loginAs("admin");
        ApiResponse<List<Map<String, Object>>> r = organizationController.tree();
        var tech = r.getData().stream()
                .filter(n -> "TECH".equals(n.get("orgCode"))).findFirst().orElseThrow();
        // 共享库纪律：只断言 memberCount 与仓库查询一致
        long expect = userRepository.countByOrgIdAndStatus(((Number) tech.get("id")).longValue(), UserStatus.ACTIVE);
        assertEquals(expect, ((Number) tech.get("memberCount")).longValue());
        assertTrue((Boolean) tech.get("children") instanceof List);
    }

    @Test
    void b_create_and_duplicate_code() {
        loginAs("admin");
        OrganizationRequest req = new OrganizationRequest();
        req.setOrgName("T2_测试部");
        var r = organizationController.create(req);
        assertEquals(200, r.getCode());
        // 重复 code（缺省=名称）
        OrganizationRequest dup = new OrganizationRequest();
        dup.setOrgName("T2_测试部");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> organizationController.create(dup));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("已存在"));
    }

    @Test
    void c_update_cycle_rejected() {
        loginAs("admin");
        var a = createTmp("T2_环A");
        var b = createTmp("T2_环B");
        // A.parent = B（合法）
        OrganizationRequest ok = new OrganizationRequest();
        ok.setParentId(b.getId());
        assertEquals(200, organizationController.update(a.getId(), ok).getCode());
        // B.parent = A（成环）
        OrganizationRequest bad = new OrganizationRequest();
        bad.setParentId(a.getId());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> organizationController.update(b.getId(), bad));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("环"));
    }

    @Test
    void d_delete_protection() {
        loginAs("admin");
        var dept = createTmp("T2_保护部");
        var child = createTmp("T2_保护子部");
        OrganizationRequest mv = new OrganizationRequest();
        mv.setParentId(dept.getId());
        organizationController.update(child.getId(), mv);
        // 有子部门 → 400
        ResponseStatusException sub = assertThrows(ResponseStatusException.class,
                () -> organizationController.delete(dept.getId()));
        assertTrue(sub.getReason().contains("子部门"));
        // 删掉子部门后，把 finance 临时调过来 → 有在职员工 400（finally 还原 TECH）
        organizationController.delete(child.getId());
        var fin = userRepository.findByUsername("finance").orElseThrow();
        var tech = organizationRepository.findByOrgCode("TECH").orElseThrow();
        fin.setOrg(dept); userRepository.save(fin);
        try {
            ResponseStatusException em = assertThrows(ResponseStatusException.class,
                    () -> organizationController.delete(dept.getId()));
            assertTrue(em.getReason().contains("在职员工"));
        } finally {
            fin.setOrg(tech); userRepository.save(fin);
        }
    }

    @Test
    void e_permission_employee_403_admin_ok() {
        loginAs("employee");
        OrganizationRequest req = new OrganizationRequest();
        req.setOrgName("T2_权限部");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> organizationController.create(req));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        loginAs("admin");
        assertEquals(200, organizationController.create(req).getCode());
    }

    @Test
    void f_member_count_excludes_disabled() {
        loginAs("admin");
        var dept = createTmp("T2_计数部");
        var tech = organizationRepository.findByOrgCode("TECH").orElseThrow();
        var fin = userRepository.findByUsername("finance").orElseThrow();
        fin.setOrg(dept); userRepository.save(fin);
        try {
            assertEquals(1, userRepository.countByOrgIdAndStatus(dept.getId(), UserStatus.ACTIVE));
            // 禁用 finance → 树节点 memberCount 同步归 0（验收 2/5）
            userController.disableUser(fin.getId());
            var node = findNode(organizationController.tree().getData(), dept.getId());
            assertEquals(0L, ((Number) node.get("memberCount")).longValue());
        } finally {
            fin.setStatus(UserStatus.ACTIVE);
            fin.setOrg(tech);
            userRepository.save(fin);
        }
    }

    // helper：createTmp(name) 创建临时部门（code 缺省=名称）；findNode(list, id) 递归查树节点；
    // @AfterAll 先子后父硬删所有 orgCode 以 "T2_" 开头的部门；
    // 需额外 @Autowired UserController userController（测试 f 用）
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：rsync + `mvn test -Dtest=OrganizationTests`
预期：FAIL（编译错误：`OrganizationController` 不存在）

- [ ] **步骤 3：实现**

`OrganizationRequest.java`（DTO）：`@NotBlank String orgName; String orgCode; Long parentId; Integer sortOrder;`

`OrganizationService.java`：
- `tree()`：`findAll` 过滤 `orgType="DEPT"`，一次性 `userRepository.findByStatus(ACTIVE)` 按 orgId 归组计数 → 嵌套 children（parentId 分组，null 挂根），节点 Map 顺序：id/orgCode/orgName/parentId/sortOrder/status/memberCount/children。
- `create(req)`：orgCode 缺省=orgName；`findByOrgCode` 已存在（含停用）→ 400；parentId 非空必须存在 → 400；`orgType="DEPT"`, `status=ENABLED`。
- `update(id, req)`：非 null 字段才改；orgCode 变更时与**其他**部门查重；parent 变更做环检查——从新 parent 沿 `parent` 链上溯，遇到自己 → 400「部门树存在环」（防链上 Hibernate 懒加载：方法标 `@Transactional`）。
- `delete(id)`：`findByParentId(id)` 非空 → 400 子部门消息；`countByOrgIdAndStatus(id, ACTIVE) > 0` → 400 在职员工消息（含 N）；否则 `delete`。

`OrganizationController.java`：`@RequestMapping("/api/organizations")`；GET `/tree`（`@Transactional(readOnly=true)`，无权限门槛）；POST/PUT/DELETE 均 `@Transactional` + `requireDeptManage()`（注入 `AuthService`，`getCurrentUser()`，ADMIN 角色直通，否则查权限码 `hr:dept`）。

`PermissionSeeder.PERMS` 人事组追加一行：`new String[]{"hr:dept", "部门管理", "人事"}`（**不动** NORMAL 组基础 13 项）。

- [ ] **步骤 4：运行测试验证通过**

运行：rsync + `mvn test -Dtest=OrganizationTests`
预期：PASS（5 个测试）

- [ ] **步骤 5：全量回归**

预期：BUILD SUCCESS，`Tests run: 107`（102 + 5），0 失败（重点看 PermissionTests 是否仍绿——PERMS 追加后 admin 组自动拿到新码，NORMAL 组不变）

- [ ] **步骤 6：Commit**

```bash
git add backend && git commit -m "feat(org): 部门管理后端（树+人数/创建/编辑/删除保护）+ hr:dept 权限码"
```

---

### 任务 T3：员工后端（创建/调岗/性别/职称/筛选 + 职称创建）

**文件：**
- 创建：`backend/src/main/java/com/oa/dto/UserCreateRequest.java`
- 修改：`backend/src/main/java/com/oa/dto/UserUpdateRequest.java`
- 修改：`backend/src/main/java/com/oa/controller/UserController.java`
- 测试：`backend/src/test/java/com/oa/UserCreateTests.java`（创建）；扩展 `backend/src/test/java/com/oa/UserManagementTests.java`（1 处）

**接口契约（T4/T5 前端依赖）：**
- `POST /api/users` body `UserCreateRequest` = `{username*（@NotBlank，正则 `[a-z0-9_.-]{2,32}`）, password*（@NotBlank，长度>=8）, realName*, gender?, orgId?, position?, phone?, email?, supervisorId?, jobLevelId?, jobTitleId?, roleCodes?}`。校验：username 已存在 → 400「用户名已存在」；orgId 指定但部门不存在 → 400「部门不存在」；supervisorId 指定但用户不存在/非 ACTIVE/等于自己 → 400「主管必须为在职员工」；jobLevelId/jobTitleId 指定但字典不存在 → 400「职级不存在」/「职称不存在」；roleCodes 内编码不存在 → 400「角色不存在」。成功 → `status=ACTIVE`，BCrypt 编码密码，响应 `userView`。写权限 `hr:user`。
- `PUT /api/users/{id}` 扩展字段：`orgId`（null=不动，`clearOrg=true`=清除）；`gender`（null=不动）；`jobTitleId`（null=不动，`clearJobTitle=true`=清除）。orgId 指向不存在部门 → 400「部门不存在」；supervisorId 语义不变（总是同步，null=清除），但**非空时必须指向 ACTIVE 用户**（新校验）。响应 Map 追加 `gender, jobTitleId, jobTitleName, orgId, orgName, phone, lastLoginAt`。
- `GET /api/users` 新签名：`listUsers(@RequestParam(defaultValue="active") String status, @RequestParam(required=false) Long orgId, @RequestParam(required=false) String keyword)`。status：`active`=ACTIVE / `disabled`=INACTIVE 或 LOCKED / `deleted`=DELETED / `all`=全部。keyword 匹配 username/realName/phone/employeeNo（like %kw%）。orgId 精确。行 Map 追加 `gender, jobTitleId, jobTitleName, phone, lastLoginAt`（orgId/orgName 已有）。**同步修改**现有调用：`UserManagementTests.disabled_user_cannot_login_and_is_hidden` 里 `listUsers(false)` → `listUsers("active", null, null)`。
- `POST /api/job-titles` body `JobTitleRequest`（与 JobLevelRequest 同构：`code?（缺省=名称）, name*, enabled?`）；code 重复 → 400「职称代码已存在」。
- `disableUser` 追加自守卫：目标是当前登录用户 → 400「不能禁用当前登录用户」（对齐 deleteUser 现有守卫，6.4.5-7 纵深）。

- [ ] **步骤 1：编写失败的测试** `UserCreateTests.java`（骨架照 UserManagementTests；临时用户 username 用 `t3_xxx` 前缀 + 随机后缀防共享库碰撞，org 一律 TECH，@AfterAll 全部软删清理）

```java
/** 员工创建/调岗/筛选（6.4.4 / 6.4.5）。 */
@SpringBootTest
class UserCreateTests {

    @Autowired private UserController userController;
    @Autowired private OrganizationController organizationController;
    @Autowired private UserRepository userRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private AuthService authService;

    @AfterEach
    void tearDown() { SecurityContextHolder.clearContext(); }

    @Test
    void a_create_user_with_dept() {
        loginAs("admin");
        UserCreateRequest req = new UserCreateRequest();
        req.setUsername("t3_zhangsan"); req.setPassword("t3pass123");
        req.setRealName("张三"); req.setGender("男");
        req.setOrgId(techId()); req.setRoleCodes(List.of("EMPLOYEE"));
        var r = userController.createUser(req);
        assertEquals(200, r.getCode());
        loginAs("admin");
        var list = userController.listUsers("active", techId(), null).getData();
        assertTrue(list.stream().anyMatch(m -> "t3_zhangsan".equals(m.get("username"))));
    }

    @Test
    void b_create_dup_username_400() { /* 建一次成功后用同 username 再建 → 400「用户名已存在」 */ }

    @Test
    void c_create_invalid_dict_400() {
        loginAs("admin");
        // 不存在的部门 / 不存在的职称 / 不存在的职级 → 各 400
        var req = baseReq(); req.setOrgId(999999L);
        assertBadRequest(userController.createUser(req), "部门不存在");
        var r2 = baseReq(); r2.setJobTitleId(999999L);
        assertBadRequest(userController.createUser(r2), "职称不存在");
        var r3 = baseReq(); r3.setJobLevelId(999999L);
        assertBadRequest(userController.createUser(r3), "职级不存在");
    }

    @Test
    void d_create_invalid_supervisor_400() {
        loginAs("admin");
        var fin = userRepository.findByUsername("finance").orElseThrow();
        fin.setStatus(UserStatus.INACTIVE); userRepository.save(fin); // 共享库：finally 还原 ACTIVE
        try {
            var req = baseReq(); req.setSupervisorId(fin.getId());
            assertBadRequest(userController.createUser(req), "主管必须为在职员工");
        } finally { fin.setStatus(UserStatus.ACTIVE); userRepository.save(fin); }
    }

    @Test
    void e_update_transfer_dept_updates_tree_count() {
        loginAs("admin");
        var tech = organizationRepository.findByOrgCode("TECH").orElseThrow();
        var market = organizationRepository.findByOrgCode("MARKET").orElseThrow();
        long techBefore = userRepository.countByOrgIdAndStatus(tech.getId(), UserStatus.ACTIVE);
        long marketBefore = userRepository.countByOrgIdAndStatus(market.getId(), UserStatus.ACTIVE);
        var u = createTmpUser(); // t3_lisi, org=TECH
        // 调岗到 MARKET（supervisorId 显式回传现值——null 会清除）
        UserUpdateRequest up = new UserUpdateRequest();
        up.setOrgId(market.getId());
        up.setSupervisorId(u.getSupervisorId());
        userController.updateUser(u.getId(), up);
        // 验收 2：前后 diff——TECH -1、MARKET +1（共享库纪律：不绝对断言）
        assertEquals(techBefore - 1, userRepository.countByOrgIdAndStatus(tech.getId(), UserStatus.ACTIVE));
        assertEquals(marketBefore + 1, userRepository.countByOrgIdAndStatus(market.getId(), UserStatus.ACTIVE));
        // 部门树端点 memberCount 与仓库一致
        var marketNode = findNode(organizationController.tree().getData(), market.getId());
        assertEquals(((Number) marketNode.get("memberCount")).longValue(),
                userRepository.countByOrgIdAndStatus(market.getId(), UserStatus.ACTIVE));
    }

    @Test
    void f_list_status_filter_and_keyword() {
        loginAs("admin");
        var u = createTmp(); // t3_wangwu
        userController.disableUser(u.getId());
        var active = userController.listUsers("active", null, "t3_wangwu").getData();
        var disabled = userController.listUsers("disabled", null, "t3_wangwu").getData();
        assertTrue(active.isEmpty(), "禁用后不在 active");
        assertEquals(1, disabled.size(), "在 disabled 组");
        assertEquals("女", disabled.get(0).get("gender"));
        userController.enableUser(u.getId());
    }

    @Test
    void g_disable_self_400() {
        loginAs("employee");
        var me = userRepository.findByUsername("employee").orElseThrow();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userController.disableUser(me.getId()));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }
}
```

（`findNode(list, id)` 递归查树节点 helper，与 OrganizationTests 同款；`createTmpUser()` 建 `t3_lisi` 临时用户：org=TECH、roleCodes=["EMPLOYEE"]、密码 t3pass123，@AfterAll 软删清理。）

- [ ] **步骤 2：运行测试验证失败**

运行：rsync + `mvn test -Dtest=UserCreateTests`
预期：FAIL（编译错误：`createUser`/`UserCreateRequest` 不存在）

- [ ] **步骤 3：实现**（按上文接口契约；`userView` 抽成私有方法补 6 个新字段，`jobTitleName` 用注入的 `JobTitleRepository` 批量查避免 N+1）

- [ ] **步骤 4：运行测试验证通过**

运行：rsync + `mvn test -Dtest=UserCreateTests,UserManagementTests`
预期：PASS

- [ ] **步骤 5：全量回归**

预期：BUILD SUCCESS，约 `Tests run: 115`，0 失败（注意：改 `listUsers` 签名后搜全仓库调用点 `listUsers(` 逐一适配）

- [ ] **步骤 6：Commit**

```bash
git add backend && git commit -m "feat(user): 员工创建/调岗/性别/职称关联 + 状态/部门/关键字筛选 + 职称创建"
```

---

### 任务 T4：前端系统六 Tab 导航框架 + 部门管理页

**文件：**
- 创建：`frontend/src/api/org.ts`
- 创建：`frontend/src/components/SystemTabs.tsx`
- 创建：`frontend/src/pages/Departments.tsx`
- 修改：`frontend/src/components/AppShell.tsx`
- 修改：`frontend/src/App.tsx`
- 修改：`frontend/src/pages/LeaveManagement.tsx`（左树换共享部门树，6.4.3 联动）

**接口契约（api/org.ts，T5/T6 依赖）：**

```ts
export interface OrgNode {
  id: number; orgCode: string; orgName: string;
  parentId: number | null; sortOrder: number | null; status: string;
  memberCount: number; children: OrgNode[]
}
export const orgApi = {
  tree: () => apiClient.get<OrgNode[]>('/organizations/tree').then(r => r.data),
  create: (b: { orgName: string; orgCode?: string | null; parentId?: number | null; sortOrder?: number | null }) =>
    apiClient.post<OrgNode>('/organizations', b).then(r => r.data),
  update: (id: number, b: Partial<{ orgName: string; orgCode: string | null; parentId: number | null; sortOrder: number | null }>) =>
    apiClient.put<OrgNode>(`/organizations/${id}`, b).then(r => r.data),
  remove: (id: number) => apiClient.delete(`/organizations/${id}`).then(r => r.data),
}
```

- [ ] **步骤 1：`SystemTabs.tsx`**（复制 `AttendanceTabs.tsx` 模式）：六 Tab = `员工 /system/users(hr:user)`、`部门 /system/departments(hr:dept)`、`职称 /system/job-titles(hr:level)`、`职级 /system/job-levels(hr:level)`、`权组 /system/groups(system:permission)`、`设置 /system/settings(system:settings 等三码任一)`；`hasPerm` 逻辑同 AttendanceTabs（ADMIN 直通）；`end` 全部 false（/system 前缀路由各自独立段）。

- [ ] **步骤 2：`AppShell.tsx`**：系统区 3 个导航项（/users、/permission-groups、/settings）合并为 1 项 `{ to: '/system', label: '系统', icon: 齿轮, perm: ['hr:user','hr:dept','hr:level','system:permission','system:settings','system:log','system:company'] }`；面包屑追加：`/system/users`→员工管理、`/system/departments`→部门管理、`/system/job-titles`→职称管理、`/system/job-levels`→职级管理、`/system/groups`→权组与权限、`/system/settings`→系统设置（`/system` 精确 → 员工管理）。

- [ ] **步骤 3：`App.tsx` 路由**：`/system` → `Navigate /system/users`；六个路由各包 `ProtectedRoute`（perm 同 SystemTabs）；旧路由 `/users`、`/permission-groups`、`/settings` 改 `<Navigate to="/system/..." replace>`（深链接兼容）。Users/PermissionGroups/Settings 组件本体不动。

- [ ] **步骤 4：`Departments.tsx`**（部门管理页，规格 6.3.2）：
  - 顶部 `<SystemTabs/>`；数据 = `orgApi.tree()` + 选中部门下 `userApi.list({ status:'active', orgId })`。
  - 左：部门树递归渲染（名称 + `(memberCount)`），节点 hover 出「编辑」「删除」图标按钮；顶部 `+ 创建部门`（顶层）。
  - 右：选中部门标题（`{orgName} · {memberCount} 人`）+ 工具条（`+ 创建新员工` → `navigate('/system/users?create=1&org=' + id)`；姓名搜索框，前端过滤表格）+ 员工表（列：用户名/姓名/部门/职务/手机/邮箱/上级领导（按 supervisorId 反查用户名）/操作（`编辑` → `navigate('/system/users?edit=' + id)`））。
  - 创建部门/编辑部门共用弹窗：名称*、代码（新建可填、编辑锁定，同 LeaveTypes 裁决）、上级部门（树选择器，含"无（顶层）"）、排序。
  - 删除：确认弹窗，后端 400 消息（子部门/在职员工）经 Toast 显示。
  - 权限：路由层 `hr:dept`（读树任意登录用户，写按钮仅 hr:manage 可见——本 Tab 路由已门控 hr:dept，按钮不再重复判断）。

- [ ] **步骤 5：`LeaveManagement.tsx` 联动**：左部门树数据源由 `leaveApi.adminDepartments()` 换为 `orgApi.tree()`（拍平渲染，6.4.3「假期管理复用部门树」）；员工表数据源不变（`leaveApi.adminEmployees` 仍传 orgId）。

- [ ] **步骤 6：验证**

运行：`cd frontend && npx tsc --noEmit`
预期：无错误

- [ ] **步骤 7：Commit**

```bash
git add frontend && git commit -m "feat(system-ui): 系统六Tab导航 + 部门管理页（树/CRUD/部门内建员工）+ 假期管理复用部门树"
```

---

### 任务 T5：前端员工管理页升级 + 职称/职级页（含字典后端配套）

**文件：**
- 创建：`frontend/src/components/EmployeeFormModal.tsx`（T4 的"创建新员工"弹框实现体）
- 修改：`frontend/src/pages/Users.tsx`
- 创建：`frontend/src/pages/JobTitles.tsx`、`frontend/src/pages/JobLevels.tsx`
- 修改：`frontend/src/api/user.ts`
- 修改：`backend/src/main/java/com/oa/controller/UserController.java`（字典改名/删除）、`backend/src/main/java/com/oa/repository/UserRepository.java`（引用计数）
- 测试：`backend/src/test/java/com/oa/JobMasterDataTests.java`（+3）

**接口契约（api/user.ts 扩展）：**

```ts
// UserRow 追加：gender?: string | null; jobTitleId?: number | null; jobTitleName?: string | null;
//              phone?: string | null; lastLoginAt?: string | null
list: (p?: { status?: 'active' | 'disabled' | 'deleted' | 'all'; orgId?: number | null; keyword?: string | null }) =>
  apiClient.get<UserRow[]>('/users', { params: { status: 'active', ...只传非空项 } }).then(r => r.data),
create: (b: { username: string; password: string; realName: string; gender?: string; orgId?: number | null;
  position?: string; phone?: string; email?: string; supervisorId?: number | null;
  jobLevelId?: number | null; jobTitleId?: number | null; roleCodes?: string[] }) =>
  apiClient.post<UserRow>('/users', b).then(r => r.data),
update: (id: number, b: { realName?; position?; phone?; email?; gender?; orgId?; clearOrg?;
  supervisorId?; jobLevelId?; clearJobLevel?; jobTitleId?; clearJobTitle?; roleCodes? }) =>
  apiClient.put<UserRow>(`/users/${id}`, b).then(r => r.data),
jobTitles: (all = true) => apiClient.get<JobTitleRow[]>('/job-titles', { params: { all } }).then(r => r.data),
createJobTitle: (b: { code?: string | null; name: string; enabled?: boolean }) =>
  apiClient.post('/job-titles', b).then(r => r.data),
// JobTitleRow = { id; code; name; enabled }（与 JobLevelRow 同构）
```

注意：现有 `getUsers()`/`getRoles()` 与 `update` 的旧签名调用方（TemplateEditor/ApprovalForm/PermissionGroups/StartProcess/LeaveBalanceDetail）必须继续编译——`update` 旧 body 类型并入新 body（字段全可选）即可。

- [ ] **步骤 1：`EmployeeFormModal.tsx`**：props `{ open, initial?: UserRow | null, defaultOrgId?: number, onClose, onSaved }`。表单字段（规格 6.4.4）：用户名*（编辑时禁用）/姓名*/性别（男/女 select，可空）/部门（树选择，默认 = initial.orgId ?? defaultOrgId）/职务/职称（下拉 = `jobTitles(true)`，已屏蔽项灰显）/职级（下拉，已有模式）/权组（多选 = `getRoles()`）/手机/邮箱/上级领导（下拉 = `list({status:'active'})`，排除自己，可空）/初始密码*（仅创建时，>=8 位）。提交 = create 或 update（全量回传 + clearOrg/clearJobTitle 布尔）。后端 400 消息经 Toast。

- [ ] **步骤 2：`Users.tsx` 重构**（规格 6.3.1）：
  - 子 Tab 四个：`全部员工(status=all) / 正常(active) / 禁用(disabled) / 回收站(deleted)`，替换现有 2 Tab。
  - 工具条：`+ 创建新员工`（开 EmployeeFormModal）、姓名/账号搜索框（keyword，300ms 防抖）、状态由 Tab 决定。
  - 表格列（9 列）：用户名 / 姓名（+gender 小字）/ 职称（jobTitleName，"—" 兜底）/ 部门（orgName）/ 权组（roles join）/ 职务 / 上次登录（formatDateTime(lastLoginAt)，— 兜底）/ 状态（4 态标签，复用 STATUS_META）/ 操作（编辑 | 禁用·启用 | 删除；删除行显示 恢复；`isSelf` 时禁用 禁用/删除 按钮，现有逻辑保留）。
  - 读 `useSearchParams`：`?create=1&org=<id>` → 打开创建弹框（defaultOrgId）；`?edit=<userId>` → 拉 list 找到该用户打开编辑弹框（T4 部门页跳转目标）。
  - 行内职级 select 保留（现有功能）。

- [ ] **步骤 3：`JobLevels.tsx` / `JobTitles.tsx`**（规格 6.3.3/6.3.4，结构相同各 ~120 行）：
  - 顶部 `<SystemTabs/>`；子 Tab：`全部 / 正常(enabled) / 屏蔽(!enabled)`（前端过滤）。
  - 工具条：`+ 创建新职级(称)`、名称搜索。
  - 表格：名称 / 代码 / 状态（正常绿/屏蔽灰）/ 操作（编辑 | 屏蔽·启用 | 删除）。
  - 弹窗：名称*、代码（新建可填、编辑锁定）、描述**不建模**（全局约束偏离）。
  - 数据源（`userApi` 扩展）：`jobLevels(all)` / `jobTitles(all)` / `createJobTitle`（T3）/ `updateJobLevel(id, {name?, enabled?})` / `updateJobTitle(...)` / `removeJobLevel(id)` / `removeJobTitle(id)`。
  - **后端配套**（`UserController` + `JobMasterDataTests` 新增 3 测试）：
    - `PUT /job-levels/{id}`、`/job-titles/{id}` 扩展：`req.name` 非空 → 改名（code 不可变）；enabled 切换沿用现有逻辑（现状已支持，已确认）。
    - `DELETE /job-levels/{id}`、`/job-titles/{id}`：有**非删除**员工引用 → 400「该职级(称)仍被 N 名员工使用，请先解除关联」（`UserRepository` 新增 `long countByJobLevelId(Long)`、`long countByJobTitleIdAndStatusNot(Long, UserStatus status)`）；无引用 → 硬删。
    - 测试：① 改名生效（创建临时职级/职称后改名断言）；② 被引用删除 400（临时用户绑定后删）；③ 无引用删除 200。临时数据 @AfterAll 清理（先解绑用户再删字典）。
  - 权限：路由已门控 `hr:level`。

- [ ] **步骤 4：验证**

运行：后端字典配套先跑 `mvn test -Dtest=JobMasterDataTests`（3 新测试绿）；再 `cd frontend && npx tsc --noEmit`
预期：测试 PASS、tsc 无错误（重点：`update` 签名合并后所有旧调用方零改动编译通过）

- [ ] **步骤 5：全量回归 + Commit**

运行：完整 `mvn test`，预期 `Tests run ≈ 118`，0 失败

```bash
git add frontend backend && git commit -m "feat(system-ui): 员工页四态Tab+9列表格+创建/编辑弹框 + 职级/职称字典页（含字典改名/删除保护）"
```

---

### 任务 T6：跨模块联动 + 全量回归 + 部署 + E2E + README

**文件：**
- 修改：`backend/src/main/java/com/oa/dto/InstanceDTO.java`（+ `initiatorOrgName`、`initiatorPosition`）
- 修改：`backend/src/main/java/com/oa/service/ProcessService.java`（InstanceDTO 组装处从 `user.getOrg().getOrgName()` / `user.getPosition()` 取，空安全）
- 修改：`frontend/src/components/InstanceInfoCard.tsx`（发起人卡片下加 `部门 · 职位` 行，6.4.3「详情页自动带出申请人 姓名/部门/职位」）
- 修改：`README.md`
- 测试：`backend/src/test/java/com/oa/DeptEmployeeAssocTests.java`（创建：端到端跨模块断言）

- [ ] **步骤 1：编写失败的跨模块测试** `DeptEmployeeAssocTests.java`

```java
/** 6.4.5 验收要点集成：审批详情带出部门 + 部门树/人数/选择器联动。 */
@SpringBootTest
class DeptEmployeeAssocTests {

    @Test
    void instance_shows_initiator_org_and_position() {
        // employee 发起一个已发布的 LEAVE 流程（defId 经 approval_types 查 code=LEAVE 的 defId，
        // businessData 用合法 JSON 字符串），然后 GET 实例详情
        // 断言 data.instance.initiatorName 非空 且 initiatorOrgName 与 employee 当前 orgName 一致（验收 1）
    }

    @Test
    void disabled_user_excluded_from_selectors_but_history_kept() {
        // admin 禁用 employee → GET /users?status=active 不含 employee（验收 7）
        //   GET /users?status=disabled 含；恢复后回到 active
        //   已发起的历史实例详情 initiatorOrgName 不受影响（历史记录保留）
    }
}
```

（测试内发起的 Flowable 实例必须清理：complete 任务 + 不部署新 def；复用现有模板，照 LeaveProcessTests 的 approve 辅助。）

- [ ] **步骤 2：运行验证失败** → 实现 InstanceDTO/ProcessService/InstanceInfoCard → 运行通过

- [ ] **步骤 3：全量回归**

运行：rsync + 完整 `mvn test`
预期：BUILD SUCCESS，`Tests run ≈ 122`（115 + T5 的 3 字典 + T6 的 4 集成），0 失败

运行：`cd frontend && npx tsc --noEmit`
预期：干净

- [ ] **步骤 4：部署 + E2E**

运行：仓库根 `./build.sh`（**无参**全量重建）；等 backend 200 后跑 E2E 脚本（/tmp/oa-dept-e2e.py）：
1. admin 建部门 E2E_部门 → `/organizations/tree` 出现、memberCount=0
2. admin 建员工（orgId=新部门, supervisorId=manager, gender=女, jobTitleId=技术总监）→ `/users?status=active&orgId=..` 可见；新部门 memberCount=1
3. 调岗该员工到 TECH → 两个部门人数各 ±1（验收 2）
4. 禁用该员工 → `/users?status=active` 不含、`disabled` 含；tree memberCount 回落（验收 7）
5. employee 发起请假并 manager 审批 → 实例详情含 `initiatorOrgName`（验收 1）
6. 删除空部门 → 200；删除有子部门/有员工的部门 → 400 消息断言
7. `GET /api/leave/admin/departments` 与 `/organizations/tree` 人数口径一致（6.4.3）

- [ ] **步骤 5：README 更新**：「系统模块」一节改写——六 Tab（员工/部门/职称/职级/权组/设置）+ 部门管理（树+人数+CRUD+删除保护）+ 员工-部门关联规则（唯一归属/调岗/主管/人数实时/禁用选择器语义）+ 权限码 `hr:dept`。

- [ ] **步骤 6：Commit + Push**

```bash
git add -A && git commit -m "feat(dept-employee): 审批详情带出部门/职位 + 全量回归部署 E2E 全绿 + README"
git push origin develop
```
