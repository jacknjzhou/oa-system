package com.oa;

import com.oa.dto.LoginRequest;
import com.oa.dto.LoginResponse;
import com.oa.repository.RefreshTokenRepository;
import com.oa.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Refresh token 家族轮换测试：
 * 登录 → refresh 换新 → 旧 token 重用触发整家族吊销 → 最新 token 亦失效。
 */
@SpringBootTest
class RefreshTokenTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        loginAs("employee");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void login_rotatesOnRefresh_reuseRevokesFamily() {
        LoginResponse login = login("employee", "employee123");
        String firstRefresh = login.getRefreshToken();
        assertTrue(firstRefresh != null && !firstRefresh.isBlank());

        // 1) 正常 refresh：拿到新 refresh token（值轮换）
        LoginResponse rotated = authService.refresh(firstRefresh);
        assertNotEquals(firstRefresh, rotated.getRefreshToken());

        // 2) 重用旧的 refresh token → 401，且整家族被吊销
        ResponseStatusException reuse = assertThrows(ResponseStatusException.class,
                () -> authService.refresh(firstRefresh));
        assertEquals(401, reuse.getStatusCode().value());
        assertTrue(reuse.getReason().contains("重用"));

        // 3) 最新 token 随家族一起失效
        ResponseStatusException familyDead = assertThrows(ResponseStatusException.class,
                () -> authService.refresh(rotated.getRefreshToken()));
        assertEquals(401, familyDead.getStatusCode().value());

        // 4) 重新登录可恢复（新家族）
        LoginResponse relogin = login("employee", "employee123");
        assertNotEquals(firstRefresh, relogin.getRefreshToken());
        LoginResponse ok = authService.refresh(relogin.getRefreshToken());
        assertNotEquals(relogin.getRefreshToken(), ok.getRefreshToken());
    }

    @Test
    void refresh_unknownToken_returns401() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.refresh("not.a.jwt"));
        assertEquals(401, ex.getStatusCode().value());
    }

    private LoginResponse login(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return authService.login(request);
    }

    private void loginAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(username, null, List.of()));
    }
}
