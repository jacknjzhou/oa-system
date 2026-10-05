package com.oa;

import com.oa.controller.LeaveController;
import com.oa.dto.InstanceDTO;
import com.oa.dto.ProcessStartRequest;
import com.oa.dto.TaskCompleteRequest;
import com.oa.dto.TaskDTO;
import com.oa.entity.ProcessInstance;
import com.oa.enums.ProcessInstanceStatus;
import com.oa.repository.ProcessInstanceRepository;
import com.oa.repository.UserRepository;
import com.oa.service.LeaveService;
import com.oa.service.ProcessService;
import com.oa.service.TaskService;
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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** 假期管理（HD-01/02/03/08）：部门/员工/余额/日志/导出 + 权限码。 */
@SpringBootTest
@TestMethodOrder(MethodName.class)
class LeaveManageTests {

    @Autowired
    LeaveService leaveService;

    @Autowired
    LeaveController leaveController;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ProcessService processService;

    @Autowired
    TaskService taskService;

    @Autowired
    ProcessInstanceRepository instanceRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(username, null, List.of()));
    }

    private Long employeeId() {
        return userRepository.findByUsername("employee").orElseThrow().getId();
    }

    private InstanceDTO startLeave(String title, int days) {
        loginAs("employee");
        ProcessStartRequest req = new ProcessStartRequest();
        req.setDefId(processService.listDefinitions(true).stream()
                .filter(d -> "leave".equals(d.getDefKey()))
                .findFirst().orElseThrow().getId());
        req.setTitle(title);
        req.setBusinessData("{\"leaveType\": \"年假\", \"days\": " + days + ", \"reason\": \"日志测试\"}");
        return processService.startInstance(req);
    }

    @Test
    @Order(1)
    void departments_with_member_counts() {
        List<Map<String, Object>> depts = leaveService.adminDepartments();
        Set<String> codes = depts.stream().map(d -> (String) d.get("orgCode")).collect(Collectors.toSet());
        assertTrue(codes.containsAll(Set.of("TECH", "MARKET", "OPERATIONS", "ADMIN_DEPT", "HR")),
                "应含 5 个部门: " + codes);

        var tech = depts.stream().filter(d -> "TECH".equals(d.get("orgCode"))).findFirst().orElseThrow();
        assertEquals(4, ((Number) tech.get("memberCount")).longValue(), "4 演示用户未移动（四裁决④）");
        var hr = depts.stream().filter(d -> "HR".equals(d.get("orgCode"))).findFirst().orElseThrow();
        assertEquals(0, ((Number) hr.get("memberCount")).longValue(), "新部门无成员");
    }

    @Test
    @Order(2)
    void employees_filter_by_org_and_keyword() {
        Long techId = ((Number) leaveService.adminDepartments().stream()
                .filter(d -> "TECH".equals(d.get("orgCode"))).findFirst().orElseThrow()
                .get("id")).longValue();

        List<Map<String, Object>> list = leaveService.adminEmployees(techId, "ANNUAL", null);
        assertEquals(4, list.size());
        assertTrue(list.stream().allMatch(e -> techId.equals(e.get("orgId"))));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> balances = (List<Map<String, Object>>) list.get(0).get("balances");
        assertEquals(1, balances.size(), "按类型筛选只含 ANNUAL");
        assertEquals("ANNUAL", balances.get(0).get("code"));

        // 关键字命中 username
        List<Map<String, Object>> byKw = leaveService.adminEmployees(null, null, "employee");
        assertEquals(1, byKw.size());
        assertEquals("employee", byKw.get(0).get("username"));
    }

    @Test
    @Order(3)
    void balances_row_shape_and_unlimited_null() {
        List<Map<String, Object>> rows = leaveService.adminBalances(employeeId());
        var sick = rows.stream().filter(r -> "SICK".equals(r.get("code"))).findFirst().orElseThrow();
        assertNull(sick.get("quota"), "不限额 → null");
        assertNull(sick.get("used"));
        assertNull(sick.get("available"));
        assertEquals("half_day", sick.get("unit"));
        assertEquals(false, sick.get("limited"));

        var annual = rows.stream().filter(r -> "ANNUAL".equals(r.get("code"))).findFirst().orElseThrow();
        assertNotNull(annual.get("quota"), "限额类型有数值");
        assertNotNull(annual.get("available"));
    }

    @Test
    @Order(4)
    void logs_include_operator_label_and_instance() {
        // 发起+批准一次请假 → 审批联动流水（带 instanceId）
        InstanceDTO instance = startLeave("联动-日志", 2);
        loginAs("manager");
        TaskDTO task = taskService.getMyTasks().stream()
                .filter(t -> instance.getId().equals(t.getInstanceId()))
                .findFirst().orElseThrow();
        TaskCompleteRequest complete = new TaskCompleteRequest();
        complete.setComment("同意");
        taskService.completeTask(task.getId(), complete);
        assertEquals(ProcessInstanceStatus.COMPLETED,
                instanceRepository.findById(instance.getId()).orElseThrow().getStatus());

        Map<String, Object> logs = leaveService.adminLogs(employeeId(), "ANNUAL", 0, 50);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) logs.get("items");
        assertFalse(items.isEmpty());
        Map<String, Object> latest = items.get(0);
        assertTrue(List.of("授予额度", "调整额度", "修改配额", "审批扣减", "冲销退回", "冻结", "释放")
                        .contains(latest.get("typeLabel")),
                "typeLabel 中文标签: " + latest.get("typeLabel"));
        assertNotNull(latest.get("operatorName"), "操作人姓名解析");
        assertNotNull(latest.get("instanceId"), "审批联动流水直存 instanceId（裁决③）");

        // 不按类型过滤（全类型）
        Map<String, Object> all = leaveService.adminLogs(employeeId(), null, 0, 50);
        assertTrue(((Number) all.get("total")).longValue() >= 1);
    }

    @Test
    @Order(5)
    void controller_admin_endpoints_require_leave_manage() {
        loginAs("employee");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> leaveController.adminDepartments());
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());

        loginAs("admin");
        var res = leaveController.adminDepartments();
        assertEquals(200, res.getCode());
        assertEquals(5, res.getData().size());
    }

    @Test
    @Order(6)
    void export_csv_shape() {
        String csv = leaveService.adminExport(null, null, null);
        assertTrue(csv.startsWith("\uFEFF"), "BOM 头");
        String[] lines = csv.split("\n");
        assertEquals("员工,账号,部门,假期类型,单位,额度,已用,冻结,剩余", lines[0].substring(1));
        assertTrue(csv.contains("系统管理员"), "含演示员工");
        assertTrue(csv.contains("不限额"), "不限额类型文案");
    }
}
