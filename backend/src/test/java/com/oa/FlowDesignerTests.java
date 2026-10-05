package com.oa;

import com.oa.dto.DefinitionDTO;
import com.oa.dto.ProcessDefinitionRequest;
import com.oa.dto.ProcessStartRequest;
import com.oa.dto.TaskCompleteRequest;
import com.oa.dto.TaskDTO;
import com.oa.entity.ProcessDefinition;
import com.oa.entity.User;
import com.oa.enums.ProcessDefinitionStatus;
import com.oa.repository.ProcessDefinitionRepository;
import com.oa.repository.UserRepository;
import com.oa.service.ProcessService;
import com.oa.service.TaskService;
import org.flowable.engine.RepositoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 流程设计器（P2-2）后端能力测试：
 * 发起人主管解析（AssignSupervisorDelegate）+ 模板“流程未设计”双态。
 */
@SpringBootTest
class FlowDesignerTests {

    private static final String SUPERVISOR_BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                               xmlns:flowable="http://flowable.org/bpmn"
                               targetNamespace="http://oa.local/sup">
              <bpmn:process id="sup_test" name="主管审批测试" isExecutable="true">
                <bpmn:startEvent id="start" name="发起"/>
                <bpmn:sequenceFlow id="f1" sourceRef="start" targetRef="resolve_sup"/>
                <bpmn:serviceTask id="resolve_sup" name="解析主管"
                                   flowable:delegateExpression="${assignSupervisorDelegate}"/>
                <bpmn:sequenceFlow id="f2" sourceRef="resolve_sup" targetRef="sup_task"/>
                <bpmn:userTask id="sup_task" name="主管审批" flowable:candidateUsers="${supervisorUsername}"/>
                <bpmn:sequenceFlow id="f3" sourceRef="sup_task" targetRef="end"/>
                <bpmn:endEvent id="end" name="结束"/>
              </bpmn:process>
            </bpmn:definitions>
            """;

    @Autowired
    private ProcessService processService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private ProcessDefinitionRepository definitionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RepositoryService repositoryService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(username, null, List.of()));
    }

    private User user(String username) {
        return userRepository.findByUsername(username).orElseThrow();
    }

    private Long deploySupervisorFlow() {
        repositoryService.createDeployment()
                .name("sup_test")
                .key("sup_test")
                .addString("sup_test.bpmn20.xml", SUPERVISOR_BPMN)
                .deploy();
        ProcessDefinition def = new ProcessDefinition();
        def.setDefKey("sup_test");
        def.setName("主管审批测试");
        def.setFormConfig("{\"fields\":[]}");
        def.setBpmnXml(SUPERVISOR_BPMN);
        def.setStatus(ProcessDefinitionStatus.PUBLISHED);
        def.setVersion(1);
        return definitionRepository.save(def).getId();
    }

    @Test
    void supervisorTask_resolvesFromSupervisorId() {
        // employee 的主管 = manager
        User employee = user("employee");
        User manager = user("manager");
        employee.setSupervisorId(manager.getId());
        userRepository.save(employee);

        Long defId = deploySupervisorFlow();

        loginAs("employee");
        ProcessStartRequest req = new ProcessStartRequest();
        req.setDefId(defId);
        req.setTitle("主管审批测试单");
        req.setBusinessData("{}");
        var instance = processService.startInstance(req);
        assertThat(instance.getStatus()).isEqualTo("RUNNING");

        // 主管（manager）的待办里出现该任务
        loginAs("manager");
        List<TaskDTO> tasks = taskService.getMyTasks().stream()
                .filter(t -> "主管审批".equals(t.getNodeName()))
                .toList();
        assertThat(tasks).isNotEmpty();

        // 主管审批后流程完成
        loginAs("manager");
        var task = tasks.get(0);
        TaskCompleteRequest complete = new TaskCompleteRequest();
        complete.setComment("同意");
        taskService.completeTask(task.getId(), complete);
        loginAs("employee");
        var detail = processService.getInstance(instance.getId());
        assertThat(detail.get("instance")).isNotNull();
    }

    @Test
    void supervisorFallsBackToInitiatorWhenNotSet() {
        // employee 未设置主管（seeder 预置过，这里显式清除）→ 任务回退给发起人本人
        User employee = user("employee");
        employee.setSupervisorId(null);
        userRepository.save(employee);

        Long defId = deploySupervisorFlow();

        loginAs("employee");
        ProcessStartRequest req = new ProcessStartRequest();
        req.setDefId(defId);
        req.setTitle("无主管回退测试单");
        req.setBusinessData("{}");
        processService.startInstance(req);

        List<TaskDTO> tasks = taskService.getMyTasks().stream()
                .filter(t -> "主管审批".equals(t.getNodeName()))
                .toList();
        assertThat(tasks).isNotEmpty();
    }

    @Test
    void templateWithoutFlow_canPublishButNotStart() {
        loginAs("admin");
        ProcessDefinitionRequest req = new ProcessDefinitionRequest();
        req.setDefKey("no_flow");
        req.setName("无流程模板");
        req.setFormConfig("{\"fields\":[{\"key\":\"a\",\"label\":\"A\",\"type\":\"text\",\"required\":false}]}");
        // 不传 bpmnXml（流程未设计）
        DefinitionDTO created = processService.createDefinition(req);
        assertThat(created.getStatus()).isEqualTo("DRAFT");
        assertThat(created.isFlowReady()).isFalse();

        DefinitionDTO published = processService.publish(created.getId());
        assertThat(published.getStatus()).isEqualTo("PUBLISHED");
        assertThat(published.isFlowReady()).isFalse();

        ProcessStartRequest start = new ProcessStartRequest();
        start.setDefId(created.getId());
        start.setTitle("无流程发起");
        start.setBusinessData("{}");
        assertThatThrownBy(() -> processService.startInstance(start))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("尚未设计流程");
    }

    private static final String KEEP_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         id="DEF_keep" targetNamespace="http://oa.local">
              <process id="null_keep_test" isExecutable="true">
                <startEvent id="start"/>
                <sequenceFlow id="f1" sourceRef="start" targetRef="t1"/>
                <userTask id="t1" name="审批"/>
                <sequenceFlow id="f2" sourceRef="t1" targetRef="end"/>
                <endEvent id="end"/>
              </process>
            </definitions>
            """;

