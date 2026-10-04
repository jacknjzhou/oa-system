package com.oa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 职称（可屏蔽）。 */
@Entity
@Table(name = "job_title")
@Getter
@Setter
public class JobTitle extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 16)
    private String code;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
}
