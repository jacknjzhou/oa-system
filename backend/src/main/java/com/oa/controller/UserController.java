package com.oa.controller;

import com.oa.dto.ApiResponse;
import jakarta.validation.Valid;
import com.oa.dto.JobLevelRequest;
import com.oa.dto.JobTitleRequest;
import com.oa.dto.UserCreateRequest;
import com.oa.dto.UserUpdateRequest;
import com.oa.entity.JobLevel;
import com.oa.entity.JobTitle;
import com.oa.entity.Role;
import com.oa.entity.Organization;
import com.oa.entity.User;
import com.oa.repository.JobLevelRepository;
import com.oa.repository.OrganizationRepository;
import com.oa.repository.JobTitleRepository;
import com.oa.enums.EnableStatus;
import com.oa.enums.UserStatus;
import com.oa.repository.RoleRepository;
import com.oa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import com.oa.service.AuthService;
import com.oa.service.PermissionService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
    private final OrganizationRepository organizationRepository;
    private final JobTitleRepository jobTitleRepository;
    private final PermissionService permissionService;

    @GetMapping("/users")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> listUsers(
            @RequestParam(defaultValue = "active") String status,
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) String keyword) {
        String st = status == null ? "active" : status.trim().toLowerCase();
        List<User> users = userRepository.findAll().stream()
                .filter(u -> switch (st) {
                    case "all" -> true;
                    case "disabled" -> u.getStatus() == UserStatus.INACTIVE
                            || u.getStatus() == UserStatus.LOCKED;
                    case "deleted" -> u.getStatus() == UserStatus.DELETED;
                    default -> u.getStatus() == UserStatus.ACTIVE;
                })
                .filter(u -> orgId == null || (u.getOrg() != null && orgId.equals(u.getOrg().getId())))
                .filter(u -> keyword == null || keyword.isBlank()
                        ? true
                        : matches(u, keyword.trim()))
                .toList();
        return ApiResponse.success(users.stream().map(this::userView).toList());
    }

    /** 创建员工（6.4.4）：需 hr:user；字典字段（部门/主管/职级/职称）均校验存在性与在职状态。 */
    @PostMapping("/users")
    @Transactional
    public ApiResponse<Map<String, Object>> createUser(@Valid @RequestBody UserCreateRequest req) {
        requireUserManage();
        if (req.getUsername() == null || !req.getUsername().matches("[a-z0-9_.-]{2,32}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名格式: 2-32 位小写字母/数字/_ . -");
        }
        if (userRepository.findByUsername(req.getUsername()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名已存在: " + req.getUsername());
        }
        if (req.getPassword() == null || req.getPassword().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "初始密码至少 8 位");
        }
        User u = new User();
        u.setUsername(req.getUsername());
        u.setPasswordHash(passwordEncoder().encode(req.getPassword()));
        u.setRealName(req.getRealName());
        u.setGender(req.getGender());
        u.setPosition(req.getPosition());
        u.setPhone(req.getPhone());
        u.setEmail(req.getEmail());
        u.setStatus(UserStatus.ACTIVE);
        if (req.getOrgId() != null) {
            var org = organizationRepository.findById(req.getOrgId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "部门不存在"));
            u.setOrg(org);
        }
        if (req.getSupervisorId() != null) {
            u.setSupervisorId(requireActiveUser(req.getSupervisorId()).getId());
        }
        if (req.getJobLevelId() != null) {
            if (jobLevelRepository.findById(req.getJobLevelId()).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "职级不存在");
            }
            u.setJobLevelId(req.getJobLevelId());
        }
        if (req.getJobTitleId() != null) {
            if (jobTitleRepository.findById(req.getJobTitleId()).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "职称不存在");
            }
            u.setJobTitleId(req.getJobTitleId());
        }
        if (req.getRoleCodes() != null) {
            u.setRoles(req.getRoleCodes().stream()
                    .map(code -> roleRepository.findByRoleCode(code)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "角色不存在: " + code)))
                    .collect(Collectors.toSet()));
        }
        return ApiResponse.success(userView(userRepository.save(u)));
    }

    /** 权限校验：ADMIN 或 hr:user（员工创建；HTTP 边界层强制）。 */
    private void requireUserManage() {
        User current = authService.getCurrentUser();
        if (current == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        boolean admin = current.getRoleCodes().stream().anyMatch("ADMIN"::equals);
        boolean hasPerm = permissionService.permissionCodes(current.getId()).contains("hr:user");
        if (!admin && !hasPerm) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无员工管理权限（hr:user）");
        }
    }

    /** 主管/引用必须为在职员工（6.4.5-4）。 */
    private User requireActiveUser(Long id) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "主管不存在"));
        if (u.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "主管必须为在职员工");
        }
        return u;
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
        // 主管总是同步（前端设计器始终回传当前值；null = 清除）；非空必须为在职员工（6.4.5-4）
        if (req.getSupervisorId() == null) {
            user.setSupervisorId(null);
        } else {
            if (req.getSupervisorId().equals(user.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "主管不能是自己");
            }
            user.setSupervisorId(requireActiveUser(req.getSupervisorId()).getId());
        }
        if (req.getOrgId() != null) {
            var org = organizationRepository.findById(req.getOrgId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "部门不存在"));
            user.setOrg(org);
        } else if (Boolean.TRUE.equals(req.getClearOrg())) {
            user.setOrg(null);
        }
        if (req.getGender() != null) {
            user.setGender(req.getGender());
        }
        if (req.getJobTitleId() != null) {
            if (jobTitleRepository.findById(req.getJobTitleId()).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "职称不存在");
            }
            user.setJobTitleId(req.getJobTitleId());
        } else if (Boolean.TRUE.equals(req.getClearJobTitle())) {
            user.setJobTitleId(null);
        }
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
        // 统一 userView：含 orgId/orgName/gender/jobTitleId/lastLoginAt（前端编辑弹窗回显 + 测试断言）
        return ApiResponse.success(userView(saved));
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

    /** 职级改名/启停（SY-03）：code 不可变（引用保护）；名称空 400。 */
    @PutMapping("/job-levels/{id}")
    @Transactional
    public ApiResponse<Map<String, Object>> updateJobLevel(@PathVariable Long id,
                                                           @RequestBody JobLevelRequest req) {
        JobLevel l = jobLevelRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "职级不存在"));
        if (req.getName() == null || req.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "名称必填");
        }
        l.setName(req.getName().trim());
        if (req.getEnabled() != null) {
            l.setEnabled(req.getEnabled());
        }
        return ApiResponse.success(jobLevelView(jobLevelRepository.save(l)));
    }

    /** 删除职级（SY-03）：被未删除员工引用时 400（软数据，改绑优先）。 */
    @DeleteMapping("/job-levels/{id}")
    @Transactional
    public ApiResponse<Boolean> deleteJobLevel(@PathVariable Long id) {
        JobLevel l = jobLevelRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "职级不存在"));
        long refs = userRepository.countByJobLevelIdAndStatusNot(l.getId(), UserStatus.DELETED);
        if (refs > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该职级下有 " + refs + " 名员工（未删除），不可删除");
        }
        jobLevelRepository.delete(l);
        return ApiResponse.success(true);
    }

    /** 创建职称（SY-04）：code 缺省=名称。 */
    @PostMapping("/job-titles")
    @Transactional
    public ApiResponse<Map<String, Object>> createJobTitle(@RequestBody JobTitleRequest req) {
        if (req.getName() == null || req.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "名称必填");
        }
        String code = req.getCode() == null || req.getCode().isBlank()
                ? req.getName().trim() : req.getCode().trim().toUpperCase();
        if (jobTitleRepository.findByCode(code).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "职称代码已存在: " + code);
        }
        JobTitle t = new JobTitle();
        t.setCode(code);
        t.setName(req.getName().trim());
        t.setEnabled(req.getEnabled() == null || req.getEnabled());
        return ApiResponse.success(jobTitleView(jobTitleRepository.save(t)));
    }

    /** 职称改名/启停（SY-04）：code 不可变；名称空 400。 */
    @PutMapping("/job-titles/{id}")
    @Transactional
    public ApiResponse<Map<String, Object>> updateJobTitle(@PathVariable Long id,
                                                           @RequestBody JobLevelRequest req) {
        JobTitle t = jobTitleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "职称不存在"));
        if (req.getName() == null || req.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "名称必填");
        }
        t.setName(req.getName().trim());
        if (req.getEnabled() != null) {
            t.setEnabled(req.getEnabled());
        }
        return ApiResponse.success(jobTitleView(jobTitleRepository.save(t)));
    }

    /** 删除职称（SY-04）：被未删除员工引用时 400。 */
    @DeleteMapping("/job-titles/{id}")
    @Transactional
    public ApiResponse<Boolean> deleteJobTitle(@PathVariable Long id) {
        JobTitle t = jobTitleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "职称不存在"));
        long refs = userRepository.countByJobTitleIdAndStatusNot(t.getId(), UserStatus.DELETED);
        if (refs > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该职称下有 " + refs + " 名员工（未删除），不可删除");
        }
        jobTitleRepository.delete(t);
        return ApiResponse.success(true);
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
        User current = authService.getCurrentUser();
        if (current != null && current.getId().equals(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能禁用当前登录用户");
        }
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

    private BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
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
        m.put("position", u.getPosition());
        m.put("phone", u.getPhone());
        m.put("email", u.getEmail());
        m.put("gender", u.getGender());
        m.put("orgId", u.getOrg() == null ? null : u.getOrg().getId());
        m.put("orgName", u.getOrg() == null ? null : u.getOrg().getOrgName());
        m.put("supervisorId", u.getSupervisorId());
        m.put("roles", u.getRoleCodes());
        m.put("jobLevelId", u.getJobLevelId());
        m.put("jobTitleId", u.getJobTitleId());
        m.put("jobTitleName", u.getJobTitleId() == null ? null
                : jobTitleRepository.findById(u.getJobTitleId()).map(JobTitle::getName).orElse(null));
        m.put("lastLoginAt", u.getLastLoginAt());
        m.put("status", u.getStatus() != null ? u.getStatus().name() : null);
        m.put("deletedAt", u.getDeletedAt());
        return m;
    }

    private static boolean matches(User u, String kw) {
        return str(u.getUsername()).contains(kw) || str(u.getRealName()).contains(kw)
                || str(u.getPhone()).contains(kw) || str(u.getEmployeeNo()).contains(kw);
    }

    private static String str(String v) {
        return v == null ? "" : v;
    }
}
