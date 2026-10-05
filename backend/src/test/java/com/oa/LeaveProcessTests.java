package com.oa;

import com.oa.dto.InstanceDTO;
import com.oa.dto.ProcessStartRequest;
import com.oa.dto.TaskCompleteRequest;
import com.oa.dto.TaskDTO;
import com.oa.dto.TaskRejectRequest;
import com.oa.entity.ProcessInstance;
import com.oa.enums.ProcessInstanceStatus;
import com.oa.repository.ProcessInstanceRepository;
import com.oa.service.LeaveService;
import com.oa.service.ProcessService;
import com.oa.service.TaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer.MethodName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 请假 ↔ 假期账本联动（AT-04）：
 * 发起冻结额度 → 审批通过扣减 → 拒绝/撤回释放。
 * employee(3) 年假额度 10（LeaveBalanceSeeder）。
 */
@SpringBootTest
@TestMethodOrder(MethodName.class)
class LeaveProcessTests {

    @Autowired
    private ProcessService processService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private LeaveService leaveService;

    @Autowired
    private ProcessInstanceRepository instanceRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Long templateId(String defKey) {
        return processService.listDefinitions(true).stream()
                .filter(d -> defKey.equals(d.getDefKey()))
                .findFirst().orElseThrow().getId();
    }

