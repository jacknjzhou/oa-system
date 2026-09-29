package com.oa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * Refresh token 家族轮换的持久化状态。
 * <p>
 * 仅存 token 的 SHA-256 哈希（不落明文）；同一登录会话（family）内轮换时
 * 作废旧记录、新记录沿用 familyId。检测到已作废 token 被重用时吊销整家族。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = "tokenHash")
@Entity
@Table(name = "refresh_token")
public class RefreshToken extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** refresh JWT 的 SHA-256 十六进制哈希 */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    /** 登录会话家族 ID（一次登录 = 一个 family，轮换不换 family） */
    @Column(name = "family_id", nullable = false, length = 36)
    private String familyId;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
