-- =====================================================================
-- 初始化数据：组织 / 角色 / 用户 / 用户角色关联 / 流程定义
-- 密码均使用 BCrypt 编码（Spring Security BCryptPasswordEncoder 可校验 $2a$/$2b$ 前缀）
-- 使用子查询关联自然键，避免显式主键与自增序列冲突
-- =====================================================================

-- 组织架构：技术部
INSERT INTO organization (org_code, org_name, org_type, sort_order, path, status, created_at, updated_at)
VALUES ('TECH', '技术部', 'DEPT', 1, '/1/', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 角色：ADMIN / MANAGER / EMPLOYEE
INSERT INTO role (role_code, role_name, description, is_system, sort_order, status, created_at, updated_at) VALUES
('ADMIN',    '系统管理员', '拥有系统全部权限',   TRUE, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('MANAGER',  '部门经理',   '部门审批与管理权限', TRUE, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('EMPLOYEE', '普通员工',   '基础业务权限',       TRUE, 3, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 用户：admin / manager / employee
INSERT INTO sys_user (org_id, username, password_hash, real_name, email, phone, employee_no, position, status, created_at, updated_at) VALUES
((SELECT id FROM organization WHERE org_code='TECH'), 'admin',
 '$2b$10$psGLzTCPhy5ZD2PucxSyIuMaJrwUi7v8fI8GokW6grnaGWvcikwF2',
 '系统管理员', 'admin@oa.com', '13800000001', 'EMP001', '管理员', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM organization WHERE org_code='TECH'), 'manager',
 '$2b$10$g0j.cBRhpF3GyUwWCzyu6eQodToGtiDVEQvJBNjapc9aS6/UfYmVK',
 '部门经理', 'manager@oa.com', '13800000002', 'EMP002', '经理', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM organization WHERE org_code='TECH'), 'employee',
 '$2b$10$nNG2izS5jKTKpPp93NZ0POep1khyGRt2lBc5cvVoSXy7cKV19YF2W',
 '普通员工', 'employee@oa.com', '13800000003', 'EMP003', '员工', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 用户角色关联（admin 仅 ADMIN，manager 仅 MANAGER，employee 仅 EMPLOYEE，
-- 保证流程按角色路由时：部门经理审批 -> manager，总经理审批 -> admin）
INSERT INTO user_roles (user_id, role_id) VALUES
((SELECT id FROM sys_user WHERE username='admin'),    (SELECT id FROM role WHERE role_code='ADMIN')),
((SELECT id FROM sys_user WHERE username='manager'),  (SELECT id FROM role WHERE role_code='MANAGER')),
((SELECT id FROM sys_user WHERE username='employee'), (SELECT id FROM role WHERE role_code='EMPLOYEE'));

-- 流程定义：报销审批
-- 流转：开始 -> 部门经理审批(MANAGER) -> 金额判断(>1万走总经理审批(ADMIN), 否则结束) -> 结束
INSERT INTO process_definition (def_key, name, version, category, form_config, node_json, status, creator_id, published_at, created_at, updated_at)
VALUES (
  'reimbursement',
  '报销审批',
  1,
  'finance',
  '{"fields":[{"key":"amount","label":"金额","type":"number"},{"key":"reason","label":"报销事由","type":"text"}]}',
  '{"nodes":[{"key":"start","name":"开始","type":"start","next":"dept_manager"},{"key":"dept_manager","name":"部门经理审批","type":"approval","assigneeRole":"MANAGER","next":"condition_amount"},{"key":"condition_amount","name":"金额判断","type":"condition","conditions":[{"expr":"amount>10000","next":"gm_manager"},{"expr":"default","next":"end"}]},{"key":"gm_manager","name":"总经理审批","type":"approval","assigneeRole":"ADMIN","next":"end"},{"key":"end","name":"结束","type":"end"}]}',
  1,
  (SELECT id FROM sys_user WHERE username='admin'),
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP
);
