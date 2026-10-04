# 织蝶云 OA 对标补齐 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 subagent-driven-development（推荐）或 executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 对照 `docs/zhidieyun_oa_requirements.md`（5 模块 30+ 功能点），在现有模块化单体（Spring Boot 3 + Flowable 7 + React 18）基础上补齐审批核心闭环（P1）、审批设置/表单流程设计（P2）、考勤与假期（P3）、系统模块（P4）、整合（P5）。

**架构：** 沿用现有双源架构（Flowable 引擎表 = 流程运行时事实源，自有表存业务快照，`syncInstanceAfterAction()` 收敛）。新增表一律走 Flyway 版本化迁移；审批结果从二元（同意/拒绝）扩展为织蝶云四态（同意/拒绝/驳回/转交）+ 草稿 + 抄送；动态表单以 `formConfig` JSON 承载（21 控件 → React 控件库），可视化流程设计器生成 BPMN XML 后走既有发布/部署管线。

**技术栈：** Java 21 / Spring Boot 3.2.10 / Flowable 7.0.1 / JPA / Flyway（H2+MySQL 双档）/ React 18 + Vite + Tailwind / bpmn-js

**规格：** `docs/zhidieyun_oa_requirements.md`（v2，2026-10-04）

## 全局约束

- 枚举持久化为 `@Enumerated(EnumType.ORDINAL)` → TINYINT + CHECK 范围（如 `chk_pi_status` 0..3）：**新枚举值只能追加在末尾**，禁止在中间插入（会错乱存量行）
- Flyway 双兼容 DDL：禁 `ENGINE/CHARSET` 表选项、`BIT` 不带精度、禁内联 `INDEX`（独立 `CREATE INDEX`）
- **H2 与 MySQL 语法分歧点**（如 `DROP CONSTRAINT` vs `DROP CHECK`）用 vendor 目录拆分：`db/migration/h2/Vx__*.sql` + `db/migration/mysql/Vx__*.sql`，两个 profile 的 `spring.flyway.locations` 各自只挂本 vendor 目录（同版本号不得两目录并存）
- 本地无 JDK/Maven：单测用 `rsync` 到 `/tmp/oa-backend-test` + `docker run maven:3.9-eclipse-temurin-21 mvn test`
- `eclipse-temurin:21-jre-alpine` 无 curl（compose healthcheck 用 wget）
- 每个任务结束：H2 单测全绿 → commit；P1 全部完成后跑 compose MySQL E2E
- 演示账号：admin/admin123、manager/manager123、employee/employee123、finance/finance123

## 现状基线（Gap 分析结论）

| 需求 | 现状 | 结论 |
|------|------|------|
| AP-01 我的申请 | MyInstances 列表 + 状态筛选 | 缺草稿/催办/撤回行操作 |
| AP-02 待我审批 | todo（角色候选组） | ✅ 已满足 |
| AP-03 我已审批 | done 列表 | ✅ 已满足 |
| AP-04 抄送给我 | 无 | **P1 新增** |
| AP-05 详情 | Tracking + 同意/驳回/转交 UI | 缺审批日志视图、常用意见、拒绝（终止） |
| AP-06 创建申请 | StartProcess 固定表单 | 动态表单在 P2 |
| AP-07 模块视图 | 无 | P5 |
| AS-01~03 审批管理 | 模板 CRUD + 发布/停用 | 基本满足 |
| AS-04 表单设计器 | 无 | **P2 新增** |
| AS-05 流程设计器 | 手写 XML | **P2 新增** |
| AS-06 预览 | 无 | P2 |
| AS-07 审批权限 | 无 | P2（权组维度见 P4） |
| AS-08 审批类型 | BusinessType 4 值 enum | **P2 新增** |
| CK-01~03 考勤 | 无 | **P3 新增** |
| HD-01~06 假期 | 无 | **P3 新增**（与审批联动） |
| SY-01 员工 | 基础 CRUD | 缺禁用/软删/回收站 → P4 |
| SY-02 部门 | 树 + 成员 | ✅ 已满足 |
| SY-03/04 职级/职称 | 无 | P4 新增 |
| SY-05 权组 | Role 有，无权限项 | P4 |
| SY-06 企业信息/日志 | 无 | P4 新增（登录日志属非功能硬要求） |

## 审查重点（Review Focus）

