package com.oa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 职级（一级~四级，可屏蔽）。 */
@Entity
@Table(name = "job_level")
@Getter
@Setter
public class JobLevel extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 8)
    private String code;

    @Column(name = "name", nullable = false, length = 32)
    private String name;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
}
