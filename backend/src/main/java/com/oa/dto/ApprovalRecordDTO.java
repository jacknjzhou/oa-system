package com.oa.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审批历史记录视图。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRecordDTO {

    private Long id;

    private String taskId;

    private String nodeKey;

    private String nodeName;

    /** SUBMIT / APPROVE / REJECT / TRANSFER / CANCEL */
    private String action;

    private String operatorName;

    private String comment;

    private String fromNode;

    private String toNode;

    private String createdAt;
}
