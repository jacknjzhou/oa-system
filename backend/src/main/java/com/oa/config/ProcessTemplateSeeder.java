package com.oa.config;

import com.oa.entity.ApprovalType;
import com.oa.entity.ProcessDefinition;
import com.oa.entity.User;
import com.oa.enums.ProcessDefinitionStatus;
import com.oa.repository.ApprovalTypeRepository;
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
import java.util.ArrayList;
import java.util.List;

/**
 * 内置报销审批模板种子：Flowable 自动部署 classpath BPMN 后，
 * 若业务表中尚无对应模板元数据（首次启动 / 历史库无数据），补齐一条 PUBLISHED 记录。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessTemplateSeeder implements ApplicationRunner {

    private final ProcessDefinitionRepository definitionRepository;
    private final ApprovalTypeRepository approvalTypeRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        boolean defExists = !definitionRepository.findByDefKey("reimbursement").isEmpty();
        ProcessDefinition def = defExists
                ? definitionRepository.findByDefKey("reimbursement").stream().findFirst().orElseThrow()
                : createReimbursementDefinition();
        healReimbursementForm(def);
        // 预置“报销”审批类型（幂等；覆盖旧库升级路径：模板在、类型缺）
        seedReimbursementType(def);
        // 请假/采购完整模板 + 15 个系统审批预设（幂等；先建模板再绑类型）
        seedLeaveTemplate();
        seedProcurementTemplate();
        seedSystemApprovalTypes();
        // 预置演示主管关系（“发起人主管”审批人解析演示）：幂等，已设置则不动
        seedSupervisors();
    }

    /** 15 个系统审批预设（幂等：code 已存在则跳过）。 */
    private void seedSystemApprovalTypes() {
        String[][] presets = {
                {"LEAVE", "请假", "🏖️", "假期", "请假审批（年假/病假/事假等）"},
                {"TRAVEL", "出差", "🚄", "财务", "出差申请与报销"},
                {"PROCUREMENT", "采购", "📦", "采购", "采购申请审批"},
                {"OVERTIME", "加班", "⏰", "考勤", "加班申请"},
                {"OUTSIDE", "外出", "🚪", "考勤", "外出申请"},
                {"PUNCH_FIX", "补卡", "🕐", "考勤", "打卡补录申请"},
                {"SEAL_USE", "用章", "📮", "行政", "公章使用申请"},
                {"CAR_USE", "用车", "🚗", "行政", "公务用车申请"},
                {"CONTRACT", "合同", "📝", "管理", "合同审批"},
                {"PAYMENT", "付款", "💰", "财务", "付款申请"},
                {"ADVANCE", "预支", "💵", "财务", "工资预支申请"},
                {"REGULARIZATION", "转正", "👔", "人事", "试用期转正申请"},
                {"RECRUITMENT", "招聘", "📥", "人事", "招聘申请"},
                {"RESIGNATION", "离职", "📤", "人事", "离职申请"},
                {"DOCUMENT", "公文", "📜", "公文", "公文签发"},
        };
        int created = 0;
        for (String[] p : presets) {
            if (approvalTypeRepository.findByCode(p[0]).isPresent()) {
                continue;
            }
            ApprovalType type = new ApprovalType();
            type.setCode(p[0]);
            type.setName(p[1]);
            type.setCategory(p[3]);
            type.setIcon(p[2]);
            type.setDescription(p[4]);
            type.setWeight((int) approvalTypeRepository.count() + 10);
            type.setEnabled(true);
            if ("PROCUREMENT".equals(p[0])) {
                type.setDef(definitionRepository.findByDefKey("procurement").stream().findFirst().orElse(null));
            } else if ("LEAVE".equals(p[0])) {
                type.setDef(definitionRepository.findByDefKey("leave").stream().findFirst().orElse(null));
            } else if ("DOCUMENT".equals(p[0])) {
                type.setDef(definitionRepository.findByDefKey("document").stream().findFirst().orElse(null));
            }
            approvalTypeRepository.save(type);
            created++;
        }
        if (created > 0) {
            log.info("已预置 {} 个系统审批类型（15 类系统预设）", created);
        }
    }

    private void seedSupervisors() {
        User admin = userRepository.findByUsername("admin").orElse(null);
        User manager = userRepository.findByUsername("manager").orElse(null);
        User employee = userRepository.findByUsername("employee").orElse(null);
        User finance = userRepository.findByUsername("finance").orElse(null);
        boolean changed = false;
        if (admin != null && manager != null && manager.getSupervisorId() == null) {
            manager.setSupervisorId(admin.getId());
            changed = true;
        }
        if (manager != null && employee != null && employee.getSupervisorId() == null) {
            employee.setSupervisorId(manager.getId());
            changed = true;
        }
        if (manager != null && finance != null && finance.getSupervisorId() == null) {
            finance.setSupervisorId(manager.getId());
            changed = true;
        }
        if (changed) {
            List<User> dirty = new ArrayList<>();
            if (manager != null && manager.getSupervisorId() != null) dirty.add(manager);
            if (employee != null && employee.getSupervisorId() != null) dirty.add(employee);
            if (finance != null && finance.getSupervisorId() != null) dirty.add(finance);
            userRepository.saveAll(dirty);
            log.info("已预置演示主管关系（manager→admin，employee/finance→manager）");
        }
    }

    /**
     * 表单配置自愈：旧库模板（P2 前只有 2 个字段）升级为完整报销表单。
     * 幂等标记：formConfig 已含 "type":"amount" 则跳过。
     */
    private static final String FULL_REIMBURSEMENT_FORM = """
            {"fields":[
              {"key":"amount","label":"报销金额","type":"amount","required":true,"unit":"元","min":0,"placeholder":"请输入金额"},
              {"key":"reason","label":"报销事由","type":"textarea","required":true,"placeholder":"请说明报销事由"},
              {"key":"category","label":"费用类别","type":"select","required":true,"options":["差旅费","办公费","招待费","培训费","其他"]},
              {"key":"expenseDate","label":"费用发生日期","type":"date","required":false},
              {"key":"invoice","label":"发票/票据","type":"attachment","required":false}
            ]}""";

    private void healReimbursementForm(ProcessDefinition def) {
        String current = def.getFormConfig() == null ? "" : def.getFormConfig();
        if (current.contains("\"type\":\"amount\"")) {
            return;
        }
        def.setFormConfig(FULL_REIMBURSEMENT_FORM);
        definitionRepository.save(def);
        log.info("已升级报销模板表单配置（完整报销表单）");
    }

    private ProcessDefinition createReimbursementDefinition() throws Exception {
        if (!definitionRepository.findByDefKey("reimbursement").isEmpty()) {
            return definitionRepository.findByDefKey("reimbursement").stream().findFirst().orElseThrow();
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
        def.setFormConfig(FULL_REIMBURSEMENT_FORM);
        def.setBpmnXml(bpmnXml);
        def.setStatus(ProcessDefinitionStatus.PUBLISHED);
        def.setCreator(admin);
        def.setPublishedAt(LocalDateTime.now());
        definitionRepository.save(def);
        log.info("已内置报销审批模板 reimbursement");
        return def;
    }

    /** 幂等补种“报销”审批类型：关联 reimbursement 模板。 */
    private void seedReimbursementType(ProcessDefinition def) {
        approvalTypeRepository.findByCode("REIMBURSEMENT").ifPresentOrElse(
                existing -> {
                    if (existing.getDef() == null && def != null) {
                        existing.setDef(def);
                        approvalTypeRepository.save(existing);
                    }
                },
                () -> {
                    ApprovalType type = new ApprovalType();
                    type.setCode("REIMBURSEMENT");
                    type.setName("报销");
                    type.setCategory("财务");
                    type.setIcon("🧾");
                    type.setDescription("费用报销审批");
                    type.setWeight(10);
                    type.setDef(def);
                    type.setEnabled(true);
                    approvalTypeRepository.save(type);
                    log.info("已内置“报销”审批类型");
                });
    }

    // ==================== 请假 / 采购模板（P2-4）====================

    private static String simpleBpmn(String processId, String name, String taskName) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                                  xmlns:flowable="http://flowable.org/bpmn" id="DEF_%s" targetNamespace="http://oa/flow">
                  <bpmn:process id="%s" name="%s" isExecutable="true">
                    <bpmn:startEvent id="start" name="发起"/>
                    <bpmn:sequenceFlow id="f1" sourceRef="start" targetRef="managerApprove"/>
                    <bpmn:userTask id="managerApprove" name="%s" flowable:candidateGroups="MANAGER"/>
                    <bpmn:sequenceFlow id="f2" sourceRef="managerApprove" targetRef="end"/>
                    <bpmn:endEvent id="end" name="结束"/>
                  </bpmn:process>
                </bpmn:definitions>""".formatted(processId, processId, name, taskName);
    }

    private void seedLeaveTemplate() throws Exception {
        if (!definitionRepository.findByDefKey("leave").isEmpty()) {
            return;
        }
        User admin = userRepository.findByUsername("admin").orElse(null);
        ProcessDefinition def = new ProcessDefinition();
        def.setDefKey("leave");
        def.setName("请假审批");
        def.setVersion(1);
        def.setCategory("leave");
        def.setDescription("请假申请（年假/病假/事假等），部门经理审批");
        def.setFormConfig("""
                {"fields":[
                  {"key":"leaveType","label":"请假类型","type":"radio","required":true,"options":["年假","病假","事假","婚假","产假","其他"]},
                  {"key":"leaveRange","label":"请假时段","type":"dateRange","required":true},
                  {"key":"days","label":"请假天数","type":"number","required":true,"min":0,"max":60,"unit":"天"},
                  {"key":"reason","label":"请假事由","type":"textarea","required":true,"placeholder":"请说明请假原因"}
                ]}""");
        def.setBpmnXml(simpleBpmn("leave", "请假审批", "经理审批"));
        def.setFlowSpec("{\"nodes\":[{\"id\":\"managerApprove\",\"name\":\"经理审批\",\"approverType\":\"role\",\"roles\":[\"MANAGER\"],\"signMode\":\"single\"}]}");
        def.setStatus(ProcessDefinitionStatus.PUBLISHED);
        def.setCreator(admin);
        def.setPublishedAt(LocalDateTime.now());
        definitionRepository.save(def);
        log.info("已内置请假审批模板 leave");
    }

    private void seedProcurementTemplate() throws Exception {
        if (!definitionRepository.findByDefKey("procurement").isEmpty()) {
            return;
        }
        User admin = userRepository.findByUsername("admin").orElse(null);
        ProcessDefinition def = new ProcessDefinition();
        def.setDefKey("procurement");
        def.setName("采购审批");
        def.setVersion(1);
        def.setCategory("procurement");
        def.setDescription("采购申请，部门经理审批，超 5000 元加总经理审批");
        def.setFormConfig("""
                {"fields":[
                  {"key":"itemName","label":"采购物品","type":"input","required":true,"placeholder":"物品/服务名称"},
                  {"key":"amount","label":"预计金额","type":"amount","required":true,"unit":"元","min":0},
                  {"key":"supplier","label":"供应商","type":"input","required":false},
                  {"key":"reason","label":"采购用途","type":"textarea","required":true}
                ]}""");
        def.setBpmnXml(simpleBpmn("procurement", "采购审批", "经理审批"));
        def.setFlowSpec("{\"nodes\":[{\"id\":\"managerApprove\",\"name\":\"经理审批\",\"approverType\":\"role\",\"roles\":[\"MANAGER\"],\"signMode\":\"single\"}]}");
        def.setStatus(ProcessDefinitionStatus.PUBLISHED);
        def.setCreator(admin);
        def.setPublishedAt(LocalDateTime.now());
        definitionRepository.save(def);
        log.info("已内置采购审批模板 procurement");
    }

}
