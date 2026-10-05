package com.oa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 假期余额（AT-03 三账本之一：额度账）。
 * available = quota - used - frozen（BigDecimal：支持 0.5 半天 / 2.5 小时）。
 */
@Getter
@Setter
@Entity
@Table(name = "leave_balance",
        uniqueConstraints = @UniqueConstraint(name = "uk_leave_balance_user_type", columnNames = {"user_id", "leave_type_id"}),
        indexes = @Index(name = "idx_leave_balance_user", columnList = "user_id"))
public class LeaveBalance extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "leave_type_id", nullable = false)
    private Long leaveTypeId;

    @Column(nullable = false)
    private BigDecimal quota = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal used = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal frozen = BigDecimal.ZERO;

    public BigDecimal available() {
        return nvl(quota).subtract(nvl(used)).subtract(nvl(frozen));
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
