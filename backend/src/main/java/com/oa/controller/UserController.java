package com.oa.controller;

import com.oa.dto.ApiResponse;
import com.oa.dto.UserUpdateRequest;
import com.oa.entity.Role;
import com.oa.entity.User;
import com.oa.enums.EnableStatus;
import com.oa.enums.UserStatus;
import com.oa.repository.RoleRepository;
import com.oa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
            m.put("supervisorId", u.getSupervisorId());
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

    /**
     * 更新用户资料（流程设计器“发起人主管”配置用）：
     * 姓名/职位/电话/主管/角色。
     */
    @PutMapping("/users/{id}")
    @Transactional
    public ApiResponse<Map<String, Object>> updateUser(@PathVariable Long id, @RequestBody UserUpdateRequest req) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        if (req.getRealName() != null) user.setRealName(req.getRealName());
        if (req.getPosition() != null) user.setPosition(req.getPosition());
        if (req.getPhone() != null) user.setPhone(req.getPhone());
        if (req.getEmail() != null) user.setEmail(req.getEmail());
        // 主管总是同步（前端设计器始终回传当前值；null = 清除）
        user.setSupervisorId(req.getSupervisorId());
        if (req.getRoleCodes() != null) {
            Set<Role> roles = req.getRoleCodes().stream()
                    .map(code -> roleRepository.findByRoleCode(code)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "角色不存在: " + code)))
                    .collect(Collectors.toSet());
            user.setRoles(roles);
        }
        User saved = userRepository.save(user);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", saved.getId());
        m.put("username", saved.getUsername());
        m.put("realName", saved.getRealName());
        m.put("supervisorId", saved.getSupervisorId());
        m.put("roles", saved.getRoleCodes());
        return ApiResponse.success(m);
    }
}
