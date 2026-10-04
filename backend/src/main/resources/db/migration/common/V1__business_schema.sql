-- =====================================================================
-- V1 业务表初始化（9 张）
-- 与 JPA 实体一一对应；此前由 Hibernate ddl-auto=update 生成，
-- 现由 Flyway 接管 DDL 版本化（Hibernate 在生产置为 none）。
-- Flowable 引擎表（ACT_*/FLW_*）由 flowable database-schema-update 自管，不在本脚本范围。
-- 注意：采用本脚本后，已有数据需先备份/清空（建表不幂等）。
-- =====================================================================

CREATE TABLE organization (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    created_at  DATETIME(6)  NULL,
    updated_at  DATETIME(6)  NULL,
    org_code    VARCHAR(64)  NOT NULL,
    org_name    VARCHAR(128) NOT NULL,
    org_type    VARCHAR(32)  NULL,
    path        VARCHAR(512) NULL,
    sort_order  INT          NULL,
    status      TINYINT      NOT NULL,
    parent_id   BIGINT       NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_organization_org_code UNIQUE (org_code),
    CONSTRAINT fk_organization_parent FOREIGN KEY (parent_id) REFERENCES organization (id),
    CONSTRAINT chk_organization_status CHECK (status BETWEEN 0 AND 1)
);

CREATE TABLE role (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    created_at  DATETIME(6)  NULL,
    updated_at  DATETIME(6)  NULL,
    description VARCHAR(256) NULL,
    is_system   BIT       NOT NULL,
    role_code   VARCHAR(64)  NOT NULL,
    role_name   VARCHAR(128) NOT NULL,
    sort_order  INT          NULL,
    status      TINYINT      NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_role_code UNIQUE (role_code),
    CONSTRAINT chk_role_status CHECK (status BETWEEN 0 AND 1)
);

CREATE TABLE sys_user (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    created_at     DATETIME(6)  NULL,
    updated_at     DATETIME(6)  NULL,
    email          VARCHAR(128) NULL,
    employee_no    VARCHAR(64)  NULL,
    last_login_at  DATETIME(6)  NULL,
    password_hash  VARCHAR(128) NOT NULL,
    phone          VARCHAR(32)  NULL,
    position       VARCHAR(64)  NULL,
    real_name      VARCHAR(64)  NULL,
    status         TINYINT      NOT NULL,
    username       VARCHAR(64)  NOT NULL,
    org_id         BIGINT       NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_user_username UNIQUE (username),
    CONSTRAINT fk_sys_user_org FOREIGN KEY (org_id) REFERENCES organization (id),
    CONSTRAINT chk_sys_user_status CHECK (status BETWEEN 0 AND 2)
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES role (id)
);

CREATE TABLE process_definition (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    created_at   DATETIME(6)  NULL,
    updated_at   DATETIME(6)  NULL,
    bpmn_xml     TEXT         NOT NULL,
    category     VARCHAR(64)  NULL,
    def_key      VARCHAR(64)  NOT NULL,
    description  VARCHAR(500) NULL,
    form_config  TEXT         NULL,
    name         VARCHAR(128) NOT NULL,
    published_at DATETIME(6)  NULL,
    status       TINYINT      NOT NULL,
    version      INT          NOT NULL,
    creator_id   BIGINT       NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_process_definition_creator FOREIGN KEY (creator_id) REFERENCES sys_user (id),
    CONSTRAINT chk_process_definition_status CHECK (status BETWEEN 0 AND 2)
);

