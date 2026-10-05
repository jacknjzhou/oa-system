package com.oa;

import com.oa.controller.OrganizationController;
import com.oa.controller.UserController;
import com.oa.dto.ApiResponse;
import com.oa.dto.UserCreateRequest;
import com.oa.dto.UserUpdateRequest;
import com.oa.entity.Organization;
import com.oa.entity.User;
import com.oa.enums.UserStatus;
import com.oa.repository.OrganizationRepository;
import com.oa.repository.UserRepository;
import com.oa.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 员工创建/调岗/筛选（6.4.4 / 6.4.5）。临时用户 t3_ 前缀，@AfterAll 软删清理。 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
class UserCreateTests {

    @Autowired private UserController userController;
    @Autowired private OrganizationController organizationController;
    @Autowired private UserRepository userRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private AuthService authService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @BeforeAll
    void loginAdmin() {
        loginAs("admin");
    }

    private void loginAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(username, null, List.of()));
    }

    private Long techId() {
        return organizationRepository.findByOrgCode("TECH").orElseThrow().getId();
    }

    private UserCreateRequest baseReq() {
        UserCreateRequest req = new UserCreateRequest();
        req.setUsername("t3_tmp" + System.nanoTime() % 1000000);
        req.setPassword("t3pass123");
        req.setRealName("T3临时");
        req.setOrgId(techId());
        req.setRoleCodes(List.of("EMPLOYEE"));
        return req;
    }

    private User createTmpUser() {
        loginAs("admin");
        UserCreateRequest req = baseReq();
        req.setUsername("t3_lisi_" + System.nanoTime() % 1000000);
        ApiResponse<Map<String, Object>> r = userController.createUser(req);
        assertEquals(200, r.getCode());
        return userRepository.findByUsername(req.getUsername()).orElseThrow();
    }

    private static void assertBadRequest(ResponseStatusException ex, String msgPart) {
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains(msgPart), "消息应含「" + msgPart + "」，实际: " + ex.getReason());
    }

    @Test
    void a_create_user_with_dept() {
        loginAs("admin");
        UserCreateRequest req = baseReq();
        req.setUsername("t3_zhangsan");
        req.setGender("男");
        ApiResponse<Map<String, Object>> r = userController.createUser(req);
        assertEquals(200, r.getCode());
        assertEquals("男", r.getData().get("gender"));
        var list = userController.listUsers("active", techId(), null).getData();
        assertTrue(list.stream().anyMatch(m -> "t3_zhangsan".equals(m.get("username"))));
        // 权限：普通员工不能建员工
        loginAs("employee");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userController.createUser(baseReq()));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void b_create_dup_username_400() {
        loginAs("admin");
        var req = baseReq();
        req.setUsername("t3_dup_user");
        userController.createUser(req);
        var dup = baseReq();
        dup.setUsername("t3_dup_user");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userController.createUser(dup));
        assertBadRequest(ex, "用户名已存在");
    }

    @Test
    void c_create_invalid_dict_400() {
        loginAs("admin");
        var r1 = baseReq();
        r1.setOrgId(999999L);
        assertBadRequest(assertThrows(ResponseStatusException.class, () -> userController.createUser(r1)), "部门不存在");
        var r2 = baseReq();
        r2.setJobTitleId(999999L);
        assertBadRequest(assertThrows(ResponseStatusException.class, () -> userController.createUser(r2)), "职称不存在");
        var r3 = baseReq();
        r3.setJobLevelId(999999L);
        assertBadRequest(assertThrows(ResponseStatusException.class, () -> userController.createUser(r3)), "职级不存在");
    }

    @Test
    void d_create_invalid_supervisor_400() {
        loginAs("admin");
        var fin = userRepository.findByUsername("finance").orElseThrow();
        fin.setStatus(UserStatus.INACTIVE);
        userRepository.save(fin);
        try {
            var req = baseReq();
            req.setSupervisorId(fin.getId());
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> userController.createUser(req));
            assertBadRequest(ex, "主管必须为在职员工");
            // 主管=自己（update 路径，验收 4 纵深）→ 400
            var u = createTmpUser();
            UserUpdateRequest up = new UserUpdateRequest();
            up.setSupervisorId(u.getId());
            assertBadRequest(assertThrows(ResponseStatusException.class,
                    () -> userController.updateUser(u.getId(), up)), "主管不能是自己");
        } finally {
            fin.setStatus(UserStatus.ACTIVE);
            userRepository.save(fin);
        }
    }

    @Test
    void e_update_transfer_dept_updates_tree_count() {
        loginAs("admin");
        var tech = organizationRepository.findByOrgCode("TECH").orElseThrow();
        var market = organizationRepository.findByOrgCode("MARKET").orElseThrow();
        long techBefore = userRepository.countByOrgIdAndStatus(tech.getId(), UserStatus.ACTIVE);
        long marketBefore = userRepository.countByOrgIdAndStatus(market.getId(), UserStatus.ACTIVE);
        var u = createTmpUser();
        // 调岗到 MARKET（supervisorId 显式回传现值——null 会清除）
        UserUpdateRequest up = new UserUpdateRequest();
        up.setOrgId(market.getId());
        up.setSupervisorId(u.getSupervisorId());
        ApiResponse<Map<String, Object>> r = userController.updateUser(u.getId(), up);
        assertEquals(200, r.getCode());
        assertEquals(market.getId(), ((Number) r.getData().get("orgId")).longValue());
        // 验收 2：前后 diff——创建(+1) + 调岗(-1) 后 TECH 净零；MARKET +1（共享库纪律：diff 断言）
        assertEquals(techBefore, userRepository.countByOrgIdAndStatus(tech.getId(), UserStatus.ACTIVE));
        assertEquals(marketBefore + 1, userRepository.countByOrgIdAndStatus(market.getId(), UserStatus.ACTIVE));
        // 部门树端点 memberCount 与仓库一致
        var marketNode = findNode(organizationController.tree().getData(), market.getId());
        assertNotNull(marketNode);
        assertEquals(((Number) marketNode.get("memberCount")).longValue(),
                userRepository.countByOrgIdAndStatus(market.getId(), UserStatus.ACTIVE));
        // 不存在的部门 → 400
        UserUpdateRequest bad = new UserUpdateRequest();
        bad.setOrgId(999999L);
        bad.setSupervisorId(u.getSupervisorId());
        assertBadRequest(assertThrows(ResponseStatusException.class,
                () -> userController.updateUser(u.getId(), bad)), "部门不存在");
    }

    @Test
    void f_list_status_filter_and_keyword() {
        loginAs("admin");
        var u = createTmpUser();
        assertNull(u.getGender(), "gender 未设 → null");
        userController.disableUser(u.getId());
        var active = userController.listUsers("active", null, u.getUsername()).getData();
        assertTrue(active.isEmpty(), "禁用后不在 active");
        var disabled = userController.listUsers("disabled", null, u.getUsername()).getData();
        assertEquals(1, disabled.size(), "在 disabled 组");
        var deleted = userController.listUsers("deleted", null, u.getUsername()).getData();
        assertTrue(deleted.isEmpty(), "未删除不在回收站");
        userController.enableUser(u.getId());
        assertFalse(userController.listUsers("active", null, u.getUsername()).getData().isEmpty());
    }

    @Test
    void g_disable_self_400() {
        loginAs("employee");
        var me = userRepository.findByUsername("employee").orElseThrow();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userController.disableUser(me.getId()));
        assertBadRequest(ex, "不能禁用当前登录用户");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> findNode(List<Map<String, Object>> nodes, long id) {
        for (Map<String, Object> n : nodes) {
            if (((Number) n.get("id")).longValue() == id) return n;
            var children = (List<Map<String, Object>>) n.get("children");
            if (children != null) {
                Map<String, Object> hit = findNode(children, id);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    @org.junit.jupiter.api.AfterAll
    void cleanup() {
        // 软删所有 t3_ 临时用户（永不硬删——回收站模式）
        loginAs("admin");
        userRepository.findAll().stream()
                .filter(u -> u.getUsername() != null && u.getUsername().startsWith("t3_")
                        && u.getStatus() != UserStatus.DELETED)
                .forEach(u -> {
                    u.setStatus(UserStatus.DELETED);
                    u.setDeletedAt(java.time.LocalDateTime.now());
                    userRepository.save(u);
                });
    }
}