    private void loginAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(username, null, List.of()));
    }

    private InstanceDTO startLeave(String title, int days) {
        return startLeave(title, "年假", String.valueOf(days));
    }

    private InstanceDTO startLeave(String title, String leaveType, String days) {
        loginAs("employee");
        ProcessStartRequest req = new ProcessStartRequest();
        req.setDefId(templateId("leave"));
        req.setTitle(title);
        req.setBusinessData("{\"leaveType\": \"" + leaveType + "\", \"days\": " + days + ", \"reason\": \"测试请假\"}");
        return processService.startInstance(req);
    }

    private void approve(Long instanceId) {
        loginAs("manager");
        TaskDTO task = taskService.getMyTasks().stream()
                .filter(t -> instanceId.equals(t.getInstanceId()))
                .findFirst().orElseThrow();
        TaskCompleteRequest complete = new TaskCompleteRequest();
        complete.setComment("同意");
        taskService.completeTask(task.getId(), complete);
    }

    private Map<String, Object> lastConsumeTxn(Long userId, String typeCode) {
        Map<String, Object> row = leaveService.ledger(userId, typeCode).get(0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> txns = (List<Map<String, Object>>) row.get("transactions");
        return txns.stream().filter(t -> "CONSUME".equals(t.get("txnType"))).findFirst().orElseThrow();
    }

    @Test
    void approved_leave_consumes_balance() {
        // 基线：已用 0（LeaveTests 的 employee 流水与本类共享库——用余额差值断言更稳）
        int availableBefore = ((Number) leaveService.balance(3L, "ANNUAL").get("available")).intValue();

        InstanceDTO instance = startLeave("联动-批准", 2);
        assertNotNull(instance.getId());
        // 发起后冻结：可用 -2
        int frozenAvailable = ((Number) leaveService.balance(3L, "ANNUAL").get("available")).intValue();
        assertEquals(availableBefore - 2, frozenAvailable, "发起后应冻结 2 天");

        loginAs("manager");
        TaskDTO task = taskService.getMyTasks().stream()
                .filter(t -> instance.getId().equals(t.getInstanceId()))
                .findFirst().orElseThrow();
        TaskCompleteRequest complete = new TaskCompleteRequest();
        complete.setComment("同意");
        taskService.completeTask(task.getId(), complete);

        ProcessInstance saved = instanceRepository.findById(instance.getId()).orElseThrow();
        assertEquals(ProcessInstanceStatus.COMPLETED, saved.getStatus());

        Map<String, Object> after = leaveService.balance(3L, "ANNUAL");
        int used = ((Number) after.get("used")).intValue();
        int available = ((Number) after.get("available")).intValue();
        assertEquals(availableBefore - 2, available, "批准后扣减 2 天");
        assertEquals(((Number) after.get("frozen")).intValue() + 0, 0, "冻结已清零");
        assertEquals(used >= 2, true);
    }

    @Test
    void rejected_leave_releases_freeze() {
        int availableBefore = ((Number) leaveService.balance(3L, "ANNUAL").get("available")).intValue();

        InstanceDTO instance = startLeave("联动-拒绝", 3);
        int frozenAvailable = ((Number) leaveService.balance(3L, "ANNUAL").get("available")).intValue();
        assertEquals(availableBefore - 3, frozenAvailable, "发起后应冻结 3 天");

        loginAs("manager");
        TaskDTO task = taskService.getMyTasks().stream()
                .filter(t -> instance.getId().equals(t.getInstanceId()))
                .findFirst().orElseThrow();
        TaskRejectRequest reject = new TaskRejectRequest();
        reject.setComment("不同意");
        taskService.rejectTask(task.getId(), reject);

        ProcessInstance saved = instanceRepository.findById(instance.getId()).orElseThrow();
        assertEquals(ProcessInstanceStatus.REJECTED, saved.getStatus());
        // 拒绝后释放：可用恢复
        int availableAfter = ((Number) leaveService.balance(3L, "ANNUAL").get("available")).intValue();
        assertEquals(availableBefore, availableAfter, "拒绝后应释放冻结");
    }

    @Test
    void insufficient_balance_blocks_start() {
        // 余额最多 10 天，申请 99 天 → 发起失败且无流程实例残留
        loginAs("employee");
        ProcessStartRequest req = new ProcessStartRequest();
        req.setDefId(templateId("leave"));
        req.setTitle("联动-超额");
        req.setBusinessData("{\"leaveType\": \"年假\", \"days\": 99, \"reason\": \"超额\"}");
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> processService.startInstance(req));
    }

    /** 事假 unit=hour 不限额：不换算、不动余额，流水 delta=-2 且 unit=hour。 */
    @Test
    void unit_follows_type_no_conversion() {
        Map<String, Object> before = leaveService.balance(3L, "PERSONAL");
        int usedBefore = ((Number) before.get("used")).intValue();

        InstanceDTO instance = startLeave("联动-事假小时", "事假", "2");
        approve(instance.getId());

        Map<String, Object> after = leaveService.balance(3L, "PERSONAL");
        assertEquals(usedBefore, ((Number) after.get("used")).intValue(), "不限额类型不动余额");

        Map<String, Object> txn = lastConsumeTxn(3L, "PERSONAL");
        assertEquals(0, new BigDecimal("-2").compareTo((BigDecimal) txn.get("delta")));
        assertEquals("hour", txn.get("unit"), "流水 unit 跟随类型，无跨单位换算");
    }

    /** 年假 1.5 天（天精度 2 位小数）：余额按 BigDecimal 精确扣减。 */
    @Test
    void fractional_days_decimal_precision() {
        BigDecimal availableBefore = (BigDecimal) leaveService.balance(3L, "ANNUAL").get("available");

        InstanceDTO instance = startLeave("联动-小数", "年假", "1.5");
        approve(instance.getId());

        BigDecimal availableAfter = (BigDecimal) leaveService.balance(3L, "ANNUAL").get("available");
        assertEquals(0, availableBefore.subtract(new BigDecimal("1.5")).compareTo(availableAfter),
                "可用 = " + availableBefore + " - 1.5 = " + availableAfter);

        Map<String, Object> txn = lastConsumeTxn(3L, "ANNUAL");
        assertEquals(0, new BigDecimal("-1.5").compareTo((BigDecimal) txn.get("delta")), "流水 delta 保留小数");
        assertEquals("day", txn.get("unit"));
    }

    /** 病假 unit=half_day：days=2 → 流水 delta=-2（2 个半天），unit=half_day。 */
    @Test
    void half_day_unit_propagated() {
        InstanceDTO instance = startLeave("联动-病假半天", "病假", "2");
        approve(instance.getId());

        Map<String, Object> txn = lastConsumeTxn(3L, "SICK");
        assertEquals(0, new BigDecimal("-2").compareTo((BigDecimal) txn.get("delta")));
        assertEquals("half_day", txn.get("unit"));
    }

    /** 审批联动流水的操作人=发起人（裁决③）：冻结/扣减流水 operatorId 非空。 */
    @Test
    void approval_txns_record_initiator_as_operator() {
        InstanceDTO instance = startLeave("联动-操作人", "年假", "1");
        approve(instance.getId());

        Map<String, Object> row = leaveService.ledger(3L, "ANNUAL").get(0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> txns = (List<Map<String, Object>>) row.get("transactions");
        Map<String, Object> latest = txns.get(0);
        assertNotNull(latest.get("operatorId"), "审批联动流水 operatorId=发起人");
        assertNotNull(latest.get("operatorName"));
        assertNotNull(latest.get("instanceId"), "instanceId 直存（裁决③）");
    }

    /** 请假模板表单选项与 leave_type 表同源（动态注入，非硬编码 6 项）。 */
    @Test
    void leave_template_options_synced_from_leave_types() {
        String formConfig = processService.listDefinitions(true).stream()
                .filter(d -> "leave".equals(d.getDefKey()))
                .findFirst().orElseThrow()
                .getFormConfig();
        assertNotNull(formConfig);
        assertTrue(formConfig.contains("\"年假\""), "选项含年假: " + formConfig);
        assertTrue(formConfig.contains("\"婚假\""), "选项含婚假（超出旧 6 项）: " + formConfig);
        assertTrue(formConfig.contains("\"丧假\""), "选项含丧假");
        assertTrue(formConfig.contains("\"例假\""), "选项含例假");
        assertTrue(formConfig.contains("\"请假时长\""), "days 标签改为请假时长");
    }
}
