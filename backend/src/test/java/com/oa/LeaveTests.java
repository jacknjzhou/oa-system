package com.oa;

import com.oa.service.LeaveService;
import org.junit.jupiter.api.MethodOrderer.MethodName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 假期三账本（AT-03）：余额账 / 发生账 / 额度账。 */
@SpringBootTest
@TestMethodOrder(MethodName.class)
class LeaveTests {

    @Autowired
    LeaveService leaveService;

    @Test
    @Order(1)
    void seeded_leave_types_and_demo_balances() {
        List<Map<String, Object>> types = leaveService.listTypes();
        var codes = types.stream().map(t -> (String) t.get("code")).toList();
        assertTrue(codes.contains("ANNUAL"), "应预置年假: " + codes);
        assertTrue(codes.contains("SICK"));
        assertTrue(codes.contains("PERSONAL"));

        // 演示用户已授年假额度
        Map<String, Object> balance = leaveService.balance(1L, "ANNUAL");
        assertEquals(10, ((Number) balance.get("quota")).intValue(), "admin 年假额度应为 10");
        assertEquals(10, ((Number) balance.get("available")).intValue());
    }

    @Test
    @Order(2)
    void consume_then_reverse() {
        int before = ((Number) leaveService.balance(3L, "ANNUAL").get("available")).intValue();
        leaveService.consume(3L, "ANNUAL", 2, "LEAVE-TEST-1", "测试扣减");
        assertEquals(before - 2, ((Number) leaveService.balance(3L, "ANNUAL").get("available")).intValue(),
                "扣减后余额应 -2");

        leaveService.reverse(3L, "ANNUAL", 2, "LEAVE-TEST-1", "测试冲销");
        assertEquals(before, ((Number) leaveService.balance(3L, "ANNUAL").get("available")).intValue(),
                "冲销后余额应恢复");

        List<Map<String, Object>> ledger = leaveService.ledger(3L, "ANNUAL");
        int deltas = ((List<?>) ledger.get(0).get("transactions")).size();
        assertTrue(deltas >= 2, "流水应含扣减+冲销: " + deltas);
    }

    @Test
    @Order(3)
    void overdraw_rejected() {
        assertThrows(Exception.class, () -> leaveService.consume(3L, "ANNUAL", 9999, "LEAVE-TEST-2", "超额"));
        // 余额未变
        Map<String, Object> b = leaveService.balance(3L, "ANNUAL");
        assertEquals(10, ((Number) b.get("available")).intValue());
    }
}
