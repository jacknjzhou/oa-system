package com.oa.config;

import com.oa.entity.ProcessDefinition;
import com.oa.entity.User;
import com.oa.enums.ProcessDefinitionStatus;
import com.oa.repository.ProcessDefinitionRepository;
import com.oa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * 内置报销审批模板种子：Flowable 自动部署 classpath BPMN 后，
 * 若业务表中尚无对应模板元数据（首次启动 / 历史库无数据），补齐一条 PUBLISHED 记录。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessTemplateSeeder implements ApplicationRunner {

    private final ProcessDefinitionRepository definitionRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (!definitionRepository.findByDefKey("reimbursement").isEmpty()) {
            return;
        }
        String bpmnXml = new String(
                new ClassPathResource("processes/reimbursement.bpmn20.xml").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        User admin = userRepository.findByUsername("admin").orElse(null);

        ProcessDefinition def = new ProcessDefinition();
        def.setDefKey("reimbursement");
        def.setName("报销审批");
        def.setVersion(1);
        def.setCategory("finance");
        def.setDescription("金额超过 1 万需财务与部门经理会签，再总经理审批");
        def.setFormConfig("""
                {"fields":[
                  {"key":"amount","label":"金额","type":"number","required":true},
                  {"key":"reason","label":"报销事由","type":"textarea","required":true}
                ]}""");
        def.setBpmnXml(bpmnXml);
        def.setStatus(ProcessDefinitionStatus.PUBLISHED);
        def.setCreator(admin);
        def.setPublishedAt(LocalDateTime.now());
        definitionRepository.save(def);
        log.info("已内置报销审批模板 reimbursement");
    }
}
