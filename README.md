# OA 办公系统

企业级 OA 审批系统（模块化单体）：Flowable 流程引擎 + Spring Boot 3 + React 18。
对照 `docs/zhidieyun_oa_requirements.md` 实现：审批核心闭环、审批设置、考勤与假期、系统管理、整合。

## 技术栈

- **后端**: Java 21 + Spring Boot 3.2 + Flowable 7.0（BPMN 引擎）+ Spring Security（JWT + Refresh Token 家族）+ Flyway（H2/MySQL 双兼容）
- **前端**: React 18 + Vite 5 + TypeScript + Tailwind + bpmn-js（流程设计器）
- **数据库**: MySQL 8.0（compose 部署）/ H2 内存库（本地开发、测试），DDL 由 Flyway 迁移统一管理
- **部署**: Docker Compose（backend + frontend + MySQL），nginx 托管前端静态资源

## 文档

| 文件 | 内容 |
| --- | --- |
| `docs/zhidieyun_oa_requirements.md` | 织蝶云功能规格（5 模块 30+ 功能点，本系统对照实现的依据） |
| `docs/oa_solution_architecture.md` | 架构分析、模块完成度评估、生产化加固记录 |
| `docs/superpowers/plans/2026-10-04-zhidieyun-gap-implementation.md` | 补齐实现计划（P1~P5）与决策记录 |

## 功能全景

### 审批核心（P1）
- **四态审批**：通过（可驳回首节点/指定节点）/ 拒绝（整单终止）/ 转办 / 催办（可重复，留痕通知）；实例支持草稿（`DRAFT`）与撤回（运行中/已拒绝均可）
- **抄送**：发起选抄送人 + 流程内置 `cc` serviceTask；「抄送给我」只读列表
- **审批日志时间线**：实例全部审批记录升序展示；审批表单「常用意见」快捷下拉

### 审批设置（P2）
- **审批类型**（`approval_type`）：15 类预置（请假/报销/出差/采购/加班/外出/补卡/用章/用车/合同/付款/预支/转正/招聘/离职），可增删改、屏蔽；code 稳定键关联模板
- **动态表单**：模板 `formConfig` 驱动，19 类控件；三栏式表单设计器（字段库/字段列表/属性面板）
- **可视化流程设计器**：角色/用户/发起人主管/发起人 × 单签/会签/并签 拖排成链，前端生成 BPMN；支持高级模式贴 XML
- **字段级权限**：发起/每个审批节点独立配置 可编辑/只读/隐藏；**审批预览**（formConfig 只读渲染）
- **模板 × 角色 功能权限**（`approval_permission`）：申请/查看/管理/编辑 四权限项勾选
- **模板管理**：草稿/发布/停用生命周期；发布即部署引擎（`ProcessDefinitionDeployer` 启动幂等补部署）；存量实例按旧版本跑完

### 考勤与假期（P3）
- **考勤打卡**（`check_record`）：上下班打卡（手动/扫码）、今日工时、月度统计；CSV 导出；**打印视图**（`/print`，window.print）
- **假期三账本**（`leave_type`/`leave_balance`/`leave_transaction`）：6 类假期（年假/病假/事假/婚假/陪产假/特殊假）；定量额度 = 授予 − 已用 − 冻结；全部流水留痕；CSV 导出
- **请假联动**：发起请假冻结额度（余额不足 400 拦截）→ 审批通过转已用 → 拒绝/撤回释放；幂等（按 实例单号 + 动作标记 防重复记账）

### 系统（P4）
- **员工管理**：启用/禁用（禁用即 403 登录拦截）/软删除进回收站/恢复；自我保护（不能删自己）；职级内联选择
- **职级 + 职称**（`job_level`/`job_title`）：4 职级 + 7 职称预置，可增删、屏蔽
- **权组 + 权限清单**（`permission`/`permission_group`）：28 项内置权限（审批/考勤/假期/公文/印章/合同/费用/采购/人事/系统 十分组）；系统管理员/普通组；**登录响应携带权限码集合**，前端按码门控页面与导航（ADMIN 角色直通）
- **系统设置**：企业信息（单行，抬头/信用代码等）/ 登录日志（成功与失败均记录：IP/UA/原因）/ 关于

### 整合（P5）
- **审批主页**（`/`）：欢迎区（带企业名称）+ 15 类申请卡片（点击直达发起抽屉）+ 四 Tab 概览（待我审批/我的申请/我已审批/抄送给我，实时计数）
- 导出打印：考勤/假期 CSV（BOM，Excel 中文兼容）；考勤打印视图

