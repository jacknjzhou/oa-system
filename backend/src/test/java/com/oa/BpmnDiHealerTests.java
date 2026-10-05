package com.oa;

import com.oa.bpmn.BpmnDiHealer;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** BPMN DI 自愈：缺 DI 的 XML 注入可渲染布局；已有 DI 不动；结果可部署可发起。 */
@SpringBootTest
class BpmnDiHealerTests {

    @Autowired
    private RepositoryService repositoryService;
    @Autowired
    private RuntimeService runtimeService;
    @Autowired
    private TaskService taskService;

    /** 无 DI 的简单链式流程（模拟 seeder simpleBpmn 输出）。 */
    private static final String NO_DI_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                              xmlns:flowable="http://flowable.org/bpmn" id="DEF_t1" targetNamespace="http://oa/flow">
              <bpmn:process id="diag_no_di" name="无DI流程" isExecutable="true">
                <bpmn:startEvent id="start" name="发起"/>
                <bpmn:sequenceFlow id="f1" sourceRef="start" targetRef="managerApprove"/>
                <bpmn:userTask id="managerApprove" name="经理审批" flowable:candidateGroups="MANAGER"/>
                <bpmn:sequenceFlow id="f2" sourceRef="managerApprove" targetRef="end"/>
                <bpmn:endEvent id="end" name="结束"/>
              </bpmn:process>
            </bpmn:definitions>""";

    @Test
    void noDi_getsInjectedAndRendersAllNodes() {
        String healed = BpmnDiHealer.ensureDi(NO_DI_XML);
        assertThat(healed).contains("BPMNDiagram");
        assertThat(healed).contains("BPMNPlane");
        assertThat(healed).contains("bpmnElement=\"start\"");
        assertThat(healed).contains("bpmnElement=\"managerApprove\"");
        assertThat(healed).contains("bpmnElement=\"end\"");
        assertThat(healed).contains("BPMNEdge");
        // 任务盒子
        assertThat(healed).contains("width=\"120\"");
    }

    @Test
    void noDi_healedXml_deploysAndStartsInFlowable() throws Exception {
        String healed = BpmnDiHealer.ensureDi(NO_DI_XML);
        var deployment = repositoryService.createDeployment()
                .addString("diag_no_di.bpmn20.xml", healed)
                .deploy();
        var def = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey("diag_no_di")
                .singleResult();
        assertThat(def).isNotNull();
        var instance = runtimeService.startProcessInstanceById(def.getId());
        Task task = taskService.createTaskQuery().processInstanceId(instance.getId()).singleResult();
        assertThat(task).isNotNull();
        assertThat(task.getTaskDefinitionKey()).isEqualTo("managerApprove");
        // 清理：完成任务使实例结束，并删除部署，避免残留任务污染其他测试类（共享 H2）
        taskService.complete(task.getId());
        assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(instance.getId()).count()).isZero();
        repositoryService.deleteDeployment(deployment.getId(), true);
    }

    @Test
    void hasDi_returnsUnchanged() throws Exception {
        String xml = new String(
                new ClassPathResource("processes/reimbursement.bpmn20.xml").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        assertThat(xml).contains("BPMNDiagram");
        assertThat(BpmnDiHealer.ensureDi(xml)).isEqualTo(xml);
    }

    @Test
    void prefixlessBpmn_getsDiInjected() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" id="DEF_t2" targetNamespace="http://oa/flow">
                  <process id="diag_plain" name="无前缀" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow id="f1" sourceRef="start" targetRef="approve"/>
                    <userTask id="approve" name="审批"/>
                    <sequenceFlow id="f2" sourceRef="approve" targetRef="end"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>""";
        String healed = BpmnDiHealer.ensureDi(xml);
        assertThat(healed).contains("BPMNDiagram");
        assertThat(healed).contains("bpmnElement=\"approve\"");
        assertThat(healed).endsWith("</definitions>");
    }

    @Test
    void invalidXml_returnsUnchanged() {
        String broken = "<bpmn:definitions><broken";
        assertThat(BpmnDiHealer.ensureDi(broken)).isEqualTo(broken);
    }
}
