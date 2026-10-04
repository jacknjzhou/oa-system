package com.oa.controller;

import com.oa.dto.ApiResponse;
import com.oa.dto.JobLevelRequest;
import com.oa.dto.UserUpdateRequest;
import com.oa.entity.JobLevel;
import com.oa.entity.JobTitle;
import com.oa.entity.Role;
import com.oa.entity.User;
import com.oa.repository.JobLevelRepository;
import com.oa.repository.JobTitleRepository;
import com.oa.enums.EnableStatus;
import com.oa.enums.UserStatus;
import com.oa.repository.RoleRepository;
import com.oa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import com.oa.service.AuthService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final AuthService authService;
    private final JobLevelRepository jobLevelRepository;
    private final JobTitleRepository jobTitleRepository;

    @GetMapping("/users")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> listUsers(
            @RequestParam(defaultValue = "false") boolean includeDeleted) {
        List<User> users = userRepository.findAll().stream()
                .filter(u -> u.getStatus() == UserStatus.DELETED ? includeDeleted
                        : u.getStatus() == UserStatus.ACTIVE)
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
            m.put("jobLevelId", u.getJobLevelId());
            m.put("status", u.getStatus() != null ? u.getStatus().name() : null);
            m.put("deletedAt", u.getDeletedAt());
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
        if (Boolean.TRUE.equals(req.getClearJobLevel())) {
            user.setJobLevelId(null);
        } else if (req.getJobLevelId() != null) {
            if (jobLevelRepository.findById(req.getJobLevelId()).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "职级不存在");
            }
            user.setJobLevelId(req.getJobLevelId());
        }
        User saved = userRepository.save(user);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", saved.getId());
        m.put("username", saved.getUsername());
        m.put("realName", saved.getRealName());
        m.put("supervisorId", saved.getSupervisorId());
        m.put("roles", saved.getRoleCodes());
        m.put("jobLevelId", saved.getJobLevelId());
        return ApiResponse.success(m);
    }

    /** 职级列表（?all=1 含已屏蔽）。 */
    @GetMapping("/job-levels")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> listJobLevels(
            @RequestParam(defaultValue = "false") boolean all) {
        List<JobLevel> levels = all ? jobLevelRepository.findAll()
                : jobLevelRepository.findByEnabledTrueOrderByCodeAsc();
        return ApiResponse.success(levels.stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.getId());
            m.put("code", l.getCode());
            m.put("name", l.getName());
            m.put("enabled", l.getEnabled());
            return m;
        }).toList());
    }

    /** 职称列表（?all=1 含已屏蔽）。 */
    @GetMapping("/job-titles")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> listJobTitles(
            @RequestParam(defaultValue = "false") boolean all) {
        List<JobTitle> titles = all ? jobTitleRepository.findAll()
                : jobTitleRepository.findByEnabledTrueOrderByCodeAsc();
        return ApiResponse.success(titles.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("code", t.getCode());
            m.put("name", t.getName());
            m.put("enabled", t.getEnabled());
            return m;
        }).toList());
    }

    /** 职级新增/屏蔽。body: {code, name, enabled?} */
    @PostMapping("/job-levels")
    @Transactional
    public ApiResponse<Map<String, Object>> createJobLevel(@RequestBody JobLevelRequest req) {
        if (req.getCode() == null || req.getCode().isBlank() || req.getName() == null || req.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "编码与名称必填");
        }
        String code = req.getCode().trim().toUpperCase();
        if (jobLevelRepository.findByCode(code).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "编码已存在: " + code);
        }
        JobLevel l = new JobLevel();
        l.setCode(code);
        l.setName(req.getName().trim());
        l.setEnabled(req.getEnabled() == null || req.getEnabled());
        return ApiResponse.success(jobLevelView(jobLevelRepository.save(l)));
    }

    /** 职级屏蔽/启用。body: {enabled} */
    @PutMapping("/job-levels/{id}")
    @Transactional
    public ApiResponse<Map<String, Object>> updateJobLevel(@PathVariable Long id,
                                                           @RequestBody JobLevelRequest req) {
        JobLevel l = jobLevelRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "职级不存在"));
        if (req.getEnabled() != null) {
            l.setEnabled(req.getEnabled());
        }
        return ApiResponse.success(jobLevelView(jobLevelRepository.save(l)));
    }

    /** 职称屏蔽/启用。body: {enabled} */
    @PutMapping("/job-titles/{id}")
    @Transactional
    public ApiResponse<Map<String, Object>> updateJobTitle(@PathVariable Long id,
                                                           @RequestBody JobLevelRequest req) {
        JobTitle t = jobTitleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "职称不存在"));
        if (req.getEnabled() != null) {
            t.setEnabled(req.getEnabled());
        }
        return ApiResponse.success(jobTitleView(jobTitleRepository.save(t)));
    }

    private Map<String, Object> jobLevelView(JobLevel l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.getId());
        m.put("code", l.getCode());
        m.put("name", l.getName());
        m.put("enabled", l.getEnabled());
        return m;
    }

    private Map<String, Object> jobTitleView(JobTitle t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("code", t.getCode());
        m.put("name", t.getName());
        m.put("enabled", t.getEnabled());
        return m;
    }

    /** 禁用：不可登录、待办/通知不可见（重新启用即恢复）。 */
    @PostMapping("/users/{id}/disable")
    @Transactional
    public ApiResponse<Map<String, Object>> disableUser(@PathVariable Long id) {
        User user = requireUser(id);
        user.setStatus(UserStatus.INACTIVE);
        return ApiResponse.success(userView(userRepository.save(user)));
    }

    @PostMapping("/users/{id}/enable")
    @Transactional
    public ApiResponse<Map<String, Object>> enableUser(@PathVariable Long id) {
        User user = requireUser(id);
        if (user.getStatus() == UserStatus.DELETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "已删除用户请先恢复");
        }
        user.setStatus(UserStatus.ACTIVE);
        return ApiResponse.success(userView(userRepository.save(user)));
    }

    /** 删除（软删入回收站）：不可登录，可恢复。不能删除自己。 */
    @DeleteMapping("/users/{id}")
    @Transactional
    public ApiResponse<Map<String, Object>> deleteUser(@PathVariable Long id) {
        User current = authService.getCurrentUser();
        if (current.getId().equals(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能删除当前登录用户");
        }
        User user = requireUser(id);
        if (user.getStatus() == UserStatus.DELETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户已在回收站");
        }
        user.setStatus(UserStatus.DELETED);
        user.setDeletedAt(java.time.LocalDateTime.now());
        return ApiResponse.success(userView(userRepository.save(user)));
    }

    /** 从回收站恢复。 */
    @PostMapping("/users/{id}/restore")
    @Transactional
    public ApiResponse<Map<String, Object>> restoreUser(@PathVariable Long id) {
        User user = requireUser(id);
        if (user.getStatus() != UserStatus.DELETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅回收站中的用户可恢复");
        }
        user.setStatus(UserStatus.ACTIVE);
        user.setDeletedAt(null);
        return ApiResponse.success(userView(userRepository.save(user)));
    }

    private User requireUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
    }

    private Map<String, Object> userView(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("realName", u.getRealName());
        m.put("status", u.getStatus() != null ? u.getStatus().name() : null);
        m.put("deletedAt", u.getDeletedAt());
        return m;
    }
}
