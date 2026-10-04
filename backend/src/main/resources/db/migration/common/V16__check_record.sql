CREATE TABLE check_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    check_time DATETIME(6) NOT NULL,
    type VARCHAR(8) NOT NULL,
    source VARCHAR(16) NOT NULL DEFAULT 'manual',
    note VARCHAR(128),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT chk_type CHECK (type IN ('in', 'out')),
    CONSTRAINT chk_source CHECK (source IN ('manual', 'scan'))
);
CREATE INDEX idx_check_record_user_time ON check_record (user_id, check_time);
