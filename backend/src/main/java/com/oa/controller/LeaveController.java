package com.oa.controller;

import com.oa.dto.ApiResponse;
import com.oa.entity.User;
import com.oa.service.AuthService;
import com.oa.service.LeaveService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 假期（AT-03）：类型 / 我的账本 / 额度授予。 */
@Validated
@RestController
@RequestMapping("/api/leave")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;
    private final AuthService authService;

    public record GrantRequest(
            @NotBlank String typeCode,
            @Min(1) @Max(365) int amount,
            String reason
    ) {}

    @GetMapping("/types")
    public ApiResponse<List<Map<String, Object>>> types() {
        return ApiResponse.success(leaveService.listTypes());
    }

    @GetMapping("/balance")
    public ApiResponse<Map<String, Object>> balance(@RequestParam String typeCode) {
        User current = authService.getCurrentUser();
        return ApiResponse.success(leaveService.balance(current.getId(), typeCode));
    }

    @GetMapping("/ledger")
    public ApiResponse<List<Map<String, Object>>> ledger(@RequestParam String typeCode) {
        User current = authService.getCurrentUser();
        return ApiResponse.success(leaveService.ledger(current.getId(), typeCode));
    }

    /** 额度授予（管理操作；演示环境对登录用户开放，生产应收敛到 ADMIN）。 */
    @PostMapping("/grant")
    public ApiResponse<Map<String, Object>> grant(@RequestBody GrantRequest req) {
        User current = authService.getCurrentUser();
        return ApiResponse.success(leaveService.grant(current.getId(), req.typeCode(), req.amount(), req.reason()));
    }
}
