package com.oa.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.oa.enums.ProcessDefinitionStatus;
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
@ToString(callSuper = true, exclude = {"creator", "formConfig", "bpmnXml"})
@Entity
@Table(name = "process_definition")
public class ProcessDefinition extends BaseEntity {

    @Column(name = "def_key", nullable = false, unique = false, length = 64)
    private String defKey;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "category", length = 64)
    private String category;

    @Column(name = "description", length = 500)
    private String description;

    @Lob
    @Column(name = "form_config", columnDefinition = "TEXT")
    private String formConfig;

    /** BPMN 2.0 标准 XML（与 Flowable 部署保持同步） */
    @Lob
    @Column(name = "bpmn_xml", nullable = false, columnDefinition = "TEXT")
    private String bpmnXml;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "status", nullable = false)
    private ProcessDefinitionStatus status;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "creator_id")
    private User creator;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;
}
