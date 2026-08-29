package com.oa.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.oa.enums.EnableStatus;
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
@ToString(callSuper = true, exclude = "parent")
@Entity
@Table(name = "organization")
public class Organization extends BaseEntity {

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "parent_id")
    private Organization parent;

    @Column(name = "org_code", nullable = false, unique = true, length = 64)
    private String orgCode;

    @Column(name = "org_name", nullable = false, length = 128)
    private String orgName;

    @Column(name = "org_type", length = 32)
    private String orgType;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "path", length = 512)
    private String path;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "status", nullable = false)
    private EnableStatus status;
}
