package com.oa.enums;

/**
 * 审批动作，映射 TINYINT: SUBMIT=0, APPROVE=1, REJECT=2, TRANSFER=3, CANCEL=4, DENY=5
 * <p>
 * REJECT = 驳回到指定节点（流程继续）；DENY = 拒绝（终止流程）。
 * 同 ordinal 约束：只能末尾追加。
 */
public enum ApprovalAction {
    SUBMIT,
    APPROVE,
    REJECT,
    TRANSFER,
    CANCEL,
    /** 拒绝：终止整个流程实例（区别于驳回到节点） */
    DENY
}
