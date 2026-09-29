# OA 系统解决方案架构分析

> 基于代码、配置与文档的完整阅读。分析对象：`backend/`（Spring Boot 3.2 + Java 21 + Flowable 7.1.0）、`frontend/`（React 18 + Vite + TS + bpmn-js）、`docs/`（五篇 HTML 设计文档）、`docker-compose.yml`。

---

## 1. 整体架构总览

```
┌─────────────────────────────────────────────────────────────┐
│ 浏览器 (React SPA)                                            │
│  Vite + React 18 + TS + Tailwind + bpmn-js                    │
│  routes: /login /start /tasks /tracking /my-instances /templates
└──────────────────────────┬──────────────────────────────────┘
                           │ /api  (nginx 反代, 同源)
┌──────────────────────────▼──────────────────────────────────┐
│ Spring Boot 3.2 单体 (Java 21)                                │
│  Controller ─► Service ─► Repository (JPA)                   │
│        │           │                                          │
│  JwtAuthFilter    Flowable 7 引擎 (Repository/Runtime/Task/    │
│  (stateless)      History) ── BPMN 运行时事实源                 │
└──────────────────────────┬──────────────────────────────────┘
                           │
        ┌──────────────────┴──────────────────┐
        │  H2 (开发) / MySQL 8 (生产)          │
        │  业务表 9 张 + Flowable 引擎表 ~30 张 │
        └─────────────────────────────────────┘
```

**部署形态**：docker-compose 三服务 —— `mysql`（8.0，healthcheck）+ `backend`（8080）+ `frontend`（nginx :80，`/api` 反代到 backend）。`docker-compose up` 即完整可运行，无 Redis / MQ / ES / 网关。

---

## 2. 技术选型

| 层 | 选型 | 依据 |
|---|---|---|
| 后端框架 | Spring Boot 3.2.5 + Java 21 | web / JPA / Security / Validation |
| 工作流 | **Flowable 7.1.0**（`flowable-spring-boot-starter-process`） | starter 只引入流程引擎模块（无 form/DMN/CMMN）；Repository/Runtime/Task/History 四个 Service 由 starter 自动装配注入，自动部署、`database-schema-update`、history-level 均由 `application.yml` 的 `flowable.*` 驱动 |
| 认证 | JJWT 0.12.5，HS256，access 30min + refresh 7d | 无状态 stateless session |
| 持久化 | Spring Data JPA，`ddl-auto: update` | 默认 H2 内存库（`MODE=LEGACY`，`data.sql` 幂等种子），`mysql` profile 切 MySQL 8 |
| 前端 | React 18 + TS + Vite 5 + React Router 6 + Axios | **bpmn-js** 做流程设计器/查看器，**tailwind** 做样式 |
| 部署 | Docker Compose + nginx | 生产蓝图（K8s/Istio）仅存在于文档 |

---

## 3. 后端分层与模块

标准三层 + 横切包（61 个 Java 文件）：

```
com.oa
├── controller   Auth / Process / Task / Document / User   (5 个 REST 入口)
├── service      Auth / Process / Task / Document / Notification / BpmnXml（共 6 个，用户 CRUD 并入 AuthService）
├── repository   8 个 Spring Data JPA 接口
├── entity       User Role Organization ProcessDefinition ProcessInstance
│                ApprovalRecord Notification Document
├── dto          Request/Response/Token 三类
├── enums        13 个状态/类型枚举
├── security     SecurityConfig · JwtTokenProvider · JwtAuthFilter
├── config       ProcessTemplateSeeder (内置模板种子)
└── exception    GlobalExceptionHandler (统一 {code,message,data})
```

**关键设计决策**：

1. **Flowable 为运行时事实源，自有表存业务快照**（`ProcessService` 类注释原话）。`process_instance.flowable_instance_id` 是唯一桥梁，两个体系通过 id 关联、通过 `syncInstanceAfterAction` 对齐状态。
2. **角色即候选组**：BPMN 里 `flowable:candidateGroups="MANAGER"` 直接使用角色 code，任务查询 = `taskAssignee(username)` ∪ `taskCandidateGroupIn(roleCodes)`，免去了 Flowable 用户组表的维护成本。
3. **业务数据 JSON 透传**：`businessData` 字符串 → 解析为 Map 注入 Flowable 变量 → 网关表达式 `${amount > 10000}` 求值。表单结构 `formConfig` 也是 JSON（前端 DynamicForm 动态渲染），实现了"一套引擎跑所有流程"。
4. **版本治理**：改 BPMN XML → 重新 deploy → 回写 Flowable 内部 version 到 `def.version`，启动实例时钉住 `defVersion`，保证老实例按老版本走。

---

## 4. 核心：工作流引擎设计

### 4.1 审批动作 → 引擎 API 映射

