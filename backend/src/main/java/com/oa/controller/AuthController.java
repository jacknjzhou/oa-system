package com.oa.controller;

import com.oa.dto.ApiResponse;
import com.oa.dto.LoginRequest;
import com.oa.dto.LoginResponse;
import com.oa.dto.RefreshTokenRequest;
import com.oa.entity.User;
import com.oa.service.AuthService;
import com.oa.service.LoginLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final LoginLogService loginLogService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest httpRequest) {
        try {
            ApiResponse<LoginResponse> res = ApiResponse.success(authService.login(request));
            var info = res.getData().getUserInfo();
            loginLogService.record(request.getUsername(),
                    info != null && info.get("id") instanceof Number n ? n.longValue() : null,
                    true, "",
                    httpRequest.getRemoteAddr(),
                    String.valueOf(httpRequest.getHeader("User-Agent")));
            return res;
        } catch (ResponseStatusException e) {
            loginLogService.record(request.getUsername(), null, false, e.getReason(),
                    httpRequest.getRemoteAddr(),
                    String.valueOf(httpRequest.getHeader("User-Agent")));
            throw e;
        }
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        String token = extractToken(request);
        authService.logout(token);
        return ApiResponse.success();
    }

    @GetMapping("/profile")
    public ApiResponse<User> profile() {
        return ApiResponse.success(authService.getCurrentUser());
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authService.refresh(request.getRefreshToken()));
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
