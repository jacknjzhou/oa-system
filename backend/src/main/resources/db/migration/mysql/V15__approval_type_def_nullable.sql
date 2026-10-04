-- 审批类型与模板解耦：15 个系统预设多数未绑定模板，def_id 允许为空
ALTER TABLE approval_type MODIFY COLUMN def_id BIGINT NULL;
