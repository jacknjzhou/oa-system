package com.oa.service;

import com.oa.dto.LoginRequest;
import com.oa.dto.LoginResponse;
import com.oa.entity.RefreshToken;
import com.oa.entity.User;
import com.oa.enums.UserStatus;
import com.oa.repository.RefreshTokenRepository;
import com.oa.repository.UserRepository;
import com.oa.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "账号已被禁用或锁定");
        }
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // 每次登录开启新的 token 家族
        return issueTokens(user, UUID.randomUUID().toString());
    }

    /**
     * Refresh 轮换 + 重用检测：
     * 已作废 token 再现 → 吊销整个家族（防窃听重放）；有效 token → 作废旧的、签发新的。
     */
    @Transactional
    public LoginResponse refresh(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "refreshToken无效或已过期");
        }
        RefreshToken stored = refreshTokenRepository.findByTokenHash(sha256Hex(refreshToken))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "refreshToken无效或已过期"));

        if (!stored.isActive()) {
            // 已作废的 token 被重用：吊销同家族全部 token
            refreshTokenRepository.deactivateFamily(stored.getFamilyId());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "检测到refreshToken重用，本会话已全部吊销");
        }
        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "refreshToken无效或已过期");
        }

        stored.setActive(false);
        refreshTokenRepository.save(stored);
        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在"));
        return issueTokens(user, stored.getFamilyId());
    }

    public void logout(String accessToken) {
        jwtTokenProvider.blacklist(accessToken);
    }

    @Transactional(readOnly = true)
    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未认证");
        }
        Object principal = auth.getPrincipal();
        if (!(principal instanceof String username)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未认证");
        }
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在"));
    }

    private LoginResponse issueTokens(User user, String familyId) {
        var roleCodes = user.getRoleCodes();
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), roleCodes);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getUsername());

        // 持久化 refresh token 状态（哈希 + 家族），支撑轮换与重用检测
        RefreshToken record = new RefreshToken();
        record.setUserId(user.getId());
        record.setTokenHash(sha256Hex(refreshToken));
        record.setFamilyId(familyId);
        record.setActive(true);
        record.setExpiresAt(LocalDateTime.now().plus(jwtTokenProvider.getRefreshTokenExpiration(), ChronoUnit.MILLIS));
        refreshTokenRepository.save(record);

        LoginResponse response = new LoginResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setTokenType("Bearer");
        response.setExpiresIn(jwtTokenProvider.getAccessTokenExpiration() / 1000);

        Map<String, Object> info = new HashMap<>();
        info.put("id", user.getId());
        info.put("username", user.getUsername());
        info.put("realName", user.getRealName());
        info.put("email", user.getEmail());
        info.put("phone", user.getPhone());
        info.put("position", user.getPosition());
        info.put("roles", roleCodes);
        response.setUserInfo(info);
        return response;
    }

    private static String sha256Hex(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
