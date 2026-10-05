package com.oa.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流程实例视图（业务快照 + 当前节点信息）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InstanceDTO {

    private Long id;

    private String instanceNo;

    private Long defId;

    private String defKey;

    private String defName;

    private Integer defVersion;

    private String title;

    private Long initiatorId;

    private String initiatorName;

    /** 发起人部门名（6.4.3：来自 sys_user.org，null 安全） */
    private String initiatorOrgName;

    /** 发起人岗位（6.4.3） */
    private String initiatorPosition;

    private String businessType;

    /** 业务表单数据（JSON 字符串） */
    private String businessData;

    /** 当前活动节点 key（BPMN 活动 id） */
    private String currentNode;

    private String currentNodeName;

    /** RUNNING / COMPLETED / CANCELLED / REJECTED / DRAFT */
    private String status;

    /** 最近一次非发起审批动作（APPROVE/REJECT/DENY/TRANSFER/CANCEL），用于“驳回中/已拒绝”等结果视图 */
    private String lastAction;

    private String lastActionAt;

    /** 紧急度：0=普通，1=重要，2=紧急 */
    private Integer priority;

    private String submittedAt;

    private String completedAt;
}
