package com.oa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 假期流水（AT-03 三账本之二/三：发生账）。delta 正=增加，负=扣减。
 * HD-03 日志字段：operator_id（操作人）/ txn_type（类型）/ remark（备注）/
 * instance_id（直接关联流程实例，免反查）。
 */
@Getter
@Setter
@Entity
@Table(name = "leave_transaction", indexes = @Index(name = "idx_leave_txn_user_time", columnList = "user_id,created_at"))
public class LeaveTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "leave_type_id", nullable = false)
    private Long leaveTypeId;

    @Column(nullable = false)
    private BigDecimal delta = BigDecimal.ZERO;

    /** 操作人（管理端授予/调整；为空表示用户自己或系统）。 */
    @Column(name = "operator_id")
    private Long operatorId;

    /** 流水类型：GRANT / ADJUST / QUOTA / CONSUME / REVERSE / FREEZE / RELEASE。 */
    @Column(name = "txn_type", nullable = false, length = 16)
    private String txnType = "GRANT";

    /** 备注（调整原因等，HD-03）。 */
    @Column(name = "remark", length = 255)
    private String remark;

    /** 直接关联的流程实例 id（HD-01 跳转）。 */
    @Column(name = "instance_id")
    private Long instanceId;

    @Column(nullable = false, length = 128)
    private String reason;

    @Column(name = "ref_instance_no", length = 32)
    private String refInstanceNo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
