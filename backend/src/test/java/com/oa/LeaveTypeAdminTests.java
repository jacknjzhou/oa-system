package com.oa;

import com.oa.controller.LeaveController;
import com.oa.dto.LeaveTypeRequest;
import com.oa.service.LeaveService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer.MethodName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 假期类型管理（HD-04~06）：CRUD + 引用保护 + 权限码 leave:manage。 */
@SpringBootTest
@TestMethodOrder(MethodName.class)
class LeaveTypeAdminTests {

    @Autowired
    LeaveController leaveController;

    @Autowired
    LeaveService leaveService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(username, null, List.of()));
    }

    @Test
    @Order(1)
    void create_update_and_delete_type() {
        loginAs("admin");
        var created = leaveController.createType(
                new LeaveTypeRequest("测试假", "T_LEAVE_1", "公司", "none", 0, "hour", 10, true));
        assertEquals(200, created.getCode());
        assertEquals("T_LEAVE_1", created.getData().get("code"));
        assertEquals("hour", created.getData().get("unit"));
        Long id = ((Number) created.getData().get("id")).longValue();

        // 管理列表可见（含单位/限次/权重）
        var list = leaveController.adminTypes();
        assertTrue(list.getData().stream().anyMatch(t -> "T_LEAVE_1".equals(t.get("code"))));

        // 更新（code 传 null = 不变）
        var updated = leaveController.updateType(id,
                new LeaveTypeRequest("测试假（改）", null, "公司", "fixed", 5, "hour", 10, true));
        assertEquals(200, updated.getCode());
        assertEquals("测试假（改）", updated.getData().get("name"));
        assertEquals("fixed", updated.getData().get("quotaType"));
        assertEquals(true, updated.getData().get("limited"));

        // 停用后管理列表仍可见、公开列表不可见
        var disabled = leaveController.updateType(id,
                new LeaveTypeRequest("测试假（停用）", null, "公司", "fixed", 5, "hour", 10, false));
        assertEquals(200, disabled.getCode());
        assertTrue(leaveController.adminTypes().getData().stream().anyMatch(t -> id.equals(((Number) t.get("id")).longValue())));
        assertFalse(leaveService.listTypes().stream().anyMatch(t -> id.equals(((Number) t.get("id")).longValue())),
                "停用类型不应出现在请假表单选项");

        // 无引用可删
        leaveController.deleteType(id);
        assertEquals(200, leaveController.adminTypes().getCode());
        assertFalse(leaveController.adminTypes().getData().stream().anyMatch(t -> id.equals(((Number) t.get("id")).longValue())));
    }

    @Test
    @Order(2)
    void create_duplicate_code_rejected() {
        loginAs("admin");
        assertThrows(ResponseStatusException.class,
                () -> leaveController.createType(new LeaveTypeRequest("年假2", "ANNUAL", "法定", "fixed", 99, "day", 99, true)));
    }

    @Test
    @Order(3)
    void delete_with_balance_ref_rejected() {
        loginAs("admin");
        var created = leaveController.createType(
                new LeaveTypeRequest("引用测试假", "T_LEAVE_2", "公司", "none", 0, "hour", 10, true));
        Long id = ((Number) created.getData().get("id")).longValue();
        // 授予一次 → 产生余额+流水引用
        leaveService.grant(1L, "T_LEAVE_2", BigDecimal.ONE, "引用测试", null, 1L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> leaveController.deleteType(id));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());

        // 改用停用（保留历史）
        var disabled = leaveController.updateType(id,
                new LeaveTypeRequest("引用测试假", null, "公司", "none", 0, "hour", 10, false));
        assertEquals(200, disabled.getCode());
        assertEquals(false, disabled.getData().get("enabled"));
    }

    @Test
    @Order(4)
    void create_by_unprivileged_user_forbidden() {
        loginAs("employee");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> leaveController.createType(
                        new LeaveTypeRequest("越权假", "T_LEAVE_3", "公司", "none", 0, "hour", 10, true)));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }
}
