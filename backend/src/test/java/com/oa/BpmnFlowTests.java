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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void highAmount_requiresCountersignThenGmApproval() {
        // 发起金额 20000（>1万）：经理审批 → 财务+经理会签（须全部通过）→ 总经理审批
        ProcessStartRequest req = new ProcessStartRequest();
        req.setDefId(templateId("reimbursement"));
        req.setTitle("大额报销测试");
        req.setBusinessData("{\"amount\": 20000, \"reason\": \"测试\"}");
        var instance = processService.startInstance(req);

        loginAs("manager");
        TaskCompleteRequest complete = new TaskCompleteRequest();
        complete.setComment("同意");
        TaskDTO task = taskService.getMyTasks().stream()
                .filter(t -> instance.getId().equals(t.getInstanceId()))
                .findFirst().orElseThrow();
        assertEquals("dept_manager", task.getNodeKey());
        taskService.completeTask(task.getId(), complete);

        ProcessInstance mid = instanceRepository.findById(instance.getId()).orElseThrow();
        assertEquals(ProcessInstanceStatus.RUNNING, mid.getStatus());
        assertEquals("countersign", mid.getCurrentNode());

        // 会签：manager 与 finance 各持一个元素，须都通过
        approveMyTask(instance.getId(), "manager", "countersign", complete);
        approveMyTask(instance.getId(), "finance", "countersign", complete);

        loginAs("admin");
        TaskDTO gmTask = taskService.getMyTasks().stream()
                .filter(t -> instance.getId().equals(t.getInstanceId()))
                .findFirst().orElseThrow();
        assertEquals("gm_manager", gmTask.getNodeKey());
        taskService.completeTask(gmTask.getId(), complete);

        ProcessInstance done = instanceRepository.findById(instance.getId()).orElseThrow();
        assertEquals(ProcessInstanceStatus.COMPLETED, done.getStatus());
    }

    @Test
    void countersign_staysRunningUntilAllElementsApproved() {
        // 会签一人通过后流程仍须等另一人（completionCondition = 全部完成）
        ProcessStartRequest req = new ProcessStartRequest();
        req.setDefId(templateId("reimbursement"));
        req.setTitle("会签等待测试");
        req.setBusinessData("{\"amount\": 20000, \"reason\": \"测试\"}");
        var instance = processService.startInstance(req);

        TaskCompleteRequest complete = new TaskCompleteRequest();
        complete.setComment("同意");
        loginAs("manager");
        TaskDTO dept = taskService.getMyTasks().stream()
                .filter(t -> instance.getId().equals(t.getInstanceId()))
                .findFirst().orElseThrow();
        taskService.completeTask(dept.getId(), complete);

        approveMyTask(instance.getId(), "finance", "countersign", complete);

        ProcessInstance mid = instanceRepository.findById(instance.getId()).orElseThrow();
        assertEquals(ProcessInstanceStatus.RUNNING, mid.getStatus());
        assertEquals("countersign", mid.getCurrentNode());
        // manager 的元素仍在待办中
        loginAs("manager");
        assertTrue(taskService.getMyTasks().stream()
                .anyMatch(t -> instance.getId().equals(t.getInstanceId())
                        && "countersign".equals(t.getNodeKey())));
        // 总经理节点尚未激活
        loginAs("admin");
        assertTrue(taskService.getMyTasks().stream()
                .noneMatch(t -> instance.getId().equals(t.getInstanceId())));
    }

    @Test
    void countersign_rejectByAnyElementTerminatesInstance() {
        // 会签中任一元素驳回 → 整单终止（含另一会签元素的任务一并消失）
        ProcessStartRequest req = new ProcessStartRequest();
        req.setDefId(templateId("reimbursement"));
        req.setTitle("会签驳回测试");
        req.setBusinessData("{\"amount\": 20000, \"reason\": \"测试\"}");
        var instance = processService.startInstance(req);

        TaskCompleteRequest complete = new TaskCompleteRequest();
        complete.setComment("同意");
        loginAs("manager");
        TaskDTO dept = taskService.getMyTasks().stream()
                .filter(t -> instance.getId().equals(t.getInstanceId()))
                .findFirst().orElseThrow();
        taskService.completeTask(dept.getId(), complete);

        loginAs("finance");
        TaskDTO myEl = taskService.getMyTasks().stream()
                .filter(t -> instance.getId().equals(t.getInstanceId()))
                .findFirst().orElseThrow();
        assertEquals("countersign", myEl.getNodeKey());
        TaskRejectRequest reject = new TaskRejectRequest();
        reject.setComment("发票不合规");
        taskService.rejectTask(myEl.getId(), reject);

        ProcessInstance saved = instanceRepository.findById(instance.getId()).orElseThrow();
        assertEquals(ProcessInstanceStatus.REJECTED, saved.getStatus());

        loginAs("manager");
        assertTrue(taskService.getMyTasks().stream()
                .noneMatch(t -> instance.getId().equals(t.getInstanceId())));
    }

    @Test
    void countersignProgressExposedInInstanceDetail() {
        // 实例详情内嵌会签进度：total=2，财务已通过、经理待办
        ProcessStartRequest req = new ProcessStartRequest();
        req.setDefId(templateId("reimbursement"));
        req.setTitle("会签进度测试");
        req.setBusinessData("{\"amount\": 20000, \"reason\": \"测试\"}");
        var instance = processService.startInstance(req);

        TaskCompleteRequest complete = new TaskCompleteRequest();
        complete.setComment("同意");
        loginAs("manager");
        TaskDTO dept = taskService.getMyTasks().stream()
                .filter(t -> instance.getId().equals(t.getInstanceId()))
                .findFirst().orElseThrow();
        taskService.completeTask(dept.getId(), complete);
        approveMyTask(instance.getId(), "finance", "countersign", complete);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) processService
                .getInstance(instance.getId()).get("countersigns");
        assertEquals(1, nodes.size());
        Map<String, Object> node = nodes.get(0);
        assertEquals("countersign", node.get("nodeKey"));
        assertEquals(2, node.get("total"));
        assertEquals(1, node.get("completed"));
        assertEquals(1, node.get("pending"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> elements = (List<Map<String, Object>>) node.get("elements");
        assertEquals(1, elements.stream().filter(x -> "FINANCE".equals(x.get("groupCode"))).count());
        assertEquals(1, elements.stream().filter(x -> "MANAGER".equals(x.get("groupCode"))).count());
        assertTrue(elements.stream().anyMatch(x -> "FINANCE".equals(x.get("groupCode"))
                && "COMPLETED".equals(x.get("status"))));
        assertTrue(elements.stream().anyMatch(x -> "MANAGER".equals(x.get("groupCode"))
                && "PENDING".equals(x.get("status"))));
    }

    @Test
    void draftInstance_createdWithoutEngine_andSubmittableOnlyByInitiator() {
        ProcessStartRequest req = new ProcessStartRequest();
        req.setDefId(templateId("reimbursement"));
        req.setTitle("草稿测试");
        req.setBusinessData("{\"amount\": 800, \"reason\": \"测试\"}");
        req.setDraft(true);

        var instance = processService.startInstance(req);
        assertEquals("DRAFT", instance.getStatus());
        ProcessInstance saved = instanceRepository.findById(instance.getId()).orElseThrow();
        assertEquals(ProcessInstanceStatus.DRAFT, saved.getStatus());
        assertNull(saved.getFlowableInstanceId());

        // 草稿不产生待办
        loginAs("manager");
        assertTrue(taskService.getMyTasks().stream()
                .noneMatch(t -> instance.getId().equals(t.getInstanceId())));

        // 非发起人不能提交
        ResponseStatusException forbidden = assertThrows(ResponseStatusException.class,
                () -> processService.submitInstance(instance.getId()));
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());

        // 发起人提交 → RUNNING 且产生首个任务
        loginAs("employee");
        var submitted = processService.submitInstance(instance.getId());
        assertEquals("RUNNING", submitted.getStatus());
        assertNotNull(instanceRepository.findById(instance.getId()).orElseThrow().getFlowableInstanceId());
        loginAs("manager");
        assertTrue(taskService.getMyTasks().stream()
                .anyMatch(t -> instance.getId().equals(t.getInstanceId())
                        && "dept_manager".equals(t.getNodeKey())));
    }

    @Test
    void submitInstance_rejectsNonDraft() {
        ProcessStartRequest req = new ProcessStartRequest();
        req.setDefId(templateId("reimbursement"));
        req.setTitle("重复提交测试");
        req.setBusinessData("{\"amount\": 800, \"reason\": \"测试\"}");
        var instance = processService.startInstance(req);
        ResponseStatusException badRequest = assertThrows(ResponseStatusException.class,
                () -> processService.submitInstance(instance.getId()));
        assertEquals(HttpStatus.BAD_REQUEST, badRequest.getStatusCode());
    }

    @Test
    void denyTask_terminatesInstanceWithDenyAction() {
        ProcessStartRequest req = new ProcessStartRequest();
        req.setDefId(templateId("reimbursement"));
        req.setTitle("拒绝测试");
        req.setBusinessData("{\"amount\": 500, \"reason\": \"测试\"}");
        var instance = processService.startInstance(req);

        loginAs("manager");
        TaskDTO task = taskService.getMyTasks().stream()
                .filter(t -> instance.getId().equals(t.getInstanceId()))
                .findFirst().orElseThrow();
        TaskRejectRequest deny = new TaskRejectRequest();
        deny.setComment("不符合报销规定");
        taskService.denyTask(task.getId(), deny);

        ProcessInstance saved = instanceRepository.findById(instance.getId()).orElseThrow();
        assertEquals(ProcessInstanceStatus.REJECTED, saved.getStatus());
        assertTrue(taskService.getMyTasks().stream()
                .noneMatch(t -> instance.getId().equals(t.getInstanceId())));

        // DTO 暴露最近审批动作
        assertEquals("DENY", processService.toInstanceDto(saved).getLastAction());
    }

    @Test
    void rejectToNode_keepsRunningWithLastActionReject() {
        // 高额报销：经理通过 → 会签中财务驳回到部门经理 → 实例仍 RUNNING，打回部门经理重审
        ProcessStartRequest req = new ProcessStartRequest();
        req.setDefId(templateId("reimbursement"));
        req.setTitle("节点驳回测试");
        req.setBusinessData("{\"amount\": 20000, \"reason\": \"测试\"}");
        var instance = processService.startInstance(req);

        TaskCompleteRequest complete = new TaskCompleteRequest();
        complete.setComment("同意");
        loginAs("manager");
        TaskDTO dept = taskService.getMyTasks().stream()
                .filter(t -> instance.getId().equals(t.getInstanceId()))
                .findFirst().orElseThrow();
        taskService.completeTask(dept.getId(), complete);

        loginAs("finance");
        TaskDTO myEl = taskService.getMyTasks().stream()
                .filter(t -> instance.getId().equals(t.getInstanceId()))
                .findFirst().orElseThrow();
        TaskRejectRequest reject = new TaskRejectRequest();
        reject.setComment("金额需复核");
        reject.setToNodeKey("dept_manager");
        taskService.rejectTask(myEl.getId(), reject);

        ProcessInstance saved = instanceRepository.findById(instance.getId()).orElseThrow();
        assertEquals(ProcessInstanceStatus.RUNNING, saved.getStatus());
        assertEquals("dept_manager", saved.getCurrentNode());
        assertEquals("REJECT", processService.toInstanceDto(saved).getLastAction());

        loginAs("manager");
        assertTrue(taskService.getMyTasks().stream()
                .anyMatch(t -> instance.getId().equals(t.getInstanceId())
                        && "dept_manager".equals(t.getNodeKey())));
    }

    @Test
    void instanceDto_exposesLastAction_inListAndDetail() {
        ProcessStartRequest req = new ProcessStartRequest();
        req.setDefId(templateId("reimbursement"));
        req.setTitle("动作轨迹测试");
        req.setBusinessData("{\"amount\": 500, \"reason\": \"测试\"}");
        var instance = processService.startInstance(req);

        loginAs("manager");
        TaskDTO task = taskService.getMyTasks().stream()
                .filter(t -> instance.getId().equals(t.getInstanceId()))
                .findFirst().orElseThrow();
        TaskCompleteRequest complete = new TaskCompleteRequest();
        complete.setComment("同意");
        taskService.completeTask(task.getId(), complete);

        loginAs("employee");
        var listed = processService.listMyInstances().stream()
                .filter(i -> instance.getId().equals(i.getId()))
                .findFirst().orElseThrow();
        assertEquals("APPROVE", listed.getLastAction());
        assertEquals("COMPLETED", listed.getStatus());
    }

    /** 以指定用户身份完成该实例指定节点的当前任务。 */
    private TaskDTO approveMyTask(Long instanceId, String username, String nodeKey,
                                   TaskCompleteRequest complete) {
        loginAs(username);
        TaskDTO task = taskService.getMyTasks().stream()
                .filter(t -> instanceId.equals(t.getInstanceId()) && nodeKey.equals(t.getNodeKey()))
                .findFirst().orElseThrow();
        taskService.completeTask(task.getId(), complete);
        return task;
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
