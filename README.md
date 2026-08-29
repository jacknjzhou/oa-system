# OA 办公系统

企业级 OA 办公自动化系统设计方案，包含架构设计、数据库 DDL、前端原型、部署架构和安全设计。

## 文档清单

| 文件 | 内容 |
|------|------|
| `oa_design_doc.html` | 整体架构、工作流引擎、ER 关系、API 设计、审批状态机 |
| `oa_ddl.html` | 12 张表完整 DDL（字段、索引、约束、外键策略） |
| `oa_frontend_prototype.html` | 前端原型：待办列表、审批表单、流程跟踪 |
| `oa_deployment.html` | K8s 集群拓扑、CI/CD 流水线、监控可观测性 |
| `oa_security.html` | JWT 鉴权流、RBAC 权限矩阵、安全防护清单 |

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

## License

MIT
