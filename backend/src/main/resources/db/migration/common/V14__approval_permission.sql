-- 审批功能权限（P2-3）：模板 × 角色 × 权限项。
-- perm 取值：start（发起）/ view（查看）/ manage（管理）/ edit（编辑）。
-- H2 / MySQL 8 双兼容（无引擎/字符集选项，BIT 不写精度，索引独立创建）。

CREATE TABLE approval_permission (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    def_id       BIGINT       NOT NULL,
    def_name     VARCHAR(128) NOT NULL,
    role_code    VARCHAR(32)  NOT NULL,
    perm         VARCHAR(16)  NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_ap_def ON approval_permission (def_id);
CREATE INDEX idx_ap_role ON approval_permission (role_code);
