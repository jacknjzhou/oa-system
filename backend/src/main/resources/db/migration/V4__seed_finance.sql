-- =====================================================================
-- V4 播种数据：FINANCE 角色 + finance 用户（会签流程的财务审批人）
-- 幂等写法同 V3。密码为 BCrypt 编码（明文 finance123）。
-- 会签场景：报销金额 > 1 万时，FINANCE 与 MANAGER 两个组各出一个任务并行审批。
-- =====================================================================

INSERT INTO role (role_code, role_name, description, is_system, sort_order, status, created_at, updated_at)
SELECT 'FINANCE', '财务', '报销会签审批权限', TRUE, 4, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM role WHERE role_code = 'FINANCE');

INSERT INTO sys_user (org_id, username, password_hash, real_name, email, phone, employee_no, position, status, created_at, updated_at)
SELECT (SELECT id FROM organization WHERE org_code = 'TECH'), 'finance',
       '$2b$10$wO.OHvJ9GuLnMCxxMpaQxuFerGvqzdRxYx3BRjhD8gIBlQEvNr3SW',
       '财务专员', 'finance@oa.com', '13800000004', 'EMP004', '会计', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'finance');

INSERT INTO user_roles (user_id, role_id)
SELECT (SELECT id FROM sys_user WHERE username = 'finance'), (SELECT id FROM role WHERE role_code = 'FINANCE') FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM user_roles WHERE user_id = (SELECT id FROM sys_user WHERE username = 'finance')
                  AND role_id = (SELECT id FROM role WHERE role_code = 'FINANCE'));
