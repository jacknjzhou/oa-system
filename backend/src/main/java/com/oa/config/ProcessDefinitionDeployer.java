package com.oa.config;

import com.oa.entity.ProcessDefinition;
import com.oa.enums.ProcessDefinitionStatus;
import com.oa.repository.ProcessDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 启动时把已发布且有 BPMN 的模板补部署进 Flowable 引擎。
 *
 * <p>覆盖"模板由 seeder 直接落库、从未走 create/update API"的场景
 * （如内置的请假/采购模板）：引擎里没有对应流程定义时发起会 500。
 * 幂等：已存在同 key 定义则跳过，避免重复部署膨胀。
 */
@Slf4j
@Order(200)
@Component
@RequiredArgsConstructor
public class ProcessDefinitionDeployer implements ApplicationRunner {

    private final ProcessDefinitionRepository definitionRepository;
    private final RepositoryService repositoryService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (ProcessDefinition def : definitionRepository.findByStatus(ProcessDefinitionStatus.PUBLISHED)) {
            if (def.getBpmnXml() == null || def.getBpmnXml().isBlank()) {
                continue;
            }
            long existing = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionKey(def.getDefKey())
                    .count();
            if (existing > 0) {
                continue;
            }
            repositoryService.createDeployment()
                    .name(def.getDefKey() + "-bootstrap")
                    .addString(def.getDefKey() + ".bpmn20.xml", def.getBpmnXml())
                    .deploy();
            log.info("启动补部署流程定义: {}（{}）", def.getDefKey(), def.getName());
        }
    }
}