| 业务动作 | Flowable 调用 | 副作用 |
|---|---|---|
| 发起 | `runtimeService.startProcessInstanceByKey(key, instanceNo, vars)` | 写审批记录(start)、经 `syncInstanceAfterAction` 通知活跃任务办理人 |
| 通过 | （无 assignee 先 `claim`）→ `taskService.complete` | 写记录、`syncInstanceAfterAction`：更新 current_node、遍历通知所有活跃任务办理人、流程走完置 COMPLETED |
| 驳回到节点 | `createChangeActivityStateBuilder().moveActivityIdTo(from, to)` | 写记录(fromNode/toNode)、同步实例 |
| 整单驳回 | `runtimeService.deleteProcessInstance` | 实例置 REJECTED、通知发起人 |
| 转办 | `taskService.setAssignee(target)` | 写记录、通知目标人 |
| 取消 | 仅发起人可 `deleteProcessInstance` | 实例置 CANCELLED |

`syncInstanceAfterAction` 是每个动作后的统一收口：查 active task → 更新 `current_node` → 通知候选组；若实例不在运行中则走 `syncCompletionIfEnded` 兜底。**注意它只取第一个 active task 作为当前节点**（串行流没问题，会签并行时会是简化）。

### 4.2 内置报销流程（`reimbursement.bpmn20.xml`）

```
开始 → 部门经理审批(MANAGER) → 金额网关 → 金额>1万 → 总经理审批(ADMIN) → 结束
                                └→ ≤1万 ─────────────────────────────┘
```

带完整 BPMNDI 坐标（供 bpmn-js 渲染）；`ProcessTemplateSeeder` 在首次启动时把 classpath 的 BPMN 同步为一条 PUBLISHED 的 `process_definition` 记录（Flowable 侧由 `classpath*:/processes/` 自动部署）。`BpmnXmlService` 提供 XML 解析工具（取 process id / userTask 列表 / 节点名映射），用于模板校验与流程图高亮。

### 4.3 流程图状态回显

`getInstance` 返回 `bpmnXml + completedActivityIds（历史已完成 userTask/start/end）+ currentActivityIds（运行时活跃）+ approvalRecords`，前端 `BpmnViewer` 据此画绿/蓝高亮 —— 轨迹与待办页共用同一数据契约。

---

## 5. 数据模型（JPA 实体 → 9 张业务表）

| 域 | 表 | 要点 |
|---|---|---|
| 用户域 | `organization` / `sys_user` / `role` / `user_roles` | 组织树(parent 自引用)、BCrypt 哈希、角色多对多 |
| 流程域 | `process_definition` | defKey 唯一 + **bpmn_xml 全量存储** + form_config + 状态机 DRAFT/PUBLISHED/DISABLED |
| | `process_instance` | 业务枢纽：def(版本钉住) + flowable_instance_id + current_node + business_data(JSON) |
| | `approval_record` | 独立于 Flowable 任务表：操作人快照名(operator_name 冗余)、from/to_node 留痕 |
| 业务域 | `document` | 公文 CRUD + 归档，**外键关联 instance** |
| 通知域 | `notification` | 站内信：type/task/process + ref_type+ref_id 泛化引用 |

种子数据（`data.sql`，幂等 `INSERT..SELECT..WHERE NOT EXISTS`）：技术部、ADMIN/MANAGER/EMPLOYEE 三角色、admin/manager/employee 三账号（一一对应流程路由：经理节点→manager、总经理节点→admin）。

---

## 6. 安全架构

- **无状态 JWT**：`SecurityConfig` 禁用 session/CSRF，`JwtAuthFilter` 在 UsernamePasswordFilter 之前，校验通过写 `SecurityContext`（principal=username）。
- **角色新鲜度**：`getCurrentUser()` 每次按 username **回查数据库**取角色 —— JWT 里的 roles 只用于 filter 层认证，业务授权永远用库中最新角色，改角色即时生效（代价是每请求一次 DB 查询）。
- **接口级**：仅 `/api/auth/login`、`/refresh`、`/h2-console/**` 放行，其余一律 `authenticated`。任务操作层在 Service 做二次鉴权（`loadOperableTask`：assignee 本人或候选组命中，否则 403）。
- **Token 生命周期**：access 30min / refresh 7d；logout 将 access token 加入**进程内**黑名单（`ConcurrentHashMap`）。

---

## 7. 前端架构（32 个 TS/TSX 文件）

```
pages(8)  ── Login / StartProcess / TaskList(todo|done) / ApprovalForm
           / ProcessTracking / MyInstances / TemplateList / TemplateEditor
components  AppShell(布局+导航+主题切换) · ProtectedRoute · Modal/Toast/Badge/EmptyState
            BpmnViewer(只读高亮) · DynamicForm(JSON表单) · TemplateFormConfig(表单结构配置)
            ApprovalTimeline · InstanceInfoCard · Theme
api(6)     client.ts(axios 拦截器) + auth/process/task/template/user
utils      bpmn.ts(解析/节点名) · format.ts
types      全量 DTO 类型
```

**关键机制**：

