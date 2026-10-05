-- 人员-部门关联（SY-02/§6.4）：员工性别 + 职称外键。全部 nullable，不触碰存量数据。
ALTER TABLE sys_user ADD COLUMN gender VARCHAR(8) NULL;
ALTER TABLE sys_user ADD COLUMN job_title_id BIGINT NULL;
CREATE INDEX idx_sys_user_job_title ON sys_user (job_title_id);
