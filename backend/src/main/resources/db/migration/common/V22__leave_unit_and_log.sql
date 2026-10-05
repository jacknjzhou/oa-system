-- V22 假期模块增强（HD-01~08）：单位列 + 流水日志字段 + 新增 4 类 + 部门

-- 1) leave_type 增加单位（day 天 / hour 小时 / half_day 半天；不跨单位换算）
ALTER TABLE leave_type ADD COLUMN unit VARCHAR(8) NOT NULL DEFAULT 'day';

-- 2) leave_transaction 日志字段（HD-03：操作人 / 类型 / 备注 / 直接关联实例）
ALTER TABLE leave_transaction ADD COLUMN operator_id BIGINT;
ALTER TABLE leave_transaction ADD COLUMN txn_type VARCHAR(16) NOT NULL DEFAULT 'GRANT';
ALTER TABLE leave_transaction ADD COLUMN remark VARCHAR(255);
ALTER TABLE leave_transaction ADD COLUMN instance_id BIGINT;

-- 3) 新增 4 类（幂等；既有 6 类由下方 UPDATE 校准属性）
INSERT INTO leave_type (code, name, category, quota_type, annual_quota, weight, enabled, unit, created_at, updated_at)
SELECT 'COMPENSATORY', '调休假', '法定', 'none', 0, 96, 1, 'hour', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM leave_type WHERE code = 'COMPENSATORY');
INSERT INTO leave_type (code, name, category, quota_type, annual_quota, weight, enabled, unit, created_at, updated_at)
SELECT 'MATERNITY', '产假', '法定', 'none', 0, 94, 1, 'day', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM leave_type WHERE code = 'MATERNITY');
INSERT INTO leave_type (code, name, category, quota_type, annual_quota, weight, enabled, unit, created_at, updated_at)
SELECT 'BEREAVEMENT', '丧假', '法定', 'none', 0, 92, 1, 'day', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM leave_type WHERE code = 'BEREAVEMENT');
INSERT INTO leave_type (code, name, category, quota_type, annual_quota, weight, enabled, unit, created_at, updated_at)
SELECT 'MENSTRUAL', '例假', '法定', 'none', 0, 0, 1, 'day', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM leave_type WHERE code = 'MENSTRUAL');

-- 4) 校准既有 6 类属性（规格 5.2 权威属性表；幂等）
UPDATE leave_type SET quota_type = 'fixed', annual_quota = 99, weight = 99, unit = 'day' WHERE code = 'ANNUAL';
UPDATE leave_type SET quota_type = 'none',  annual_quota = 0, weight = 98, unit = 'hour' WHERE code = 'PERSONAL';
UPDATE leave_type SET quota_type = 'none',  annual_quota = 0, weight = 97, unit = 'half_day' WHERE code = 'SICK';
UPDATE leave_type SET quota_type = 'none',  annual_quota = 0, weight = 95, unit = 'day' WHERE code = 'MARRIAGE';
UPDATE leave_type SET quota_type = 'none',  annual_quota = 0, weight = 93, unit = 'day' WHERE code = 'PATERNAL';
UPDATE leave_type SET weight = 90, unit = 'day' WHERE code = 'SPECIAL';

-- 5) 新增 4 个部门（幂等；演示用户不移动部门——四裁决④）
INSERT INTO organization (org_code, org_name, org_type, sort_order, path, status, created_at, updated_at)
SELECT 'MARKET', '市场部', 'DEPT', 2, '/', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM organization WHERE org_code = 'MARKET');
INSERT INTO organization (org_code, org_name, org_type, sort_order, path, status, created_at, updated_at)
SELECT 'OPERATIONS', '运营部', 'DEPT', 3, '/', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM organization WHERE org_code = 'OPERATIONS');
INSERT INTO organization (org_code, org_name, org_type, sort_order, path, status, created_at, updated_at)
SELECT 'ADMIN_DEPT', '行政部', 'DEPT', 4, '/', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM organization WHERE org_code = 'ADMIN_DEPT');
INSERT INTO organization (org_code, org_name, org_type, sort_order, path, status, created_at, updated_at)
SELECT 'HR', '人事部', 'DEPT', 5, '/', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM organization WHERE org_code = 'HR');