1. **枚举中间插入破坏存量数据**：V5 迁移后旧实例（ordinal 0..3）必须仍按原状态读回——`FlywayBootstrapTest` 补断言；任何任务改枚举必须先跑此测试
2. **H2/MySQL CHECK 语法分歧**：vendor 目录拆分后两 profile 都必须能启动——两档 profile 各跑一次上下文启动测试
3. **抄送重复通知**：同一抄送人、同一流程、多节点抄送 → 每流程每人仅 1 条通知（`cc_record` 按 instance+user 去重）
4. **催办/撤回越权**：非发起人调用 → 403；已结束流程催办 → 4xx
5. **草稿 flowable_id 为 null 的唯一约束**：`uk_process_instance_flowable` 下多个 NULL 必须可插（MySQL/H2 均允许多 NULL）——草稿多次创建测试钉住

---

## 路线图

| 阶段 | 内容 | 规模 | 状态 |
|------|------|------|------|
| **P1** | 审批核心闭环：四态+草稿、抄送、催办撤回、审批日志 | 4 任务 | ✅ 已详化（本文档） |
| P2 | 审批设置：审批类型、表单设计器（21 控件）、流程设计器（5 种审批人+条件）、预览 | 4 任务 | 任务级大纲（见下） |
| P3 | 考勤（打卡/设置/统计）+ 假期（类型/三账本/日志）+ 请假审批联动扣减 | 3 任务 | 任务级大纲 |
| P4 | 系统：员工回收站、职级/职称、权组权限清单、登录日志/企业信息 | 4 任务 | 任务级大纲 |
| P5 | 整合：审批主页四 Tab、15 类申请卡片、模块视图、导出打印、README | 2 任务 | 任务级大纲 |

---

## P1：审批核心闭环（详化）

### 任务 1：四态审批结果 + 草稿

**文件：**
- 修改：`backend/src/main/java/com/oa/enums/ProcessInstanceStatus.java`（末尾追加 `DRAFT`）
- 修改：`backend/src/main/java/com/oa/enums/ApprovalAction.java`（末尾追加 `DENY`——"拒绝"=终止流程，与"驳回"=打回节点区分）
- 创建：`backend/src/main/resources/db/migration/h2/V5__approval_extensions.sql`（H2 语法：`ALTER TABLE process_instance DROP CONSTRAINT chk_pi_status; ADD CONSTRAINT chk_pi_status CHECK (status BETWEEN 0 AND 4);` + `approval_record` 的 `chk_approval_record_action` 扩到 0..5）
- 创建：`backend/src/main/resources/db/migration/mysql/V5__approval_extensions.sql`（MySQL 语法：`ALTER TABLE ... DROP CHECK chk_xxx, ADD CHECK (...)`）
- 修改：`backend/src/main/resources/application.yml` / `application-mysql.yml`（`spring.flyway.locations` 增加 `classpath:db/migration/{h2|mysql}`，各 profile 只挂自己的）
- 修改：`backend/src/main/java/com/oa/service/ProcessService.java`（`startInstance` 支持草稿；新增 `submitInstance(Long id)`）
- 修改：`backend/src/main/java/com/oa/service/TaskService.java`（新增 `denyTask`）
- 修改：`backend/src/main/java/com/oa/controller/ProcessController.java`（`POST /process-instances/{id}/submit`）、`TaskController.java`（`POST /tasks/{id}/deny`）
- 修改：`backend/src/main/java/com/oa/dto/InstanceDTO.java`（暴露 `lastAction`/`lastActionAt`，来自最近一条非 SUBMIT 的 approval_record）
- 修改：`frontend/src/pages/MyInstances.tsx`（筛选 Tab 扩为：全部/审批中/同意/拒绝/驳回/撤回/草稿）
- 修改：`frontend/src/pages/ApprovalForm.tsx`（操作按钮四态：同意/拒绝/驳回/转交；拒绝调 `/deny`）
- 测试：`backend/src/test/java/com/oa/BpmnFlowTests.java`、`FlywayBootstrapTest.java`

**关键设计：**
- `startInstance` 请求带 `draft=true` 时：只落 `process_instance`（status=DRAFT，flowableInstanceId=null，不部署不启动引擎）；`POST /{id}/submit` 校验发起人+DRAFT → 启动 Flowable → RUNNING
- "驳回"筛选不新增实例状态：列表"驳回" Tab = `lastAction == REJECT` 且 status=RUNNING（打回节点等待中）——完整"退回发起人重编辑"闭环（含重编辑 BPMN 节点）归 P2 流程设计器
- 驳回现有 `toNodeKey` 语义不变（打回指定节点）；`deny` = 终止实例（REJECTED）

