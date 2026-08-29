package com.oa.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.oa.enums.BusinessType;
import com.oa.enums.Priority;
import com.oa.enums.ProcessInstanceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = {"def", "initiator", "businessData"})
@Entity
@Table(name = "process_instance")
public class ProcessInstance extends BaseEntity {

    @Column(name = "instance_no", nullable = false, unique = true, length = 64)
    private String instanceNo;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "def_id")
    private ProcessDefinition def;

    @Column(name = "def_version")
    private Integer defVersion;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "initiator_id")
    private User initiator;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "business_type")
    private BusinessType businessType;

    @Column(name = "business_id", length = 64)
    private String businessId;

    @Lob
    @Column(name = "business_data")
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
}
