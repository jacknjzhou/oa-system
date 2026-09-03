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

## License

MIT
