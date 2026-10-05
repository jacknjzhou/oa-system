package com.oa.controller;

import com.oa.dto.ApiResponse;
import com.oa.dto.OrganizationRequest;
import com.oa.entity.Organization;
import com.oa.entity.User;
import com.oa.service.AuthService;
import com.oa.service.OrganizationService;
import com.oa.service.PermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * 部门管理（SY-02）：部门树（人数实时）+ CRUD。
 * 读端点任意登录用户（部门下拉/假期管理复用）；写端点需 hr:dept（R7）。
 */
@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;
    private final AuthService authService;
    private final PermissionService permissionService;

    /** 部门树（含 memberCount 与嵌套 children）。 */
    @GetMapping("/tree")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> tree() {
        return ApiResponse.success(organizationService.tree());
    }

    @PostMapping
    @Transactional
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody OrganizationRequest req) {
        requireDeptManage();
        return ApiResponse.success(view(organizationService.create(req)));
    }

    /** 部分更新（null 字段不动）；不加 @Valid——编辑弹窗总是全量回传，空白字段在 service 内手动判。 */
    @PutMapping("/{id}")
    @Transactional
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id,
                                                   @RequestBody OrganizationRequest req) {
        requireDeptManage();
        return ApiResponse.success(view(organizationService.update(id, req)));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ApiResponse<Void> delete(@PathVariable Long id) {
        requireDeptManage();
        organizationService.delete(id);
        return ApiResponse.success(null);
    }

    /** 权限校验：ADMIN 角色或 hr:dept 权限码（R7；HTTP 边界层强制）。 */
    private void requireDeptManage() {
        User current = authService.getCurrentUser();
        if (current == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        boolean admin = current.getRoleCodes().stream().anyMatch("ADMIN"::equals);
        boolean hasPerm = permissionService.permissionCodes(current.getId()).contains("hr:dept");
        if (!admin && !hasPerm) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无部门管理权限（hr:dept）");
        }
    }

    private Map<String, Object> view(Organization o) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("orgCode", o.getOrgCode());
        m.put("orgName", o.getOrgName());
        m.put("parentId", o.getParent() == null ? null : o.getParent().getId());
        m.put("sortOrder", o.getSortOrder());
        m.put("status", o.getStatus() == null ? null : o.getStatus().name());
        return m;
    }
}
