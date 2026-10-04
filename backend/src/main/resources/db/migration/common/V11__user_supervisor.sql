-- 主管（流程设计器“发起人主管”审批人解析）
ALTER TABLE sys_user ADD COLUMN supervisor_id BIGINT NULL;
ALTER TABLE sys_user ADD CONSTRAINT fk_sys_user_supervisor FOREIGN KEY (supervisor_id) REFERENCES sys_user (id);
