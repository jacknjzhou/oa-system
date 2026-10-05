package com.oa.controller;

import com.oa.dto.ApiResponse;
import com.oa.dto.LeaveTypeRequest;
import com.oa.entity.User;
import com.oa.service.AuthService;
import com.oa.service.LeaveService;
import com.oa.service.PermissionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
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
    private final PermissionService permissionService;

    public record GrantRequest(
            @NotBlank String typeCode,
            @Positive @Digits(integer = 8, fraction = 2) BigDecimal amount,
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

    /** 额度授予（管理操作；演示环境对登录用户开放，生产应收敛到 leave:manage）。 */
    @PostMapping("/grant")
    public ApiResponse<Map<String, Object>> grant(@RequestBody GrantRequest req) {
        User current = authService.getCurrentUser();
        return ApiResponse.success(leaveService.grant(current.getId(), req.typeCode(),
                req.amount(), req.reason(), null, current.getId()));
    }

    // ==================== 管理端（权限码 leave:manage，Ruling ②） ====================

    /** 权限校验：ADMIN 角色或 leave:manage 权限码（四裁决②；HTTP 边界层强制）。 */
    private void requireLeaveManage() {
        User current = authService.getCurrentUser();
        if (current == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        boolean admin = current.getRoleCodes().stream().anyMatch("ADMIN"::equals);
        boolean hasPerm = permissionService.permissionCodes(current.getId()).contains("leave:manage");
        if (!admin && !hasPerm) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无假期管理权限（leave:manage）");
        }
    }

    // 直接调 controller 的测试没有 OpenEntityManagerInView → 需事务内访问懒加载 roles

    @GetMapping("/admin/types")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> adminTypes() {
        requireLeaveManage();
        return ApiResponse.success(leaveService.adminListTypes());
    }

    @PostMapping("/admin/types")
    @Transactional
    public ApiResponse<Map<String, Object>> createType(@Valid @RequestBody LeaveTypeRequest req) {
        requireLeaveManage();
        return ApiResponse.success(leaveService.createType(req));
    }

    @PutMapping("/admin/types/{id}")
    @Transactional
    public ApiResponse<Map<String, Object>> updateType(@PathVariable Long id, @Valid @RequestBody LeaveTypeRequest req) {
        requireLeaveManage();
        return ApiResponse.success(leaveService.updateType(id, req));
    }

    @DeleteMapping("/admin/types/{id}")
    @Transactional
    public ApiResponse<Void> deleteType(@PathVariable Long id) {
        requireLeaveManage();
        leaveService.deleteType(id);
        return ApiResponse.success();
    }
}
