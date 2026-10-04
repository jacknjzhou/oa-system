-- V5：审批四态与草稿（MySQL 8 语法——CHECK 约束用 CHECK 关键字，8.0.16+ 强制执行）
-- ProcessInstanceStatus 末尾追加 DRAFT=4：RUNNING/COMPLETED/CANCELLED/REJECTED 不变
ALTER TABLE process_instance
    DROP CHECK chk_pi_status,
    ADD CONSTRAINT chk_pi_status CHECK (status BETWEEN 0 AND 4);

-- ApprovalAction 末尾追加 DENY=5：SUBMIT/APPROVE/REJECT/TRANSFER/CANCEL 不变
ALTER TABLE approval_record
    DROP CHECK chk_approval_record_action,
    ADD CONSTRAINT chk_approval_record_action CHECK (action BETWEEN 0 AND 5);
