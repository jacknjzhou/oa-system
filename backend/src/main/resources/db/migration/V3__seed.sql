-- =====================================================================
-- V3 播种数据：组织 / 角色 / 用户 / 用户角色关联（从 data.sql 迁入 Flyway，保证先于业务使用、先于/独立于 Hibernate DDL）
-- 幂等写法（MySQL 8 与 H2 均支持）： INSERT ... SELECT ... FROM DUAL WHERE NOT EXISTS
-- 密码均使用 BCrypt 编码（Spring Security BCryptPasswordEncoder 可校验 $2a$/$2b$ 前缀）
-- 流程模板元数据仍由 ProcessTemplateSeeder 在应用启动时按需补种（带 Java 侧幂等判断）
-- =====================================================================

-- 组织架构：技术部
INSERT INTO organization (org_code, org_name, org_type, sort_order, path, status, created_at, updated_at)
SELECT 'TECH', '技术部', 'DEPT', 1, '/1/', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM organization WHERE org_code = 'TECH');

-- 角色：ADMIN / MANAGER / EMPLOYEE
INSERT INTO role (role_code, role_name, description, is_system, sort_order, status, created_at, updated_at)
SELECT 'ADMIN', '系统管理员', '拥有系统全部权限', TRUE, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM role WHERE role_code = 'ADMIN');

INSERT INTO role (role_code, role_name, description, is_system, sort_order, status, created_at, updated_at)
SELECT 'MANAGER', '部门经理', '部门审批与管理权限', TRUE, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM role WHERE role_code = 'MANAGER');

INSERT INTO role (role_code, role_name, description, is_system, sort_order, status, created_at, updated_at)
SELECT 'EMPLOYEE', '普通员工', '基础业务权限', TRUE, 3, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM role WHERE role_code = 'EMPLOYEE');

-- 用户：admin / manager / employee
INSERT INTO sys_user (org_id, username, password_hash, real_name, email, phone, employee_no, position, status, created_at, updated_at)
SELECT (SELECT id FROM organization WHERE org_code = 'TECH'), 'admin',
       '$2b$10$psGLzTCPhy5ZD2PucxSyIuMaJrwUi7v8fI8GokW6grnaGWvcikwF2',
       '系统管理员', 'admin@oa.com', '13800000001', 'EMP001', '管理员', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'admin');

INSERT INTO sys_user (org_id, username, password_hash, real_name, email, phone, employee_no, position, status, created_at, updated_at)
SELECT (SELECT id FROM organization WHERE org_code = 'TECH'), 'manager',
       '$2b$10$g0j.cBRhpF3GyUwWCzyu6eQodToGtiDVEQvJBNjapc9aS6/UfYmVK',
       '部门经理', 'manager@oa.com', '13800000002', 'EMP002', '经理', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'manager');

INSERT INTO sys_user (org_id, username, password_hash, real_name, email, phone, employee_no, position, status, created_at, updated_at)
SELECT (SELECT id FROM organization WHERE org_code = 'TECH'), 'employee',
       '$2a$10$LTl6JJWGqGs5ChcENZbb/uVe7drsl.95c/TX.RSW3Ch54v3s5L0ti',
       '普通员工', 'employee@oa.com', '13800000003', 'EMP003', '员工', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'employee');

-- 用户角色关联（admin 仅 ADMIN，manager 仅 MANAGER，employee 仅 EMPLOYEE，
-- 保证流程按角色路由时：部门经理审批 -> manager，总经理审批 -> admin）
INSERT INTO user_roles (user_id, role_id)
SELECT (SELECT id FROM sys_user WHERE username = 'admin'), (SELECT id FROM role WHERE role_code = 'ADMIN') FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM user_roles WHERE user_id = (SELECT id FROM sys_user WHERE username = 'admin'));

INSERT INTO user_roles (user_id, role_id)
SELECT (SELECT id FROM sys_user WHERE username = 'manager'), (SELECT id FROM role WHERE role_code = 'MANAGER') FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM user_roles WHERE user_id = (SELECT id FROM sys_user WHERE username = 'manager'));

INSERT INTO user_roles (user_id, role_id)
SELECT (SELECT id FROM sys_user WHERE username = 'employee'), (SELECT id FROM role WHERE role_code = 'EMPLOYEE') FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM user_roles WHERE user_id = (SELECT id FROM sys_user WHERE username = 'employee'));
