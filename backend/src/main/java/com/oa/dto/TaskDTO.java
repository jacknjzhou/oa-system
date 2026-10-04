package com.oa.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审批任务视图（源自 Flowable 任务表，映射为前端契约结构）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskDTO {

    /** Flowable 任务 ID（字符串） */
    private String id;

    private Long instanceId;

    private String title;

    private String defKey;

    private String defName;

    /** 任务定义 key（即 BPMN 节点 id） */
    private String nodeKey;

    private String nodeName;

    /** 当前处理人用户名（未认领时为空） */
    private String assignee;

    /** 候选角色编码列表 */
    private java.util.List<String> candidateRoles;

    private String createTime;

    private String endTime;

    /** 紧急度：0=普通，1=重要，2=紧急 */
    private Integer priority;

    private Long initiatorId;

    private String initiatorName;

    /** PENDING / COMPLETED / REJECTED */
    private String status;

    private String comment;
}
