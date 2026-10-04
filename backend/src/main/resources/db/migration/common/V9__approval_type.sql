-- V9：审批类型表（智蝶云"审批设置"对标：类型 = 名称 + 分类 + 图标 + 关联流程模板）
-- 预置"报销"类型指向 reimbursement 流程模板（V3/Seeder 保证模板存在）
CREATE TABLE approval_type (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    created_at  DATETIME(6)   NULL,
    updated_at  DATETIME(6)   NULL,
    code        VARCHAR(64)   NOT NULL,
    name        VARCHAR(64)   NOT NULL,
    category    VARCHAR(32)   NULL,
    icon        VARCHAR(32)   NULL,
    description VARCHAR(255)  NULL,
    weight      INT           NOT NULL DEFAULT 0,
    def_id      BIGINT        NOT NULL,
    enabled     TINYINT       NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    CONSTRAINT uk_approval_type_code UNIQUE (code),
    CONSTRAINT fk_approval_type_def FOREIGN KEY (def_id) REFERENCES process_definition (id)
);

CREATE INDEX idx_approval_type_weight ON approval_type (weight);

-- 预置“报销”类型由 ProcessTemplateSeeder 在 Java 侧幂等补种
--（def 由 Java 播种，SQL 侧播种会早于 def 存在）
