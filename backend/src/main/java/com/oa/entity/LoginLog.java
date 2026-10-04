package com.oa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 登录日志（成功 + 失败均记录，SY-04）。 */
@Entity
@Table(name = "login_log")
@Getter
@Setter
public class LoginLog extends BaseEntity {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", nullable = false, length = 64)
    private String username;

    @Column(name = "login_time", nullable = false)
    private LocalDateTime loginTime;

    @Column(name = "ip", nullable = false, length = 64)
    private String ip = "";

    @Column(name = "user_agent", nullable = false, length = 256)
    private String userAgent = "";

    @Column(name = "success", nullable = false)
    private Boolean success = true;

    @Column(name = "fail_reason", nullable = false, length = 128)
    private String failReason = "";
}
