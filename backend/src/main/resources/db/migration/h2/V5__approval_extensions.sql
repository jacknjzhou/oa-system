-- V5：审批四态与草稿（H2 语法——CHECK 约束用 CONSTRAINT 关键字）
-- ProcessInstanceStatus 末尾追加 DRAFT=4：RUNNING/COMPLETED/CANCELLED/REJECTED 不变
ALTER TABLE process_instance DROP CONSTRAINT chk_pi_status;
ALTER TABLE process_instance ADD CONSTRAINT chk_pi_status CHECK (status BETWEEN 0 AND 4);

-- ApprovalAction 末尾追加 DENY=5：SUBMIT/APPROVE/REJECT/TRANSFER/CANCEL 不变
ALTER TABLE approval_record DROP CONSTRAINT chk_approval_record_action;
ALTER TABLE approval_record ADD CONSTRAINT chk_approval_record_action CHECK (action BETWEEN 0 AND 5);