## 流程引擎（Flowable）要点

- **双源架构**：Flowable 引擎表 = 运行时事实源；自有表存业务快照，每次引擎动作后 `syncInstanceAfterAction()` 收敛
- **角色即候选组**：BPMN `candidateGroups` 写角色 code；待办 = 指派人 ∪ 所属角色候选
- **发起人主管**：`sys_user.supervisor_id`；BPMN `candidateUsers=${supervisorUsername}`，`assignSupervisorDelegate` 开始时解析（无主管回退发起人）
- **会签/并签**：多实例节点 + `flowGroups_<nodeId>` 角色组变量注入；报销金额 > 10000 触发会签（任一拒绝即整单驳回），详情返回 `countersigns[]` 进度
- **模板 XML 更新即部署新版本**，存量实例按旧版本跑到结束（Flowable 语义）

## 演示账号（V3/V4 播种）

| 账号 | 密码 | 角色 |
| --- | --- | --- |
| `admin` | `admin123` | ADMIN（总经理 + 系统管理），全部管理页面 |
| `manager` | `manager123` | MANAGER（部门经理），审批/员工查看 |
| `employee` | `employee123` | EMPLOYEE，发起/审批（普通组权限） |
| `finance` | `finance123` | FINANCE，审批 |

> 权限码由权组聚合随登录下发（普通组 = 基础 13 项；系统管理员 = 全部 28 项）。
> 旧会话 localStorage 中的用户信息无 `permissions` 字段，重新登录即可生效。

## 容器化开发与部署

### 前置条件

Docker + Docker Compose v2。

### 一键启动

```bash
# 构建镜像并后台启动全部服务（后端 jar 在容器内编译，无需本地 Maven）
docker compose up -d --build

# 等待就绪（健康检查：MySQL 5.7/8.x、backend /actuator/health、frontend /）
docker compose ps
```

前端 http://localhost:8081 ，后端 API http://localhost:8080/api 。

### 常用运维命令

```bash
docker compose ps                          # 查看服务状态
docker compose logs -f backend             # 查看日志（实时跟踪）
docker compose exec mysql mysql --user=root --password=root123456 -e "SELECT 1"   # 进入 MySQL
docker compose up -d --force-recreate backend   # 代码变更后重建后端
docker compose down                        # 停止并移除容器（保留数据卷）
docker compose down -v                     # 彻底清理（含 MySQL 数据卷，慎用）
```

### 本地开发（非容器化）

```bash
# 后端：8080 端口，H2 内存库（无需 MySQL；Flyway 建表 + seeder 播种）
cd backend && mvn spring-boot:run

# 前端：Vite 开发服务器（/api 代理到 8080）
cd frontend && npm install && npm run dev
```

## 生产加固说明

### 环境变量（`.env`，已 gitignore）

`JWT_SECRET`（≥32 字符）、`JWT_ACCESS_EXPIRE_MS`、`JWT_REFRESH_EXPIRE_MS`、`CORS_ORIGINS`、
`MYSQL_HOST/PORT/USER/PASSWORD/DB`。全部可经环境变量覆盖，仓库不落地密钥。

### DDL 版本化（Flyway）

21 个迁移版本（V1~V21），`db/migration/common` 双库通用、`db/migration/{h2,mysql}` 供应商差异
（CHECK 语法、NOT NULL 放宽）。H2 用 `ddl-auto: update`，MySQL 用 `ddl-auto: none`（以迁移为准）。

### Refresh Token 家族轮换

SHA-256 哈希落库 + `jti` 家族号；轮换时作废旧 token；**已作废 token 再现 = 窃听重放 → 整族吊销**。

### 已知边界（生产 TODO）

- 审批详情/日志接口未做实例归属校验（演示信任模型）
- 功能权限（模板 start/view/manage）未做运行时拦截，仅管理端约束
- 邮件通知仅站内（notify 表），无 SMTP 发送基建
- 登录日志的 IP 取自 `remoteAddr`，反代场景需配置 `X-Forwarded-For` 解析

## 测试

- 后端：62 个集成测试（`mvn test`，H2），覆盖流程四态/会签/抄送/催办撤回/动态表单/流程设计器/
  假期三账本联动/员工回收站/职级职称/权组权限/企业信息/登录日志/Flyway 引导
- 前端：`npx tsc --noEmit` 类型检查
- 容器 E2E：`/tmp/oa-p3-e2e.sh`（MySQL 全链路冒烟）

## License

MIT
