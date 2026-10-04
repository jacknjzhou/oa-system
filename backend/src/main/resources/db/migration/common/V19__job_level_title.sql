-- 职级 / 职称（SY-02）：均含“屏蔽”（enabled=0 时不可选）
CREATE TABLE job_level (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(8) NOT NULL,
    name VARCHAR(32) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_job_level_code UNIQUE (code)
);

CREATE TABLE job_title (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(16) NOT NULL,
    name VARCHAR(64) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_job_title_code UNIQUE (code)
);

ALTER TABLE sys_user ADD COLUMN job_level_id BIGINT NULL;
