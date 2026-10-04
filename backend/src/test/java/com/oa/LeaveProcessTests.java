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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 请假 ↔ 假期账本联动（AT-04）：
 * 发起冻结额度 → 审批通过扣减 → 拒绝/撤回释放。
 * employee(3) 年假额度 10（LeaveBalanceSeeder）。
 */
@SpringBootTest
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
        loginAs("employee");
        ProcessStartRequest req = new ProcessStartRequest();
        req.setDefId(templateId("leave"));
        req.setTitle(title);
        req.setBusinessData("{\"leaveType\": \"年假\", \"days\": " + days + ", \"reason\": \"测试请假\"}");
        return processService.startInstance(req);
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
}
