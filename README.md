# OA 办公系统

企业级 OA 办公自动化系统设计方案，包含架构设计、数据库 DDL、前端原型、部署架构和安全设计。

## 文档清单

| 文件                           | 内容                            |
| ---------------------------- | ----------------------------- |
| `oa_design_doc.html`         | 整体架构、工作流引擎、ER 关系、API 设计、审批状态机 |
| `oa_ddl.html`                | 12 张表完整 DDL（字段、索引、约束、外键策略）    |
| `oa_frontend_prototype.html` | 前端原型：待办列表、审批表单、流程跟踪           |
| `oa_deployment.html`         | K8s 集群拓扑、CI/CD 流水线、监控可观测性     |
| `oa_security.html`           | JWT 鉴权流、RBAC 权限矩阵、安全防护清单      |

## 技术栈

- **后端**: Java 21 + Spring Boot 3 + Spring Cloud Gateway + Flowable

- **前端**: React / Vue + TypeScript

- **数据库**: MySQL 8.0 + Redis + ElasticSearch

- **消息队列**: Kafka

- **容器编排**: Kubernetes + Istio

- **CI/CD**: GitLab CI + ArgoCD + Harbor

- **监控**: Prometheus + Grafana + Loki + Jaeger

## 架构概览

五层分层架构：接入层 → 网关层 → 应用服务层 → 数据存储层 → 基础设施层

6 大微服务：用户与组织、工作流引擎、公文管理、审批管理、日程与任务、消息通知

## 流程引擎（Flowable）

- **双源架构**：Flowable 引擎表是流程运行时事实源（活动节点、多实例状态），自有表（`process_instance`/`process_node`/`approval_record`）存业务快照；每次引擎动作后 `syncInstanceAfterAction()` 收敛两侧
- **角色即候选组**：BPMN `candidateGroups` 写角色 code，待办 = 指派人 ∪ 所属角色候选
- **会签（多实例并行）**：报销流程金额 > 10000 时进入会签节点（`FINANCE`+`MANAGER` 并行，`completionCondition` 要求全部完成，任一拒绝即驳回流程）；`≤ 10000` 直走部门经理→完成，不受影响
- **会签进度**：流程详情接口返回 `countersigns[]`（节点级 `total/completed/rejected/pending` + 每元素 `groupCode/status/assignee`）；元素变量存于多实例循环 execution 作用域，由服务层 join Flowable 引擎表（`ACT_RU_*`/`ACT_HI_*`，注意 MySQL 表名大小写敏感）取回
- **演示账号**（V3/V4 播种）：`admin/admin123`（含总经理与系统管理）、`manager/manager123`、`employee/employee123`、`finance/finance123`
- 模板 XML 修改后经「流程模板」页面更新并部署新版本，**存量流程实例继续按旧版本跑到结束**（Flowable 语义）

## 容器化开发与部署

### 前置条件

- Docker Desktop / Docker Engine ≥ 24，含 Compose v2（`docker compose` 子命令）

- 后端与前端均为多阶段构建（Maven → JRE、node → nginx），编译打包全部在容器内完成，无需本地 JDK/Maven/Node 环境

### 一键启动

```bash
# 构建镜像并后台启动全部服务（后端 jar 在容器内编译，无需本地 Maven）
docker compose up -d --build
```

启动完成后：

- 前端页面：<http://localhost>

- 后端健康检查：<http://localhost:8080/actuator/health>

- 前端容器内 nginx 将 `/api/` 反向代理到后端 8080 端口

### 常用运维命令

```bash
# 查看服务状态
docker compose ps

# 查看日志（实时跟踪）
docker compose logs -f backend
docker compose logs -f frontend

# 进入容器排查
docker compose exec backend sh
docker compose exec frontend sh

# 重启单个服务
docker compose restart backend

# 代码变更后重建并启动单个服务
docker compose up -d --build backend

# 停止并移除容器（保留镜像）
docker compose down

# 停止并移除容器 + 镜像（彻底清理）
docker compose down --rmi all

# 查看/清理镜像
docker compose images
docker image prune -f
```

