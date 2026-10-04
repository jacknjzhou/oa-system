-- 企业信息 + 登录日志（SY-04）
CREATE TABLE company_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL DEFAULT '',
    short_name VARCHAR(64) NOT NULL DEFAULT '',
    logo_url VARCHAR(256) NOT NULL DEFAULT '',
    address VARCHAR(256) NOT NULL DEFAULT '',
    phone VARCHAR(32) NOT NULL DEFAULT '',
    email VARCHAR(128) NOT NULL DEFAULT '',
    credit_code VARCHAR(32) NOT NULL DEFAULT '',
    description VARCHAR(512) NOT NULL DEFAULT '',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
);

CREATE TABLE login_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,
    username VARCHAR(64) NOT NULL,
    login_time DATETIME(6) NOT NULL,
    ip VARCHAR(64) NOT NULL DEFAULT '',
    user_agent VARCHAR(256) NOT NULL DEFAULT '',
    success TINYINT NOT NULL DEFAULT 1,
    fail_reason VARCHAR(128) NOT NULL DEFAULT '',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
);

CREATE INDEX idx_login_log_time ON login_log (login_time);
CREATE INDEX idx_login_log_user ON login_log (user_id);
