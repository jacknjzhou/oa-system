package com.oa.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.oa.enums.ApprovalAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = {"instance", "operator"})
@Entity
@Table(name = "approval_record")
public class ApprovalRecord extends BaseEntity {

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "instance_id")
    private ProcessInstance instance;

    /** Flowable 任务 ID（发起/取消动作时可为空） */
    @Column(name = "flowable_task_id", length = 64)
    private String taskId;

    @Column(name = "node_key", length = 64)
    private String nodeKey;

    @Column(name = "node_name", length = 128)
    private String nodeName;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "action", nullable = false)
    private ApprovalAction action;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "operator_id")
    private User operator;

    @Column(name = "operator_name", length = 64)
    private String operatorName;

    @Column(name = "comment_text", length = 1000)
    private String comment;

    @Column(name = "from_node", length = 64)
    private String fromNode;

    @Column(name = "to_node", length = 64)
    private String toNode;
}