CREATE TABLE process_instance (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    created_at          DATETIME(6)   NULL,
    updated_at          DATETIME(6)   NULL,
    business_data       TEXT          NULL,
    business_id         VARCHAR(64)   NULL,
    business_type       TINYINT       NULL,
    completed_at        DATETIME(6)   NULL,
    current_node        VARCHAR(64)   NULL,
    def_version         INT           NULL,
    flowable_instance_id VARCHAR(64)  NULL,
    instance_no         VARCHAR(64)   NOT NULL,
    priority            TINYINT       NULL,
    status              TINYINT       NOT NULL,
    submitted_at        DATETIME(6)   NULL,
    title               VARCHAR(200)  NOT NULL,
    def_id              BIGINT        NULL,
    initiator_id        BIGINT        NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_process_instance_no UNIQUE (instance_no),
    CONSTRAINT uk_process_instance_flowable UNIQUE (flowable_instance_id),
    CONSTRAINT fk_process_instance_def FOREIGN KEY (def_id) REFERENCES process_definition (id),
    CONSTRAINT fk_process_instance_initiator FOREIGN KEY (initiator_id) REFERENCES sys_user (id),
    CONSTRAINT chk_pi_business_type CHECK (business_type BETWEEN 0 AND 3),
    CONSTRAINT chk_pi_priority CHECK (priority BETWEEN 0 AND 3),
    CONSTRAINT chk_pi_status CHECK (status BETWEEN 0 AND 3)
);

CREATE TABLE approval_record (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    created_at     DATETIME(6)   NULL,
    updated_at     DATETIME(6)   NULL,
    action         TINYINT       NOT NULL,
    comment_text   VARCHAR(1000) NULL,
    from_node      VARCHAR(64)   NULL,
    node_key       VARCHAR(64)   NULL,
    node_name      VARCHAR(128)  NULL,
    operator_name  VARCHAR(64)   NULL,
    flowable_task_id VARCHAR(64) NULL,
    to_node        VARCHAR(64)   NULL,
    instance_id    BIGINT        NULL,
    operator_id    BIGINT        NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_approval_record_instance FOREIGN KEY (instance_id) REFERENCES process_instance (id),
    CONSTRAINT fk_approval_record_operator FOREIGN KEY (operator_id) REFERENCES sys_user (id),
    CONSTRAINT chk_approval_record_action CHECK (action BETWEEN 0 AND 4)
);

CREATE TABLE notification (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    created_at  DATETIME(6)   NULL,
    updated_at  DATETIME(6)   NULL,
    content     VARCHAR(1000) NULL,
    is_read     BIT        NOT NULL,
    notify_type TINYINT       NOT NULL,
    read_at     DATETIME(6)   NULL,
    ref_id      VARCHAR(64)   NULL,
    ref_type    TINYINT       NULL,
    title       VARCHAR(200)  NOT NULL,
    user_id     BIGINT        NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT chk_notification_type CHECK (notify_type BETWEEN 0 AND 3),
    CONSTRAINT chk_notification_ref CHECK (ref_type BETWEEN 0 AND 2)
);

CREATE TABLE document (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    created_at    DATETIME(6)   NULL,
    updated_at    DATETIME(6)   NULL,
    archived_at   DATETIME(6)   NULL,
    content       TEXT          NULL,
    doc_no        VARCHAR(64)   NULL,
    doc_type      TINYINT       NULL,
    published_at  DATETIME(6)   NULL,
    secrecy_level TINYINT       NULL,
    status        TINYINT       NOT NULL,
    title         VARCHAR(200)  NOT NULL,
    urgency       TINYINT       NULL,
    author_id     BIGINT        NULL,
    instance_id   BIGINT        NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_document_author FOREIGN KEY (author_id) REFERENCES sys_user (id),
    CONSTRAINT fk_document_instance FOREIGN KEY (instance_id) REFERENCES process_instance (id),
    CONSTRAINT chk_document_type CHECK (doc_type BETWEEN 0 AND 4),
    CONSTRAINT chk_document_secrecy CHECK (secrecy_level BETWEEN 0 AND 3),
    CONSTRAINT chk_document_status CHECK (status BETWEEN 0 AND 2),
    CONSTRAINT chk_document_urgency CHECK (urgency BETWEEN 0 AND 2)
);
