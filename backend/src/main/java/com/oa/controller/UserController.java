package com.oa.controller;

import com.oa.dto.ApiResponse;
import com.oa.entity.Role;
import com.oa.entity.User;
import com.oa.enums.EnableStatus;
import com.oa.enums.UserStatus;
import com.oa.repository.RoleRepository;
import com.oa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户与角色查询（转办选人、设计器候选角色下拉）。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @GetMapping("/users")
    public ApiResponse<List<Map<String, Object>>> listUsers() {
        List<User> users = userRepository.findAll().stream()
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .toList();
        return ApiResponse.success(users.stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("realName", u.getRealName());
            m.put("position", u.getPosition());
            m.put("email", u.getEmail());
            m.put("roles", u.getRoleCodes());
            return m;
        }).toList());
    }

    @GetMapping("/roles")
    public ApiResponse<List<Map<String, Object>>> listRoles() {
        List<Role> roles = roleRepository.findAll().stream()
                .filter(r -> r.getStatus() == EnableStatus.ENABLED)
                .toList();
        return ApiResponse.success(roles.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("code", r.getRoleCode());
            m.put("name", r.getRoleName());
            return m;
        }).toList());
    }
}
