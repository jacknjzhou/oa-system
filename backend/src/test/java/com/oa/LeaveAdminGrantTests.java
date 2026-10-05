package com.oa;

import com.oa.controller.LeaveController;
import com.oa.dto.LeaveAdminGrantRequest;
import com.oa.entity.LeaveType;
import com.oa.entity.Organization;
import com.oa.entity.User;
import com.oa.enums.UserStatus;
import com.oa.repository.LeaveTransactionRepository;
import com.oa.repository.LeaveTypeRepository;
import com.oa.repository.OrganizationRepository;
import com.oa.repository.UserRepository;
import com.oa.service.LeaveService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer.MethodName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 授予/调整余额（HD-07）：SET_QUOTA/SET_REMAINING + 在职校验 + 权限码。 */
@SpringBootTest
@TestMethodOrder(MethodName.class)
class LeaveAdminGrantTests {

    @Autowired
    LeaveController leaveController;

    @Autowired
    LeaveService leaveService;

    @Autowired
    LeaveTypeRepository leaveTypeRepository;

    @Autowired
    LeaveTransactionRepository leaveTransactionRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    OrganizationRepository organizationRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 共享 H2 库纪律：本类把 employee 的 ANNUAL 额度改小过（3/5），
     * 类结束后拉回充足基线，避免后续测试类（LeaveProcessTests 按绝对值算可用额度）断粮。
     */
    @AfterAll
    static void restoreBaseline(@Autowired LeaveService leaveService, @Autowired UserRepository users) {
        Long emp = users.findByUsername("employee").orElseThrow().getId();
        Long admin = users.findByUsername("admin").orElseThrow().getId();
        leaveService.setQuota(emp, "ANNUAL", new BigDecimal("30"), "测试基线恢复", admin);
    }

    private void loginAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(username, null, List.of()));
    }

    private Long employeeId() {
        return userRepository.findByUsername("employee").orElseThrow().getId();
    }

    private Long adminId() {
        return userRepository.findByUsername("admin").orElseThrow().getId();
    }

    private LeaveType annual() {
        return leaveTypeRepository.findByCode("ANNUAL").orElseThrow();
    }

    @Test
    @Order(1)
    void set_quota_writes_quota_and_quota_txn() {
        Long emp = employeeId();
        LeaveType annual = annual();
        BigDecimal oldQuota = new BigDecimal(
                String.valueOf(leaveService.balance(emp, "ANNUAL").get("quota")));

        Map<String, Object> b = leaveService.setQuota(emp, "ANNUAL",
                new BigDecimal("15"), "年中追加", adminId());

        assertEquals(0, new BigDecimal("15").compareTo((BigDecimal) b.get("quota")));
        var txn = leaveTransactionRepository
                .findLog(emp, annual.getId(), "QUOTA", null, PageRequest.of(0, 1)).getContent().get(0);
        assertEquals("QUOTA", txn.getTxnType());
        assertEquals(adminId(), txn.getOperatorId(), "操作人=管理员");
        assertEquals(0, new BigDecimal("15").subtract(oldQuota).compareTo(txn.getDelta()),
                "流水 delta = 新额度 − 旧额度");
        assertEquals("年中追加", txn.getRemark());
        assertNull(txn.getInstanceId(), "管理操作无关联实例");
    }

    @Test
    @Order(2)
    void set_remaining_backs_out_quota() {
        Long emp = employeeId();
        BigDecimal usedBefore = new BigDecimal(
                String.valueOf(leaveService.balance(emp, "ANNUAL").get("used")));

        Map<String, Object> b = leaveService.setRemaining(emp, "ANNUAL",
                BigDecimal.valueOf(3), "修正", adminId());

        assertEquals(0, BigDecimal.valueOf(3).compareTo((BigDecimal) b.get("available")),
                "设剩余 3 → available=3");
        assertEquals(0, usedBefore.compareTo((BigDecimal) b.get("used")), "used 不动");
        var txn = leaveTransactionRepository
                .findLog(emp, annual().getId(), "ADJUST", null, PageRequest.of(0, 1)).getContent().get(0);
        assertEquals("ADJUST", txn.getTxnType());
    }

    @Test
    @Order(3)
    void set_remaining_rejected_for_unlimited() {
        // 分支 5a：不限额类型无剩余时长概念
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> leaveService.setRemaining(employeeId(), "SICK", BigDecimal.ONE, "x", adminId()));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("不限额"));
    }

    @Test
    @Order(4)
    void set_quota_below_in_use_rejected() {
        // 不变式：额度不能低于已用+冻结
        Long emp = employeeId();
        BigDecimal used = new BigDecimal(String.valueOf(leaveService.balance(emp, "ANNUAL").get("used")));
        BigDecimal frozen = new BigDecimal(String.valueOf(leaveService.balance(emp, "ANNUAL").get("frozen")));
        BigDecimal inUse = used.add(frozen);
        if (inUse.signum() == 0) {
            // 无占用时设 0 以下不可达，改测负数分支
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> leaveService.setQuota(emp, "ANNUAL", BigDecimal.valueOf(-1), "x", adminId()));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            return;
        }
        final BigDecimal target = inUse.subtract(new BigDecimal("0.5"));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> leaveService.setQuota(emp, "ANNUAL", target, "x", adminId()));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("不能低于"));
    }

    @Test
    @Order(5)
    void grant_to_inactive_user_rejected() {
        User inactive = new User();
        inactive.setUsername("temp_inactive_" + System.nanoTime());
        inactive.setPasswordHash("$2b$10$dummy");
        inactive.setRealName("离职测试");
        inactive.setStatus(UserStatus.INACTIVE);
        Organization tech = organizationRepository.findByOrgCode("TECH").orElse(null);
        if (tech != null) {
            inactive.setOrg(tech); // 共享库纪律：演示断言“所有用户都在技术部”
        }
        final Long inactiveId = userRepository.save(inactive).getId();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> leaveService.setQuota(inactiveId, "ANNUAL", BigDecimal.ONE, "x", adminId()));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("非在职"));
    }

    @Test
    @Order(6)
    void admin_grant_endpoint_permission_and_dispatch() {
        // 无权限（employee，普通组无 leave:manage）→ 403
        loginAs("employee");
        ResponseStatusException forbidden = assertThrows(ResponseStatusException.class,
                () -> leaveController.adminGrant(new LeaveAdminGrantRequest(
                        employeeId(), "ANNUAL", "SET_REMAINING", BigDecimal.valueOf(5), "越权")));
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());

        // 管理员 → 成功（SYSTEM_ADMIN 组含全部权限）
        loginAs("admin");
        var res = leaveController.adminGrant(new LeaveAdminGrantRequest(
                employeeId(), "ANNUAL", "SET_REMAINING", BigDecimal.valueOf(5), "管理员修正"));
        assertEquals(200, res.getCode());
        assertEquals(0, BigDecimal.valueOf(5).compareTo((BigDecimal) res.getData().get("available")));

        // 非法 action → 方法级校验拒绝（HTTP 下由全局异常处理器转 400）
        Exception bad = assertThrows(Exception.class,
                () -> leaveController.adminGrant(new LeaveAdminGrantRequest(
                        employeeId(), "ANNUAL", "HACK", BigDecimal.ONE, null)));
        String msg = String.valueOf(bad.getMessage());
        assertTrue(msg.contains("action") || msg.contains("SET_QUOTA"), "应拒绝非法 action: " + msg);
    }
}
