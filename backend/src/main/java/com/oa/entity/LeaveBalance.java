package com.oa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** 假期余额（AT-03 三账本之一：额度账）。balance = quota - used - frozen。 */
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

    private Integer quota = 0;
    private Integer used = 0;
    private Integer frozen = 0;

    public int available() {
        return (quota == null ? 0 : quota) - (used == null ? 0 : used) - (frozen == null ? 0 : frozen);
    }
}
