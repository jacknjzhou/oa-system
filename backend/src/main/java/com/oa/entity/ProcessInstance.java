package com.oa.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.oa.enums.BusinessType;
import com.oa.enums.Priority;
import com.oa.enums.ProcessInstanceStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Entity
@Table(name = "process_instance")
public class ProcessInstance extends BaseEntity {

    @Column(name = "instance_no", nullable = false, unique = true, length = 64)
    private String instanceNo;

    /** 关联 Flowable 流程实例 ID */
    @Column(name = "flowable_instance_id", length = 64, unique = true)
    private String flowableInstanceId;

    @JsonIgnore
    @ToString.Exclude
    @ManyToOne
    @JoinColumn(name = "def_id")
    private ProcessDefinition def;

    @Column(name = "def_version")
    private Integer defVersion;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @JsonIgnore
    @ToString.Exclude
    @ManyToOne
    @JoinColumn(name = "initiator_id")
    private User initiator;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "business_type")
    private BusinessType businessType;

    @Column(name = "business_id", length = 64)
    private String businessId;

    @Lob
    @ToString.Exclude
    @Column(name = "business_data", columnDefinition = "TEXT")
    private String businessData;

    @Column(name = "current_node", length = 64)
    private String currentNode;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "status", nullable = false)
    private ProcessInstanceStatus status;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "priority")
    private Priority priority;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** 抄送人名单（发起时选定）；流程经过 cc_notify 时由 CcDelegate 读取并通知 */
    @JsonIgnore
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "process_instance_cc_user",
            joinColumns = @JoinColumn(name = "process_instance_id"))
    @Column(name = "user_id")
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Set<Long> ccUserIds = new LinkedHashSet<>();
}