### 本地开发（非容器化）

后端与前端可分别本地起服务调试，配合容器中的另一端使用：

```bash
# 后端：默认 8080 端口，H2 内存库
cd backend && mvn spring-boot:run

# 前端：Vite 开发服务器，热更新
cd frontend && npm install && npm run dev
```

## 生产加固说明

### 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `JWT_SECRET` | 开发默认值（已轮换） | JWT 签名密钥，≥32 字节；**生产必须通过 `.env` 覆盖**（docker compose 自动加载项目目录下 `.env`，已 gitignore）。旧仓库历史里出现过硬编码密钥，应视为已泄露，生产一律换新 |
| `JWT_ACCESS_TTL` | `1800000`（毫秒） | 访问令牌有效期，默认 30 分钟 |
| `JWT_REFRESH_TTL` | `604800000`（毫秒） | 刷新令牌有效期，默认 7 天 |
| `OA_CORS_ORIGINS` | 本地开发端口 | CORS 白名单，逗号分隔；生产填实际前端域名，如 `https://oa.example.com` |
| `FLYWAY_ENABLED` | `true` | 是否启用 Flyway（默认两档 profile 均启用，统一建表/播种路径） |

### DDL 版本化（Flyway）

- 业务表结构由 `backend/src/main/resources/db/migration/` 下的 Flyway 迁移管理：`V1__business_schema.sql`（9 张业务表）/ `V2__refresh_token.sql` / `V3__seed.sql`（admin/manager/employee 演示账号）；**新增表/改列必须新增迁移文件，禁止修改已发布迁移**
- **两档 profile 统一启用 Flyway**（H2 开发/测试 + MySQL 生产走同一条 DDL 路径）；`spring.sql.init.mode: never`——原 `data.sql` 播种已版本化为 `V3__seed.sql`（`WHERE NOT EXISTS` 幂等），不再有脚本/建表顺序问题
- `mysql` profile：`ddl-auto: none`，建表完全交给 Flyway；`baseline-on-missing-version: true` 使**已有旧库**首次升级时自动打基线（保留存量数据，V1 视为已应用，直接执行 V2/V3）
- 本地 H2（默认 profile）：`ddl-auto: update` 保留（实体漂移时自动补齐，仅内存库无副作用）
- 配置注意：`spring.jpa.defer-datasource-initialization` 必须为 `false`——为 `true` 时 Boot 会把 `EntityManagerFactory` 登记为"数据库初始化器"，与 `flyway` 互为 `dependsOn` 形成环，Hibernate 6.4 + Boot 3.2 组合下启动即失败（有 `FlywayBootstrapTest` 回归钉住）
- 本地开发注意：`mvn` 不会删除 `target/classes` 里已删资源的陈旧副本，改动资源文件后建议 `mvn clean` 一次

### Refresh Token 家族轮换

- 登录时创建 token 家族（family）；每次 refresh **作废旧 token、签发新 token**（数据库仅存 SHA-256 哈希，不落明文）
- **已作废的 token 再次出现（疑似被盗重放）→ 吊销整个家族**，之后该会话所有 token 一律 401，重新登录即可恢复
- 已知局限：logout 黑名单是**进程内** Map，重启丢失、多实例不共享（生产多实例部署需换 Redis；当前单实例可接受）

### 生产部署检查清单

- [ ] `.env` 中设置随机 `JWT_SECRET`（`openssl rand -hex 32`）
- [ ] `.env` 中设置实际 `OA_CORS_ORIGINS`
- [ ] 修改 `docker-compose.yml` 中 MySQL 密码（当前 `oa123456` 仅开发用）
- [ ] 前端经 HTTPS 反代（nginx/网关）暴露，禁止直连 8080
- [ ] 首次升级旧库：直接 `docker compose up -d --build` 即可（自动基线，数据保留）；全新部署无需任何额外操作
- [ ] 如后续多实例部署：logout 黑名单迁移至 Redis（当前为进程内）

## License

MIT
