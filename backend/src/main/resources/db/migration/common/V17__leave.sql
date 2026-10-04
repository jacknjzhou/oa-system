CREATE TABLE leave_type (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(32) NOT NULL,
    category VARCHAR(16) NOT NULL DEFAULT 'other',
    quota_type VARCHAR(8) NOT NULL DEFAULT 'none',
    annual_quota INT NOT NULL DEFAULT 0,
    weight INT NOT NULL DEFAULT 100,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_leave_type_code UNIQUE (code),
    CONSTRAINT chk_leave_quota_type CHECK (quota_type IN ('fixed', 'accrual', 'none'))
);

CREATE TABLE leave_balance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    leave_type_id BIGINT NOT NULL,
    quota INT NOT NULL DEFAULT 0,
    used INT NOT NULL DEFAULT 0,
    frozen INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_leave_balance_user_type UNIQUE (user_id, leave_type_id)
);
CREATE INDEX idx_leave_balance_user ON leave_balance (user_id);

CREATE TABLE leave_transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    leave_type_id BIGINT NOT NULL,
    delta INT NOT NULL,
    reason VARCHAR(128) NOT NULL,
    ref_instance_no VARCHAR(32),
    created_at DATETIME(6) NOT NULL
);
CREATE INDEX idx_leave_txn_user_time ON leave_transaction (user_id, created_at);
