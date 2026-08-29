package com.oa.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.oa.enums.TaskStatus;
import com.oa.enums.TaskType;
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

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = {"instance", "assignee", "delegateFrom"})
@Entity
@Table(name = "task")
public class Task extends BaseEntity {

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "instance_id")
    private ProcessInstance instance;

    @Column(name = "node_key", length = 64)
    private String nodeKey;

    @Column(name = "node_name", length = 128)
    private String nodeName;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "delegate_from_id")
    private User delegateFrom;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "task_type")
    private TaskType taskType;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "status", nullable = false)
    private TaskStatus status;

    @Column(name = "comment_text", length = 1000)
    private String comment;

    @Column(name = "due_at")
    private LocalDateTime dueAt;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