- [ ] **步骤 1：写失败测试**

`BpmnFlowTests` 新增：
```java
// 草稿：创建后 status=DRAFT、flowableInstanceId 为空、不产生任务
// submit 草稿 → RUNNING 且产生首个任务；他人 submit 我的草稿 → 403
// 低额报销：manager 调 /tasks/{id}/deny → 实例 REJECTED
// 高额报销：manager 驳回（toNodeKey=dept_manager）→ 实例仍 RUNNING，lastAction=REJECT
// getInstance/my 列表 DTO 含 lastAction
```

- [ ] **步骤 2：运行确认失败**（草稿/deny 端点不存在）

- [ ] **步骤 3：实现**（枚举末尾追加 → vendor 迁移 → locations 配置 → service/controller/DTO → 前端）

- [ ] **步骤 4：`FlywayBootstrapTest` 补断言**：V5 后启动，存量种子流程状态不变（ordinal 回归）；跑全套 17+ 新用例全绿

- [ ] **步骤 5：Commit**

```bash
git add -A && git commit -m "feat: 审批四态（同意/拒绝/驳回/转交）+ 草稿态——拒绝终止、驳回打回节点、草稿提交启动流程"
```

### 任务 2：抄送（CC 节点 + 抄送给我）

**文件：**
- 创建：`backend/src/main/resources/db/migration/V6__cc_record.sql`（`cc_record` 表：id/created_at/instance_id/user_id/node_key + `uk_cc_instance_user (instance_id, user_id)` 唯一约束——天然去重 + 独立 `CREATE INDEX`；双兼容语法）
- 创建：`backend/src/main/java/com/oa/entity/CcRecord.java` + `repository/CcRecordRepository.java`
- 创建：`backend/src/main/java/com/oa/bpmn/CcDelegate.java`（`@Component("ccDelegate")` JavaDelegate：读流程变量 `ccUserIds`（List<Long>）→ upsert cc_record + 逐人 `notificationService.notify(...)`，NotifyType 追加 `CC`（末尾，V6 同步扩 notification 表 check））
- 修改：`backend/src/main/resources/processes/reimbursement.bpmn20.xml`（`gm_manager` 后加 `serviceTask cc_notify`，`flowable:delegateExpression="${ccDelegate}"`）
- 修改：`ProcessService`（`startInstance` 接受 `ccUserIds` 变量）
- 修改：`ProcessController`（`GET /api/process-instances/cc`——我作为抄送人的实例列表）
- 修改：前端 `types/index.ts`、`api/process.ts`、`AppShell`（导航加"抄送给我"）、新页面 `CcList.tsx`（路由 `/cc`）
- 测试：`BpmnFlowTests`（`ccRecordAndNotificationCreated`：高额报销带 ccUserIds=finance → 流程走完 finance 有 cc_record + 通知；重复抄送同一人不重复通知——`GET /process-instances/cc` 每流程 1 条）

- [ ] **步骤 1：写失败测试**（上述 2 个用例）
- [ ] **步骤 2：运行确认失败**
- [ ] **步骤 3：实现**（V6 迁移 → 实体/仓库 → CcDelegate → BPMN → 端点 → 前端）
- [ ] **步骤 4：全套测试全绿**
- [ ] **步骤 5：Commit**

```bash
git commit -m "feat: 抄送——CC 节点（serviceTask+delegate）、cc_record 去重、抄送给我列表与通知"
```

### 任务 3：催办 + 撤回

**文件：**
- 修改：`ProcessService`（`remindInstance(Long id)`：仅发起人 + 仅 RUNNING → 向所有当前任务办理人 `notifyTaskHolders`；`cancelInstance` 已有，确认仅发起人可调用）
- 修改：`ProcessController`（`POST /api/process-instances/{id}/remind`）
- 修改：`MyInstances.tsx`（行内操作：查看/催办/撤回；已结束的仅查看；草稿显示"提交"）
- 修改：`api/process.ts`（remind 方法）
- 测试：`BpmnFlowTests`（`remindNotifiesCurrentHolders`：发起后 manager 收到催办通知；`remindOthersInstanceForbidden`：他人催办 → 403；`remindFinishedRejected`：已结束 → 4xx；撤回后 status=CANCELLED 且任务清空）

