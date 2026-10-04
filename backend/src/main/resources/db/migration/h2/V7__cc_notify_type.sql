-- V7：NotifyType 末尾追加 CC=4（H2 语法）
ALTER TABLE notification DROP CONSTRAINT chk_notification_type;
ALTER TABLE notification ADD CONSTRAINT chk_notification_type CHECK (notify_type BETWEEN 0 AND 4);
