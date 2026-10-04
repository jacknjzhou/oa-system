package com.oa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 打卡记录（AT-01：上班/下班 × 手动/扫码）。 */
@Getter
@Setter
@Entity
@Table(name = "check_record", indexes = {
        @Index(name = "idx_check_record_user_time", columnList = "user_id,check_time")
})
public class CheckRecord extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "check_time", nullable = false)
    private LocalDateTime checkTime;

    /** in 上班 / out 下班。 */
    @Column(nullable = false, length = 8)
    private String type;

    /** manual 手动 / scan 扫码。 */
    @Column(nullable = false, length = 16)
    private String source;

    @Column(length = 128)
    private String note;
}
