package com.oa;

import com.oa.dto.ProcessStartRequest;
import com.oa.dto.TaskCompleteRequest;
import com.oa.dto.TaskRejectRequest;
import com.oa.dto.TaskDTO;
import com.oa.entity.ProcessInstance;
import com.oa.enums.ProcessInstanceStatus;
import com.oa.repository.ProcessInstanceRepository;
import com.oa.service.ProcessService;
import com.oa.service.TaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BPMN 审批流全链路测试（H2 内存库 + Flowable 自动部署的报销流程）。
 */
@SpringBootTest
class BpmnFlowTests {

    @Autowired
    private ProcessService processService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private ProcessInstanceRepository instanceRepository;

    @BeforeEach
    void setUp() {
        loginAs("employee");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void lowAmount_reachesEndAfterManagerApproval() {
        // 发起金额 800（<=1万）：部门经理审批后直达结束
        ProcessStartRequest req = new ProcessStartRequest();
        req.setDefId(templateId("reimbursement"));
        req.setTitle("小额报销测试");
        req.setBusinessData("{\"amount\": 800, \"reason\": \"测试\"}");
        var instance = processService.startInstance(req);
        assertNotNull(instance.getId());
        assertEquals("RUNNING", instance.getStatus());

        loginAs("manager");
        List<TaskDTO> managerTasks = taskService.getMyTasks();
        TaskDTO task = managerTasks.stream()
                .filter(t -> instance.getId().equals(t.getInstanceId()))
                .findFirst().orElseThrow();
        assertEquals("dept_manager", task.getNodeKey());

        TaskCompleteRequest complete = new TaskCompleteRequest();
        complete.setComment("同意");
        taskService.completeTask(task.getId(), complete);

        ProcessInstance saved = instanceRepository.findById(instance.getId()).orElseThrow();
        assertEquals(ProcessInstanceStatus.COMPLETED, saved.getStatus());
        assertEquals("end", saved.getCurrentNode());
    }

    @Test
    void highAmount_requiresGmApproval() {
        // 发起金额 20000（>1万）：经理审批后仍需总经理审批
        ProcessStartRequest req = new ProcessStartRequest();
        req.setDefId(templateId("reimbursement"));
        req.setTitle("大额报销测试");
        req.setBusinessData("{\"amount\": 20000, \"reason\": \"测试\"}");
        var instance = processService.startInstance(req);

        loginAs("manager");
        TaskDTO task = taskService.getMyTasks().stream()
                .filter(t -> instance.getId().equals(t.getInstanceId()))
                .findFirst().orElseThrow();
        TaskCompleteRequest complete = new TaskCompleteRequest();
        complete.setComment("同意");
        taskService.completeTask(task.getId(), complete);

        ProcessInstance mid = instanceRepository.findById(instance.getId()).orElseThrow();
        assertEquals(ProcessInstanceStatus.RUNNING, mid.getStatus());
        assertEquals("gm_manager", mid.getCurrentNode());

        loginAs("admin");
        TaskDTO gmTask = taskService.getMyTasks().stream()
                .filter(t -> instance.getId().equals(t.getInstanceId()))
                .findFirst().orElseThrow();
        taskService.completeTask(gmTask.getId(), complete);

        ProcessInstance done = instanceRepository.findById(instance.getId()).orElseThrow();
        assertEquals(ProcessInstanceStatus.COMPLETED, done.getStatus());
    }

    @Test
    void reject_withoutTarget_marksInstanceRejected() {
        ProcessStartRequest req = new ProcessStartRequest();
        req.setDefId(templateId("reimbursement"));
        req.setTitle("驳回测试");
        req.setBusinessData("{\"amount\": 500, \"reason\": \"测试\"}");
        var instance = processService.startInstance(req);

        loginAs("manager");
        TaskDTO task = taskService.getMyTasks().stream()
                .filter(t -> instance.getId().equals(t.getInstanceId()))
                .findFirst().orElseThrow();
        TaskRejectRequest reject = new TaskRejectRequest();
        reject.setComment("材料不齐");
        taskService.rejectTask(task.getId(), reject);

        ProcessInstance saved = instanceRepository.findById(instance.getId()).orElseThrow();
        assertEquals(ProcessInstanceStatus.REJECTED, saved.getStatus());
        assertTrue(taskService.getMyTasks().stream()
                .noneMatch(t -> instance.getId().equals(t.getInstanceId())));
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
}
