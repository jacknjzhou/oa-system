-- V8：实例抄送名单（ElementCollection 集合表，发起时选定，提交/启动时传给 cc_notify 节点）
CREATE TABLE process_instance_cc_user (
    process_instance_id BIGINT NOT NULL,
    user_id             BIGINT NOT NULL,
    PRIMARY KEY (process_instance_id, user_id),
    CONSTRAINT fk_picc_instance FOREIGN KEY (process_instance_id) REFERENCES process_instance (id),
    CONSTRAINT fk_picc_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
);

CREATE INDEX idx_picc_user ON process_instance_cc_user (user_id);
