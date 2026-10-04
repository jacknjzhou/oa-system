package com.oa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 假期类型（AT-03）：quota_type = fixed 定量 / accrual 累积 / none 不限。 */
@Getter
@Setter
@Entity
@Table(name = "leave_type")
public class LeaveType extends BaseEntity {

    @Column(nullable = false, length = 32, unique = true)
    private String code;

    @Column(nullable = false, length = 32)
    private String name;

    @Column(nullable = false, length = 16)
    private String category = "other";

    @Column(name = "quota_type", nullable = false, length = 8)
    private String quotaType = "none";

    @Column(name = "annual_quota")
    private Integer annualQuota = 0;

    private Integer weight = 100;

    private Boolean enabled = true;
}
