-- V6：抄送记录表（H2/MySQL 双兼容：无引擎子句、独立 CREATE INDEX）
CREATE TABLE cc_record (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    created_at  DATETIME(6)   NULL,
    instance_id BIGINT        NULL,
    node_key    VARCHAR(64)   NULL,
    user_id     BIGINT        NULL,
    PRIMARY KEY (id),
    -- 同一实例对同一人只抄送一次（去重，防重复通知）
    CONSTRAINT uk_cc_instance_user UNIQUE (instance_id, user_id),
    CONSTRAINT fk_cc_instance FOREIGN KEY (instance_id) REFERENCES process_instance (id),
    CONSTRAINT fk_cc_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
);

CREATE INDEX idx_cc_user ON cc_record (user_id);