- `client.ts` 双拦截器：请求注 Bearer Token；响应**解包统一 `{code,message,data}`** 结构（调用方直接拿 data），401 清凭证跳登录。
- 表单即数据：模板 `formConfig` JSON → `DynamicForm` 动态渲染 → 提交值序列化为 `businessData` → 后端转 Flowable 变量。发起、模板、流程查看三个页面共享这条链路。
- 流程可视化：`bpmn.ts` 提供模板 XML 构建/流程 id 同步工具；`TemplateEditor` 页面**直接引用 `bpmn-js/lib/Modeler`** 建模（无独立编辑器组件）并回传 XML 存模板 —— 用户可自助"画流程"，无需懂代码。

---

## 8. 文档蓝图 vs 实际实现（Gap 分析）

| 维度 | 文档设计（`docs/` 五篇） | 实际实现 | 评价 |
|---|---|---|---|
| 服务架构 | 6 微服务 + Spring Cloud 网关 + K8s/Istio | **单一 Spring Boot 模块化单体**（5 个 controller 包内分域） | 文档是目标态；当前单体与代码 1:1 吻合 |
| 存储 | MySQL + Redis + ES + 对象存储 + Kafka | 单 MySQL（H2 开发），无 Redis/ES/MQ | 缓存/检索/异步均未落地 |
| 流程建模 | 自研 node_json 五节点模型（会签/或签/加签） | **BPMN 2.0 XML 原生模型 + Flowable** | 实现更工程化：会签/并行用 Flowable multiInstance 即可获得，无需自研；但当前演示流程只用了串行+排他网关 |
| 数据表 | README 称 12 张（DDL 文档实际列 13 张，含 node_definition/task/task_candidate/attachment 等） | 9 张业务表（8 实体 + user_roles 联结表）+ Flowable 引擎表 | node 信息内嵌 BPMN XML，不单独建表，是合理简化 |
| 通知 | 消息通知微服务 | DB 表 `notification` + `NotificationService`，**只有写没有读接口**（无 NotificationController） | 站内信只进不出 |
| 迁移 | — | `ddl-auto: update`，无 Flyway/Liquibase | 生产隐患（见下） |

这个 gap 其实符合 README 的自我定位：**可运行 Monorepo 原型 + 生产蓝图文档**，二者是"现在"与"未来"的关系，不算矛盾。

---

## 9. 亮点

1. **双源一致性收口干净**：所有引擎操作后统一 `syncInstanceAfterAction`，自有表只存"读多写少"的快照，写路径全部走引擎，避免两套状态机漂移。
2. **candidateGroups=角色 code** 的映射设计极简，把 RBAC 与流程路由合一。
3. **前后端全链路打通**：画流程（bpmn-js）→ 存 XML → 部署 Flowable → 动态表单发起 → 网关表达式路由 → 待办/审批/轨迹回显，完整闭环。
4. 种子数据幂等、模板 seeder 幂等，`docker compose up` 开箱即用。
5. 审批记录冗余 `operator_name`、`from/to_node`，历史查询不必 join 引擎表。

## 10. 风险与演进建议

| # | 问题 | 建议 |
|---|---|---|
| 1 | `jwt.secret` 硬编码进仓库（已提交 Git） | 移到环境变量 / K8s Secret，并轮换 |
| 2 | logout 黑名单为**进程内内存**：重启失效、多实例不共享 | 短期够用；横向扩展前换 Redis，或接受"access 30min 自然过期" |
| 3 | `ddl-auto: update` 用于 MySQL 生产 | 引入 Flyway，DDL 版本化 |
| 4 | 文档蓝图（K8s/微服务/Kafka/ES）无任何落地物 | 在 README 标注"目标态 vs 当前态"，或补 GitHub Issue 路线图 |
| 5 | `notification` 只写不读，前端无消息中心 | 补 `GET /api/notifications` + 铃铛组件，闭环站内信 |
| 6 | Document API 已实现但**前端无公文页面** | 补公文模块 UI，或在文档中说明属未启用模块 |
| 7 | `async-executor-activate: false`：定时器、异步续流、长期作业不工作 | 启用 Job 执行器（或注明限制），否则 BPMN 的 timer 节点是死的 |
| 8 | 当前节点只取 `activeTasks.get(0)`，会签并行时 current_node 不准 | 存 active task 集合或在前端用 `currentActivityIds` 渲染 |
| 9 | CORS 用 `allowedOriginPatterns("*")` + 反代同源 | 生产收敛为明确域名白名单 |
| 10 | refresh：`/api/auth/refresh` 每次返回**新** refresh token（值轮换），但旧 token 不失效、不存储、无重用检测（无 token family） | 接受现状需在文档说明；追求安全可加 family 轮换 + 持久化 |

---

## 总结

这是一套「**BPMN 原生 + Flowable 引擎为运行时事实源、自有表存业务快照**」的审批中台单体实现，前后端通过统一 `{code,message,data}` 契约和 formConfig/businessData 两个 JSON 管道打通，安全走无状态 JWT + 角色候选组路由；文档中的微服务/K8s 是预留蓝图，当前真正落地的是一份可 docker-compose 一键运行、审批全链路闭环的模块化单体。
