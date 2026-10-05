package com.oa;

import com.oa.controller.UserController;
import com.oa.dto.ApiResponse;
import com.oa.entity.User;
import com.oa.enums.UserStatus;
import com.oa.repository.UserRepository;
import com.oa.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 员工管理（SY-01）：禁用/删除（回收站）/恢复。
 */
@SpringBootTest
class UserManagementTests {

    @Autowired
    private UserController userController;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        // 恢复演示账号状态，避免污染其它测试类（H2 共享库）
        userRepository.findByUsername("finance").ifPresent(u -> {
            u.setStatus(UserStatus.ACTIVE);
            u.setDeletedAt(null);
            userRepository.save(u);
        });
    }

    private void loginAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(username, null, List.of()));
    }

    @Test
    void disabled_user_cannot_login_and_is_hidden() {
        loginAs("admin");
        User finance = userRepository.findByUsername("finance").orElseThrow();

        userController.disableUser(finance.getId());
        assertEquals(UserStatus.INACTIVE, userRepository.findByUsername("finance").orElseThrow().getStatus());

        // 禁用后登录 401
        loginAs("finance");
        com.oa.dto.LoginRequest login = new com.oa.dto.LoginRequest();
        login.setUsername("finance");
        login.setPassword("finance123");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.login(login));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());

        // 常规列表不可见
        loginAs("admin");
        ApiResponse<List<Map<String, Object>>> list = userController.listUsers("active", null, null);
        assertTrue(list.getData().stream().noneMatch(m -> "finance".equals(m.get("username"))));
    }

    @Test
    void soft_delete_goes_to_recycle_bin_and_restores() {
        loginAs("admin");
        User finance = userRepository.findByUsername("finance").orElseThrow();

        userController.deleteUser(finance.getId());
        User deleted = userRepository.findByUsername("finance").orElseThrow();
        assertEquals(UserStatus.DELETED, deleted.getStatus());
        assertTrue(deleted.getDeletedAt() != null);

        // 常规列表不可见，回收站列表可见
        ApiResponse<List<Map<String, Object>>> normal = userController.listUsers("active", null, null);
        assertTrue(normal.getData().stream().noneMatch(m -> "finance".equals(m.get("username"))));
        ApiResponse<List<Map<String, Object>>> recycle = userController.listUsers("deleted", null, null);
        assertTrue(recycle.getData().stream().anyMatch(m -> "finance".equals(m.get("username"))
                && "DELETED".equals(m.get("status"))));

        // 恢复
        userController.restoreUser(finance.getId());
        User restored = userRepository.findByUsername("finance").orElseThrow();
        assertEquals(UserStatus.ACTIVE, restored.getStatus());
        assertEquals(null, restored.getDeletedAt());
    }

    @Test
    void cannot_delete_self() {
        loginAs("admin");
        User admin = userRepository.findByUsername("admin").orElseThrow();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userController.deleteUser(admin.getId()));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void enable_after_disable_restores_login() {
        loginAs("admin");
        User finance = userRepository.findByUsername("finance").orElseThrow();
        userController.disableUser(finance.getId());
        userController.enableUser(finance.getId());
        assertEquals(UserStatus.ACTIVE, userRepository.findByUsername("finance").orElseThrow().getStatus());
        assertNotEquals(UserStatus.DELETED, userRepository.findByUsername("finance").orElseThrow().getStatus());
    }
}
