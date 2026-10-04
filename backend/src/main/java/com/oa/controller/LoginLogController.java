package com.oa.controller;

import com.oa.dto.ApiResponse;
import com.oa.entity.LoginLog;
import com.oa.service.LoginLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** 登录日志（SY-04）。 */
@RestController
@RequestMapping("/api/login-logs")
@RequiredArgsConstructor
public class LoginLogController {

    private final LoginLogService loginLogService;

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username) {
        size = Math.min(size, 100);
        Page<LoginLog> result = loginLogService.page(page, size, username);
        return ApiResponse.success(Map.of(
                "content", result.getContent().stream().map(this::view).toList(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages()
        ));
    }

    private Map<String, Object> view(LoginLog log) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", log.getId());
        m.put("username", log.getUsername());
        m.put("loginTime", log.getLoginTime());
        m.put("ip", log.getIp());
        m.put("userAgent", log.getUserAgent());
        m.put("success", log.getSuccess());
        m.put("failReason", log.getFailReason());
        return m;
    }
}
