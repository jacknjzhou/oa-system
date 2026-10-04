-- V7：NotifyType 末尾追加 CC=4（MySQL 8 语法）
ALTER TABLE notification
    DROP CHECK chk_notification_type,
    ADD CONSTRAINT chk_notification_type CHECK (notify_type BETWEEN 0 AND 4);