- [ ] **步骤 1：写失败测试**（3 用例）
- [ ] **步骤 2：运行确认失败**
- [ ] **步骤 3：实现**
- [ ] **步骤 4：全套测试全绿**
- [ ] **步骤 5：Commit**

```bash
git commit -m "feat: 催办（通知当前办理人）+ 撤回（发起人取消）——我的申请行内操作闭环"
```

### 任务 4：审批日志 + 常用审批意见

**文件：**
- 修改：`ProcessController`（`GET /api/process-instances/{id}/logs` → approval_record 时间线：操作人/节点/动作（含转交/撤回）/意见/时间——复用 `toRecordDtos`）
- 修改：`ProcessTracking.tsx` / `ApprovalForm.tsx`（"审批日志"按钮 → 弹窗时间线；审批意见框上方"常用意见"下拉：批准，尽快处理 / 同意，无异议 / 请补充说明后重报 / 不同意，理由见意见——前端常量，选中填充）
- 修改：`api/process.ts`（logs 方法）、`types/index.ts`
- 测试：`BpmnFlowTests`（`logsTimelineIncludesTransferAndDeny`：完整流转后 logs 按时间升序含 SUBMIT/APPROVE/TRANSFER 记录，operator_name 非空）

- [ ] **步骤 1：写失败测试**
- [ ] **步骤 2：运行确认失败**
- [ ] **步骤 3：实现**
- [ ] **步骤 4：全套测试全绿**
- [ ] **步骤 5：Commit**

```bash
git commit -m "feat: 审批日志时间线（含转交/撤回留痕）+ 常用审批意见下拉"
```

### P1 收尾（不属于单个任务）

- [ ] compose MySQL E2E：起栈 → 模板含 cc 节点 → 草稿-提交-会签-抄送-催办-撤回 全流程冒烟（扩展 `/tmp/oa-countersign-e2e.sh` 模式）
- [ ] README「流程引擎」章节补充四态/草稿/抄送说明
- [ ] 全套测试 + E2E 通过后 commit（如有遗漏修补）

---

## P2：审批设置（任务级大纲）

> 依赖 P1 完成。表单/流程设计是本计划最大增量。

### 任务 P2-1：审批类型 + 动态表单底座
- `approval_type` 表（V7：name/category/icon/description/weight，8 类预置：人事/财务/考勤/休假/日常/法务/行政/其他）+ CRUD API
- `business_type` 列 TINYINT→VARCHAR 迁移（vendor 拆分，数据 `0..3` → `REIMBURSEMENT..`）；`ProcessInstance.businessType` 改 `@Enumerated(STRING)` 或 String code
- `formConfig` JSON 规范：`[{key, label, control(21 种), required, options?, unit?, suffix?}]`；`FormDesigner` 页面（三栏：控件库/画布/属性面板，简化：控件库 21 控件 + 属性面板必填/列表显示）
- `DynamicForm` React 组件（21 控件渲染 + 校验 + 值收集）替换 StartProcess/ApprovalForm 固定表单；报销/请假/采购三模板用 formConfig 重建

### 任务 P2-2：流程设计器（可视化 → BPMN）
- `FlowDesigner` 页面（节点列表/简易画布）：发起人（部门多选，默认所有人）/ 审批人（5 种：指定成员/主管/部门/发起人自选/发起人自己 × 单人/多人）/ 抄送人 / 条件（优先级 + 字段比较 + 多选）
- 生成 BPMN XML（userTask candidateGroups/candidateUsers + gateway + multiInstance）→ 存 `bpmnXml` → 发布部署
- "主管" 审批人：`sys_user` 加 `supervisor_id` 列（V8）+ delegate 解析
- 发布双态保留（DRAFT/PUBLISHED/DISABLED 已有）

### 任务 P2-3：审批预览 + 审批权限
- 预览：formConfig 只读渲染（五步导航第 4 步）
- 权限：`approval_permission` 表（def_id × role × 申请/查看/管理/编辑/删除）+ 列表页按权组勾选（权组表在 P4，本任务先按 role）

### 任务 P2-4：模板管理完善
- 模板列表：已发布/已启用双标签、编辑/表单/流程/预览/权限/停用 按钮组、15 类系统审批预置（表单+流程+发布，seeder 扩展）

