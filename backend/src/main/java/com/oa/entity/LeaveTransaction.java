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

import java.time.LocalDateTime;

/** 假期流水（AT-03 三账本之二/三：发生账）。delta 正=增加，负=扣减。 */
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

    private Integer delta;

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
