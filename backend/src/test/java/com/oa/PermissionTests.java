package com.oa;

import com.oa.controller.PermissionController;
import com.oa.dto.ApiResponse;
import com.oa.dto.LoginRequest;
import com.oa.dto.LoginResponse;
import com.oa.entity.User;
import com.oa.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 权组与权限（SY-03）：预置、登录携带权限码、授权生效。 */
@SpringBootTest
class PermissionTests {

    @Autowired
    private PermissionController permissionController;

    @Autowired
    private com.oa.controller.AuthController authController;

    @Autowired
    private UserRepository userRepository;

    private Map<String, Object> loginAs(String username, String password) {
        LoginRequest req = new LoginRequest();
        req.setUsername(username);
        req.setPassword(password);
        MockHttpServletRequest http = new MockHttpServletRequest();
        http.setRemoteAddr("127.0.0.1");
        ApiResponse<LoginResponse> res = authController.login(req, http);
        return res.getData().getUserInfo();
    }

    @Test
    void seeded_and_login_carries_codes() {
        ApiResponse<List<Map<String, Object>>> groups = permissionController.listGroups();
        assertEquals(2, groups.getData().size(), "预置 2 个权组");

        List<Map<String, Object>> perms = permissionController
                .listPermissions(null).getData();
        assertTrue(perms.size() >= 25, "内置权限 >= 25, 实际 " + perms.size());

        Map<String, Object> admin = loginAs("admin", "admin123");
        @SuppressWarnings("unchecked")
        List<String> adminPerms = (List<String>) admin.get("permissions");
        assertTrue(adminPerms.contains("system:settings"), "admin 应有 system:settings");
        assertTrue(adminPerms.contains("approval:manage"));

        Map<String, Object> employee = loginAs("employee", "employee123");
        @SuppressWarnings("unchecked")
        List<String> empPerms = (List<String>) employee.get("permissions");
        assertTrue(empPerms.contains("approval:start"), "普通组应有 approval:start");
        assertFalse(empPerms.contains("system:settings"), "普通组不应有 system:settings");
    }

    @Test
    void assign_permissions_and_members() {
        List<Map<String, Object>> groups = permissionController.listGroups().getData();
        Map<String, Object> normal = groups.stream()
                .filter(g -> "NORMAL".equals(g.get("code")))
                .findFirst().orElseThrow();
        Long gid = ((Number) normal.get("id")).longValue();

        // 从全部权限中挑 2 项授权
        List<Map<String, Object>> perms = permissionController.listPermissions(null).getData();
        Set<Long> two = perms.stream().limit(2).map(m -> ((Number) m.get("id")).longValue()).collect(java.util.stream.Collectors.toSet());

        PermissionController.IdsRequest req = new PermissionController.IdsRequest();
        req.setIds(two);
        ApiResponse<Map<String, Object>> res = permissionController.assignPermissions(gid, req);
        assertEquals(2, ((List<?>) res.getData().get("permissionIds")).size());

        // 授权生效到登录权限码
        User employee = userRepository.findByUsername("employee").orElseThrow();
        PermissionController.IdsRequest members = new PermissionController.IdsRequest();
        members.setIds(Set.of(employee.getId()));
        permissionController.assignMembers(gid, members);

        Map<String, Object> emp = loginAs("employee", "employee123");
        @SuppressWarnings("unchecked")
        List<String> codes = (List<String>) emp.get("permissions");
        List<String> expected = two.stream()
                .map(id -> perms.stream().filter(m -> ((Number) m.get("id")).longValue() == id)
                        .findFirst().orElseThrow().get("code").toString())
                .sorted().toList();
        assertEquals(expected, codes, "员工权限码 = NORMAL 组当前授权");

        // 还原：恢复普通组基础权限与全员（回归：初版误把全部权限授给普通组，
        // 共享 H2 库污染后续测试类——如假期管理的 403 断言）
        permissionController.assignMembers(gid,
                new PermissionController.IdsRequest() {
                    {
                        setIds(userRepository.findAll().stream().map(User::getId).collect(java.util.stream.Collectors.toSet()));
                    }
                });
        String[] basicCodes = {
                "approval:start", "approval:view", "attendance:clock", "attendance:view",
                "leave:apply", "leave:view", "document:create", "document:view",
                "seal:apply", "contract:apply", "expense:apply", "expense:view",
                "procurement:apply"
        };
        java.util.Map<String, Long> codeToId = new java.util.HashMap<>();
        for (Map<String, Object> p : perms) {
            codeToId.put((String) p.get("code"), ((Number) p.get("id")).longValue());
        }
        List<Long> basic = java.util.Arrays.stream(basicCodes).map(codeToId::get)
                .filter(java.util.Objects::nonNull).toList();
        assertEquals(13, basic.size(), "基础权限应恢复 13 项（与 PermissionSeeder 一致）");
        PermissionController.IdsRequest all = new PermissionController.IdsRequest();
        all.setIds(new java.util.LinkedHashSet<>(basic));
        permissionController.assignPermissions(gid, all);
    }
}