## P3：考勤 + 假期（任务级大纲）

### 任务 P3-1：考勤打卡
- V9：`checkin_setting`（上班/下班/加班三时间，单行）+ `checkin_record`（user_id/date/clock_time/status/ip/days 统计列）
- API：设置 CRUD、`POST /checkins`（打卡：按设置判正常/迟到/早退/加班，记 IP，每天 2 次口径）、我的考勤（月+状态筛选+当月小计）、全部考勤（按员工）
- 前端：考勤页（大圆打卡按钮+时钟、记录表、小计卡片）

### 任务 P3-2：假期类型 + 三账本
- V10：`leave_type`（name/limited/unit(天/小时/半天)/weight，9 类预置）+ `leave_balance`（user_id/type_id/total/used/unit，唯一 user+type）+ `leave_log`（user/type/amount/desc/instance_id 关联审批）
- API：类型 CRUD、余额查询（不限额显示"不限额"）、余额调整留痕

### 任务 P3-3：请假审批联动
- 请假模板（P2 表单：类型/起止/时长/事由）+ 流程（主管 → >N 天加经理）
- 审批通过 → 扣减 leave_balance + 写 leave_log（说明含"查看关联流程"链接）；拒绝 → 不扣减

## P4：系统模块（任务级大纲）

### 任务 P4-1：员工管理增强
- V11：`sys_user` 加 `deleted_at`（软删）；UserStatus 末尾追加 `DELETED`（回收站视图）
- 禁用/删除（入回收站）/恢复；已禁用用户登录 401 + 待办不可见

### 任务 P4-2：职级 + 职称
- V12：`job_level`（一级~四级）+ `job_title`（7 条预置）表，均含"屏蔽"状态（EnableStatus 复用）；`sys_user` 加 `job_level_id`

### 任务 P4-3：权组 + 权限清单
- V13：`permission_group`（系统管理员/普通组预置）+ `permission`（10 大分组×权限项，系统内置清单）+ `group_permissions` + `user_groups`
- 权组 CRUD/成员/权限勾选页；登录接口返回权限码集合，前端 `ProtectedRoute` 支持权限码（替代/叠加角色判断）

### 任务 P4-4：企业信息 + 登录日志
- V14：`company_info`（单行）+ `login_log`（username/browser/os/ip/status/date，登录时从 User-Agent 解析）
- 设置页三 Tab（企业资料/邮件设置=SMTP 配置保存不发信/登录日志列表）

## P5：整合（任务级大纲）

### 任务 P5-1：审批主页
- 四 Tab 主页（我的申请/待我审批/我已审批/抄送给我 + 实时计数）、类型筛选、15 类申请卡片（创建申请页）、具体审批模块视图（我的XX/我的审批/抄送给我）

### 任务 P5-2：导出打印 + 收尾
- 考勤/假期 CSV 导出；我的考勤打印视图（window.print）
- README 全量更新（新模块/账号/权限）、compose E2E 扩展至新模块、全套回归

---

## 自检记录

1. **规格覆盖**：AP-01~06/AS-04~08/CK/HD/SY 全部映射到 P1-P5 任务；AP-07（模块视图）在 P5-1；AS-06 预览在 P2-3；SY-06 邮件"测试邮件服务器"降级为"保存配置"（无 SMTP 发送基建，已在 P4-4 注明）；报告/知识/相册/公告/邮件等织蝶云非 OA 核心分组不在本系统范围（规格 6.3.6 权限清单中按"占位权限项"处理，不实现功能）
2. **步骤歧义**：P1 四任务步骤均给出测试名/断言/端点签名/迁移语法；P2-P5 为大纲级（执行到该阶段时按本计划同一粒度展开为逐步任务——与用户"逐任务审批"工作流一致）
3. **类型一致**：`NotifyType.CC`（任务 2 追加）与任务 4 日志视图引用一致；`lastAction` 字段任务 1 定义、任务 3 撤回后不再更新（CANCEL 作为 lastAction）——任务 3 测试须钉住
4. **审查重点落位**：#1→任务 1 步骤 4（FlywayBootstrapTest）；#2→任务 1 步骤 3（双 profile 启动）；#3→任务 2 测试；#4/#5→任务 1/3 测试
5. **比例**：P1 详化与规格 2.3/2.4 对应；P2-P5 大纲约等于规格章节的 1:1 映射，无冗余代码块
