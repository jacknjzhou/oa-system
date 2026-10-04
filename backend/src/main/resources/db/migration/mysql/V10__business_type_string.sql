-- V10 (MySQL)：business_type 序号 → 字符串（配合动态审批类型；CHECK 语法为 MySQL 方言）
ALTER TABLE process_instance
    DROP CHECK chk_pi_business_type,
    MODIFY COLUMN business_type VARCHAR(64) NULL;
UPDATE process_instance SET business_type = CASE business_type
    WHEN '0' THEN 'REIMBURSEMENT'
    WHEN '1' THEN 'LEAVE'
    WHEN '2' THEN 'PROCUREMENT'
    WHEN '3' THEN 'DOCUMENT'
    ELSE 'OTHER' END
WHERE business_type IS NOT NULL;
