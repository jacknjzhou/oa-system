package com.oa;

import com.oa.dto.DefinitionDTO;
import com.oa.dto.InstanceDTO;
import com.oa.dto.ProcessDefinitionRequest;
import com.oa.dto.ProcessStartRequest;
import com.oa.entity.ApprovalType;
import com.oa.repository.ApprovalTypeRepository;
import com.oa.service.ProcessService;
import org.flowable.engine.RepositoryService;
import org.flowable.task.api.Task;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 审批类型 ↔ 模板全覆盖（用户报告：16 类型只有 3 个模板、流程图空白）：
 * 15 个预置类型全部有 PUBLISHED 模板且 bpmnXml 含可渲染的 DI。
 */
@SpringBootTest
class SystemTemplateTests {

    @Autowired
    private ApprovalTypeRepository approvalTypeRepository;

    @Autowired
    private ProcessService processService;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private TaskService flowableTaskService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(username, null, List.of()));
    }

    @Test
    void everyPresetTypeHasPublishedTemplateWithRenderableDi() {
        List<String> presetCodes = List.of(
                "REIMBURSEMENT", "LEAVE", "TRAVEL", "PROCUREMENT", "OVERTIME", "OUTSIDE",
                "PUNCH_FIX", "SEAL_USE", "CAR_USE", "CONTRACT", "PAYMENT", "ADVANCE",
                "REGULARIZATION", "RECRUITMENT", "RESIGNATION", "DOCUMENT");
        for (String code : presetCodes) {
            ApprovalType type = approvalTypeRepository.findByCode(code).orElse(null);
            assertThat(type).as("预置类型 %s 存在", code).isNotNull();
            assertThat(type.getDef()).as("类型 %s 已绑定模板", code).isNotNull();
            String xml = type.getDef().getBpmnXml();
            assertThat(xml).as("模板 %s 有 BPMN XML", code).isNotBlank();
            assertThat(xml).as("模板 %s 含流程图 DI（bpmn-js 可渲染）", code).contains("BPMNDiagram");
            // 三个早期模板（报销/请假/采购）无 flowSpec 属预期，只校验 13 个蓝图模板
            if (!List.of("REIMBURSEMENT", "LEAVE", "PROCUREMENT").contains(code)) {
                assertThat(type.getDef().getFlowSpec()).as("模板 %s 有流程设计规格", code).isNotBlank();
            }
        }
    }

    @Test
    void savedDefinitionWithoutDi_getsHealed() {
        loginAs("admin");
        String noDi = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn" id="DEF_heal_t" targetNamespace="http://oa/flow">
                  <process id="heal_t" name="DI自愈" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow id="f1" sourceRef="start" targetRef="approve"/>
                    <userTask id="approve" name="审批" flowable:candidateGroups="ADMIN"/>
                    <sequenceFlow id="f2" sourceRef="approve" targetRef="end"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>""";
        ProcessDefinitionRequest req = new ProcessDefinitionRequest();
        req.setDefKey("heal_t");
        req.setName("DI 自愈模板");
        req.setCategory("test");
        req.setBpmnXml(noDi);
        var dto = processService.createDefinition(req);
        assertThat(processService.getDefinition(dto.getId()).getBpmnXml()).contains("BPMNDiagram");
    }

    @Test
    void seededSystemTemplate_deploysAndRunsEndToEnd() {
        // 引擎里有全部 16 个部署（deployer 启动即部署 PUBLISHED）
        for (String key : List.of("reimbursement", "leave", "procurement", "travel", "overtime",
                "outside", "punch_fix", "seal_use", "car_use", "contract", "payment",
                "advance", "regularization", "recruitment", "resignation", "document")) {
            assertThat(repositoryService.createProcessDefinitionQuery().processDefinitionKey(key).count())
                    .as("引擎部署 %s", key).isGreaterThan(0);
        }

        // 员工发起“合同审批”（新增模板）：经理审批 → 总经理审批
        loginAs("employee");
        Long contractDefId = processService.listDefinitions(true).stream()
                .filter(d -> "contract".equals(d.getDefKey()))
                .map(d -> d.getId())
                .findFirst().orElseThrow();
        ProcessStartRequest req = new ProcessStartRequest();
        req.setDefId(contractDefId);
        req.setTitle("合同审批-模板验证");
        req.setBusinessData("{\"counterparty\":\"示例公司\",\"amount\":\"5000\"}");
        InstanceDTO instance = processService.startInstance(req);
        assertThat(instance.getStatus()).isEqualTo("RUNNING");
        assertThat(instance.getCurrentNode()).isEqualTo("managerApprove");

        List<Task> tasks = flowableTaskService.createTaskQuery()
                .processDefinitionKey("contract")
                .taskDefinitionKey("managerApprove")
                .list();
        assertThat(tasks).isNotEmpty();
    }
}
