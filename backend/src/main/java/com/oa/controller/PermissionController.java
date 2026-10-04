package com.oa.controller;

import com.oa.dto.ApiResponse;
import com.oa.entity.Permission;
import com.oa.entity.PermissionGroup;
import com.oa.entity.User;
import com.oa.service.PermissionService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 权组与权限管理（SY-03）。 */
@RestController
@RequestMapping("/api/permission-groups")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> listGroups() {
        return ApiResponse.success(permissionService.listGroups().stream().map(this::groupView).toList());
    }

    @PostMapping
    @Transactional
    public ApiResponse<Map<String, Object>> create(@RequestBody GroupRequest req) {
        return ApiResponse.success(groupView(permissionService.createGroup(req.getCode(), req.getName())));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ApiResponse<Void> delete(@PathVariable Long id) {
        permissionService.deleteGroup(id);
        return ApiResponse.success();
    }

    @PutMapping("/{id}/permissions")
    @Transactional
    public ApiResponse<Map<String, Object>> assignPermissions(@PathVariable Long id,
                                                              @RequestBody IdsRequest req) {
        return ApiResponse.success(groupView(permissionService.assignPermissions(id, req.getIds())));
    }

    @PutMapping("/{id}/members")
    @Transactional
    public ApiResponse<Map<String, Object>> assignMembers(@PathVariable Long id,
                                                          @RequestBody IdsRequest req) {
        return ApiResponse.success(groupView(permissionService.assignMembers(id, req.getIds())));
    }

    @GetMapping("/permissions")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> listPermissions(
            @RequestParam(required = false) String groupName) {
        return ApiResponse.success(permissionService.listPermissions(groupName).stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("code", p.getCode());
            m.put("name", p.getName());
            m.put("groupName", p.getGroupName());
            return m;
        }).toList());
    }

    private Map<String, Object> groupView(PermissionGroup g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", g.getId());
        m.put("code", g.getCode());
        m.put("name", g.getName());
        m.put("enabled", g.getEnabled());
        m.put("permissionIds", g.getPermissions().stream().map(Permission::getId).toList());
        m.put("members", g.getMembers().stream().map(u -> {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("id", u.getId());
            mm.put("username", u.getUsername());
            mm.put("realName", u.getRealName());
            return mm;
        }).toList());
        return m;
    }

    @Data
    public static class GroupRequest {
        private String code;
        private String name;
    }

    @Data
    public static class IdsRequest {
        private Set<Long> ids;
    }
}
