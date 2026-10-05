package com.oa;

import com.oa.service.BpmnXmlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * BPMN XML 解析必须兼容两种命名空间风格：
 * - 无元素前缀（默认命名空间，种子模板/前端生成器输出）
 * - bpmn: 前缀（bpmn-js Modeler 保存 XML 的默认导出格式）
 * 用户报错「BPMN XML 缺少 process 元素」即因 getElementsByTagName 按限定名匹配。
 */
@SpringBootTest
class BpmnXmlServiceTests {

    @Autowired
    private BpmnXmlService bpmnXmlService;

    private static final String PREFIXED = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                               xmlns:flowable="http://flowable.org/bpmn"
                               id="DEF_prefix" targetNamespace="http://oa.local">
              <bpmn:process id="prefix_flow" name="前缀流程" isExecutable="true">
                <bpmn:startEvent id="start"/>
                <bpmn:sequenceFlow id="f1" sourceRef="start" targetRef="approve"/>
                <bpmn:userTask id="approve" name="审批" flowable:candidateGroups="MANAGER"/>
                <bpmn:sequenceFlow id="f2" sourceRef="approve" targetRef="end"/>
                <bpmn:endEvent id="end"/>
              </bpmn:process>
            </bpmn:definitions>
            """;

    private static final String DEFAULT_NS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         id="DEF_default" targetNamespace="http://oa.local">
              <process id="default_flow" name="默认命名空间流程" isExecutable="true">
                <startEvent id="start"/>
                <sequenceFlow id="f1" sourceRef="start" targetRef="approve"/>
                <userTask id="approve" name="审批"/>
                <sequenceFlow id="f2" sourceRef="approve" targetRef="end"/>
                <endEvent id="end"/>
              </process>
            </definitions>
            """;

    @Test
    void prefixedXml_extractsProcessKeyAndUserTasks() {
        assertThat(bpmnXmlService.extractProcessKey(PREFIXED)).isEqualTo("prefix_flow");
        var tasks = bpmnXmlService.extractUserTasks(PREFIXED);
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).id()).isEqualTo("approve");
        assertThat(tasks.get(0).name()).isEqualTo("审批");
    }

    @Test
    void defaultNamespaceXml_stillWorks() {
        assertThat(bpmnXmlService.extractProcessKey(DEFAULT_NS)).isEqualTo("default_flow");
        assertThat(bpmnXmlService.userTaskNameMap(DEFAULT_NS))
                .containsEntry("approve", "审批");
    }

    @Test
    void missingProcess_stillRejected() {
        String noProcess = """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" id="x"/>
                """;
        assertThatCode(() -> bpmnXmlService.extractProcessKey(noProcess))
                .hasMessageContaining("缺少 process");
    }
}
