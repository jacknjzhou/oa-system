package com.oa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 假期类型（AT-03/HD-05）：
 * quota_type = fixed 定量 / accrual 累积 / none 不限；
 * unit = day 天 / hour 小时 / half_day 半天（单位间不换算，规格 5.5-⑤）。
 */
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

    /** 计量单位：day / hour / half_day（HD-05）。 */
    @Column(nullable = false, length = 8)
    private String unit = "day";

    private Integer weight = 100;

    private Boolean enabled = true;

    /** 是否限次/限额（决定三账本是否参与限额计算）。 */
    public boolean isLimited() {
        return !"none".equals(quotaType);
    }
}