    @Test
    void update_nullBpmnXml_preservesExisting() {
        loginAs("admin");
        ProcessDefinitionRequest create = new ProcessDefinitionRequest();
        create.setDefKey("null_keep_test");
        create.setName("null 保留测试");
        create.setBpmnXml(KEEP_XML);
        DefinitionDTO created = processService.createDefinition(create);

        // 客户端漏传 bpmnXml（null）不得覆盖已有流程
        ProcessDefinitionRequest upd = new ProcessDefinitionRequest();
        upd.setName("仅改名");
        processService.updateDefinition(created.getId(), upd);
        String saved = definitionRepository.findById(created.getId()).orElseThrow().getBpmnXml();
        assertThat(saved).isNotBlank();
        assertThat(saved).contains("null_keep_test");
    }

    @Test
    void update_rejectsDefKeyChange_allowsMissingKey() {
        loginAs("admin");
        ProcessDefinitionRequest create = new ProcessDefinitionRequest();
        create.setDefKey("upd_key_test");
        create.setName("key 修改测试");
        DefinitionDTO created = processService.createDefinition(create);

        // key 不可修改：更新时携带不同 key 必须拒绝
        ProcessDefinitionRequest change = new ProcessDefinitionRequest();
        change.setDefKey("other_key");
        change.setName("改名");
        assertThatThrownBy(() -> processService.updateDefinition(created.getId(), change))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("不可修改");

        // 前端更新不带 key（历史行为）：保持原 key，更新成功
        ProcessDefinitionRequest noKey = new ProcessDefinitionRequest();
        noKey.setName("仅改名");
        DefinitionDTO updated = processService.updateDefinition(created.getId(), noKey);
        assertThat(updated.getDefKey()).isEqualTo("upd_key_test");
        assertThat(updated.getName()).isEqualTo("仅改名");
    }
}
