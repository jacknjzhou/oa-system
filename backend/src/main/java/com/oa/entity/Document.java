package com.oa.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.oa.enums.DocType;
import com.oa.enums.DocumentStatus;
import com.oa.enums.SecrecyLevel;
import com.oa.enums.Urgency;
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
@ToString(callSuper = true, exclude = {"author", "instance", "content"})
@Entity
@Table(name = "document")
public class Document extends BaseEntity {

    @Column(name = "doc_no", length = 64)
    private String docNo;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Lob
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "doc_type")
    private DocType docType;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "urgency")
    private Urgency urgency;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "secrecy_level")
    private SecrecyLevel secrecyLevel;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "author_id")
    private User author;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "instance_id")
    private ProcessInstance instance;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "status", nullable = false)
    private DocumentStatus status;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;
}
