-- =====================================================================
-- V2 refresh token 家族轮换表
-- 配套 AuthService 的 refresh 轮换 + 重用检测；仅存 SHA-256 哈希，不落明文。
-- 列类型与 Hibernate 在 MySQL 方言下的生成结果一致（boolean → bit(1)）。
-- =====================================================================

CREATE TABLE refresh_token (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    created_at  DATETIME(6) NULL,
    updated_at  DATETIME(6) NULL,
    active      BIT      NOT NULL,
    expires_at  DATETIME(6) NOT NULL,
    family_id   VARCHAR(36) NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,
    user_id     BIGINT      NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash)
);

-- 独立建索引（MySQL 与 H2 均支持；表内 INDEX 子句 H2 不识别）
CREATE INDEX idx_refresh_token_family ON refresh_token (family_id);
