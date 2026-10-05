package com.oa;

import com.oa.entity.User;
import com.oa.repository.OrganizationRepository;
import com.oa.repository.UserRepository;
import com.oa.service.LeaveService;
import org.junit.jupiter.api.MethodOrderer.MethodName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 假期三账本 + 规格 HD-01~08（AT-03）：
 * 余额账 / 发生账 / 额度账，BigDecimal 单位感知（天/小时/半天），10 类种子，部门不移动演示用户。
 */
@SpringBootTest
@TestMethodOrder(MethodName.class)
class LeaveTests {

    @Autowired
    LeaveService leaveService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    OrganizationRepository organizationRepository;

    @Test
    @Order(1)
    void seeded_types_match_spec() {
        List<Map<String, Object>> types = leaveService.listTypes();
        var codes = types.stream().map(t -> (String) t.get("code")).toList();
        // 9 类规格类型 + 1 类演示类型（规格 5.2 + Ruling）
        assertTrue(codes.containsAll(List.of("ANNUAL", "PERSONAL", "SICK", "COMPENSATORY",
                "MARRIAGE", "MATERNITY", "PATERNAL", "BEREAVEMENT", "MENSTRUAL", "SPECIAL")),
                "应预置 10 类假期: " + codes);

        Map<String, Object> annual = byCode(types, "ANNUAL");
        assertEquals(true, annual.get("limited"));
        assertEquals("day", annual.get("unit"));
        assertEquals(99, ((Number) annual.get("weight")).intValue());

        Map<String, Object> personal = byCode(types, "PERSONAL");
        assertEquals(false, personal.get("limited"));
        assertEquals("hour", personal.get("unit"));

        Map<String, Object> sick = byCode(types, "SICK");
        assertEquals("half_day", sick.get("unit"), "病假单位应为半天");
    }

    @Test
    @Order(2)
    void demo_grant_annual() {
        // 演示用户已授年假 10（四裁决④：事假不限次，不再演示授予）
        Map<String, Object> balance = leaveService.balance(1L, "ANNUAL");
        assertEquals(10, ((Number) balance.get("quota")).intValue(), "admin 年假额度应为 10");
        assertEquals(10, ((Number) balance.get("available")).intValue());
    }

    @Test
    @Order(3)
    void consume_then_reverse() {
        int before = ((Number) leaveService.balance(3L, "ANNUAL").get("available")).intValue();
        leaveService.consume(3L, "ANNUAL", BigDecimal.valueOf(2), "LEAVE-TEST-1", "测试扣减", null);
        assertEquals(before - 2, ((Number) leaveService.balance(3L, "ANNUAL").get("available")).intValue(),
                "扣减后余额应 -2");

        leaveService.reverse(3L, "ANNUAL", BigDecimal.valueOf(2), "LEAVE-TEST-1", "测试冲销", null);
        assertEquals(before, ((Number) leaveService.balance(3L, "ANNUAL").get("available")).intValue(),
                "冲销后余额应恢复");

        List<Map<String, Object>> ledger = leaveService.ledger(3L, "ANNUAL");
        int count = ((List<?>) ledger.get(0).get("transactions")).size();
        assertTrue(count >= 2, "流水应含扣减+冲销: " + count);
    }

    @Test
    @Order(4)
    void overdraw_rejected() {
        int before = ((Number) leaveService.balance(3L, "ANNUAL").get("available")).intValue();
        assertThrows(Exception.class,
                () -> leaveService.consume(3L, "ANNUAL", BigDecimal.valueOf(9999), "LEAVE-TEST-2", "超额", null));
        // 余额未变（差值断言：与其他测试类共享库，不依赖绝对值）
        Map<String, Object> b = leaveService.balance(3L, "ANNUAL");
        assertEquals(before, ((Number) b.get("available")).intValue());
    }

    @Test
    @Order(5)
    void decimal_units_no_truncation() {
        // 年假（限次）：授予 2.5 → 扣 1.5 → 余额精确小数（不截断，HD-06/5.5）
        BigDecimal before = (BigDecimal) leaveService.balance(3L, "ANNUAL").get("available");
        leaveService.grant(3L, "ANNUAL", new BigDecimal("2.5"), "小数测试授予", null, 1L);
        Map<String, Object> b = leaveService.balance(3L, "ANNUAL");
        assertEquals(0, before.add(new BigDecimal("2.5")).compareTo((BigDecimal) b.get("available")),
                "授予 2.5 天后余额应 +2.5（小数不截断）");

        leaveService.consume(3L, "ANNUAL", new BigDecimal("1.5"), "T-DECIMAL-1", "小数扣减", null);
        b = leaveService.balance(3L, "ANNUAL");
        assertEquals(0, before.add(new BigDecimal("1.0")).compareTo((BigDecimal) b.get("available")),
                "扣 1.5 天后余额应 +1.0（小数不截断）");
    }

    @Test
    @Order(6)
    void departments_seeded_demo_users_stay_in_tech() {
        long orgCount = organizationRepository.count();
        assertTrue(orgCount >= 5, "应有 技术部 + 4 新增部门: " + orgCount);

        // 四裁决④：演示用户不移动部门（全在技术部），新部门为空
        List<User> users = userRepository.findAll();
        var techMembers = users.stream()
                .filter(u -> u.getOrg() != null && "TECH".equals(u.getOrg().getOrgCode()))
                .count();
        assertEquals(users.size(), techMembers, "演示用户应全部留在技术部");

        var marketMembers = users.stream()
                .filter(u -> u.getOrg() != null && "MARKET".equals(u.getOrg().getOrgCode()))
                .count();
        assertEquals(0, marketMembers, "市场部应无成员（不移动演示用户）");
    }

    private static Map<String, Object> byCode(List<Map<String, Object>> types, String code) {
        return types.stream().filter(t -> code.equals(t.get("code")))
                .findFirst().orElseThrow(() -> new AssertionError("缺少类型 " + code));
    }
}
