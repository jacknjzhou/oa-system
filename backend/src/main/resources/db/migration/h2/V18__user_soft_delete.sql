-- 用户软删除（回收站）+ 放宽状态 CHECK（新增 DELETED=3）
ALTER TABLE sys_user ADD COLUMN deleted_at DATETIME(6) NULL;
ALTER TABLE sys_user DROP CONSTRAINT chk_sys_user_status;
ALTER TABLE sys_user ADD CONSTRAINT chk_sys_user_status CHECK (status BETWEEN 0 AND 3);
