package com.oa.config;

import com.oa.bpmn.BpmnDiHealer;
import com.oa.entity.ApprovalType;
import com.oa.entity.LeaveType;
import com.oa.entity.ProcessDefinition;
import com.oa.entity.User;
import com.oa.enums.ProcessDefinitionStatus;
import com.oa.repository.ApprovalTypeRepository;
import com.oa.repository.LeaveTypeRepository;
import com.oa.repository.ProcessDefinitionRepository;
import com.oa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
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
// 早于 ProcessDefinitionDeployer(200)：新建/修复的模板在同一次启动即部署到 Flowable 引擎
@Order(320)
public class ProcessTemplateSeeder implements ApplicationRunner {

    private final ProcessDefinitionRepository definitionRepository;
    private final ApprovalTypeRepository approvalTypeRepository;
    private final UserRepository userRepository;
    private final LeaveTypeRepository leaveTypeRepository;

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
        seedSystemTemplates();
        seedSystemApprovalTypes();
        // 存量模板 DI 自愈：无 BPMNDiagram 的 bpmnXml 补布局（幂等，一次日志）
        healMissingDi();
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
            bindTypeToTemplate(type);
            approvalTypeRepository.save(type);
            created++;
        }
        if (created > 0) {
            log.info("已预置 {} 个系统审批类型（15 类系统预设）", created);
        }
        // 存量类型补绑：code 小写 == defKey 的模板存在且类型未绑时绑定
        for (ApprovalType type : approvalTypeRepository.findAll()) {
            if (type.getDef() == null) {
                bindTypeToTemplate(type);
                if (type.getDef() != null) {
                    approvalTypeRepository.save(type);
                }
            }
        }
    }

    /** 绑定规则：defKey = code 小写（leave/procurement/travel/... 与模板 defKey 约定一致）。 */
    private void bindTypeToTemplate(ApprovalType type) {
        if (type.getDef() != null) {
            return;
        }
        ProcessDefinition def = definitionRepository
                .findByDefKey(type.getCode().toLowerCase())
                .stream().findFirst().orElse(null);
        if (def != null) {
            type.setDef(def);
        }
    }

    /**
     * 系统模板蓝图（13 个缺模板的预置类型）：表单 + 节点链（主管/角色）+ 自动生成 BPMN（含 DI）。
     * 节点 approverType=supervisor 时前端由发起人主管代审（${supervisorUsername}）。
     */
    private record TemplateBlueprint(String defKey, String name, String category, String description,
                                     String formConfig, List<FlowNodeBlueprint> nodes) {
    }

    private record FlowNodeBlueprint(String id, String name, String approverType, String roles, String signMode) {
    }

    private static FlowNodeBlueprint node(String id, String name, String roles) {
        return new FlowNodeBlueprint(id, name, "role", roles, "single");
    }

    private static FlowNodeBlueprint supervisor(String id, String name) {
        return new FlowNodeBlueprint(id, name, "supervisor", "", "single");
    }

    /** 13 个系统模板蓝图（与前端 15 类型卡片对齐；报销/请假/采购/公文已有模板的不变）。 */
    private List<TemplateBlueprint> systemTemplates() {
        return List.of(
                new TemplateBlueprint("travel", "出差申请", "财务",
                        "出差申请：主管→经理",
                        "{\"fields\":["
                                + "{\"key\":\"destination\",\"label\":\"出差地点\",\"type\":\"input\",\"required\":true,\"placeholder\":\"如：上海\"},"
                                + "{\"key\":\"tripRange\",\"label\":\"出差时段\",\"type\":\"dateRange\",\"required\":true},"
                                + "{\"key\":\"days\",\"label\":\"出差天数\",\"type\":\"number\",\"required\":true,\"min\":0,\"max\":60,\"unit\":\"天\"},"
                                + "{\"key\":\"reason\",\"label\":\"出差事由\",\"type\":\"textarea\",\"required\":true}"
                                + "]}"
                        , List.of(supervisor("supervisorApprove", "主管审批"), node("managerApprove", "经理审批", "MANAGER"))),
                new TemplateBlueprint("overtime", "加班申请", "考勤",
                        "加班申请：经理审批",
                        "{\"fields\":["
                                + "{\"key\":\"overtimeDate\",\"label\":\"加班日期\",\"type\":\"date\",\"required\":true},"
                                + "{\"key\":\"hours\",\"label\":\"加班时长\",\"type\":\"number\",\"required\":true,\"min\":0,\"max\":24,\"unit\":\"小时\"},"
                                + "{\"key\":\"reason\",\"label\":\"加班事由\",\"type\":\"textarea\",\"required\":false}"
                                + "]}"
                        , List.of(node("managerApprove", "经理审批", "MANAGER"))),
                new TemplateBlueprint("outside", "外出申请", "考勤",
                        "外出申请：经理审批",
                        "{\"fields\":["
                                + "{\"key\":\"outsideDate\",\"label\":\"外出日期\",\"type\":\"date\",\"required\":true},"
                                + "{\"key\":\"timeRange\",\"label\":\"外出去返时间\",\"type\":\"dateRange\",\"required\":true},"
                                + "{\"key\":\"destination\",\"label\":\"外出去向\",\"type\":\"input\",\"required\":true},"
                                + "{\"key\":\"reason\",\"label\":\"外出事由\",\"type\":\"textarea\",\"required\":true}"
                                + "]}"
                        , List.of(node("managerApprove", "经理审批", "MANAGER"))),
                new TemplateBlueprint("punch_fix", "补卡申请", "考勤",
                        "打卡补录：经理审批",
                        "{\"fields\":["
                                + "{\"key\":\"fixDate\",\"label\":\"补卡日期\",\"type\":\"date\",\"required\":true},"
                                + "{\"key\":\"inTime\",\"label\":\"应到时间\",\"type\":\"input\",\"required\":false,\"placeholder\":\"如 09:00\"},"
                                + "{\"key\":\"outTime\",\"label\":\"应走时间\",\"type\":\"input\",\"required\":false,\"placeholder\":\"如 18:00\"},"
                                + "{\"key\":\"reason\",\"label\":\"补卡原因\",\"type\":\"textarea\",\"required\":true}"
                                + "]}"
                        , List.of(node("managerApprove", "经理审批", "MANAGER"))),
                new TemplateBlueprint("seal_use", "用章申请", "行政",
                        "公章使用：经理→总经理",
                        "{\"fields\":["
                                + "{\"key\":\"sealType\",\"label\":\"印章类型\",\"type\":\"select\",\"required\":true,\"options\":[\"公章\",\"合同章\",\"财务章\",\"法人章\"]},"
                                + "{\"key\":\"usage\",\"label\":\"用章文件/用途\",\"type\":\"input\",\"required\":true},"
                                + "{\"key\":\"useDate\",\"label\":\"用章日期\",\"type\":\"date\",\"required\":false},"
                                + "{\"key\":\"returnDate\",\"label\":\"归还日期\",\"type\":\"date\",\"required\":false}"
                                + "]}"
                        , List.of(node("managerApprove", "经理审批", "MANAGER"), node("adminApprove", "总经理审批", "ADMIN"))),
                new TemplateBlueprint("car_use", "用车申请", "行政",
                        "公务用车：行政审批",
                        "{\"fields\":["
                                + "{\"key\":\"useRange\",\"label\":\"用车时段\",\"type\":\"dateRange\",\"required\":true},"
                                + "{\"key\":\"destination\",\"label\":\"目的地\",\"type\":\"input\",\"required\":true},"
                                + "{\"key\":\"reason\",\"label\":\"用车事由\",\"type\":\"textarea\",\"required\":false}"
                                + "]}"
                        , List.of(node("adminApprove", "行政审批", "ADMIN"))),
                new TemplateBlueprint("contract", "合同审批", "管理",
                        "合同审批：经理→总经理",
                        "{\"fields\":["
                                + "{\"key\":\"counterparty\",\"label\":\"合同对方\",\"type\":\"input\",\"required\":true},"
                                + "{\"key\":\"amount\",\"label\":\"合同金额\",\"type\":\"amount\",\"required\":true,\"unit\":\"元\",\"min\":0},"
                                + "{\"key\":\"signDate\",\"label\":\"签订日期\",\"type\":\"date\",\"required\":false},"
                                + "{\"key\":\"riskNote\",\"label\":\"风险说明\",\"type\":\"textarea\",\"required\":false}"
                                + "]}"
                        , List.of(node("managerApprove", "经理审批", "MANAGER"), node("adminApprove", "总经理审批", "ADMIN"))),
                new TemplateBlueprint("payment", "付款申请", "财务",
                        "付款申请：经理→财务",
                        "{\"fields\":["
                                + "{\"key\":\"payee\",\"label\":\"收款方\",\"type\":\"input\",\"required\":true},"
                                + "{\"key\":\"amount\",\"label\":\"付款金额\",\"type\":\"amount\",\"required\":true,\"unit\":\"元\",\"min\":0},"
                                + "{\"key\":\"payDate\",\"label\":\"付款日期\",\"type\":\"date\",\"required\":false},"
                                + "{\"key\":\"reason\",\"label\":\"付款事由\",\"type\":\"textarea\",\"required\":true}"
                                + "]}"
                        , List.of(node("managerApprove", "经理审批", "MANAGER"), node("financeApprove", "财务审批", "FINANCE"))),
                new TemplateBlueprint("advance", "预支申请", "财务",
                        "工资/费用预支：经理→财务",
                        "{\"fields\":["
                                + "{\"key\":\"amount\",\"label\":\"预支金额\",\"type\":\"amount\",\"required\":true,\"unit\":\"元\",\"min\":0},"
                                + "{\"key\":\"month\",\"label\":\"预支月份\",\"type\":\"input\",\"required\":false,\"placeholder\":\"如 2026-10\"},"
                                + "{\"key\":\"reason\",\"label\":\"预支事由\",\"type\":\"textarea\",\"required\":true}"
                                + "]}"
                        , List.of(node("managerApprove", "经理审批", "MANAGER"), node("financeApprove", "财务审批", "FINANCE"))),
                new TemplateBlueprint("regularization", "转正申请", "人事",
                        "试用期转正：经理→总经理",
                        "{\"fields\":["
                                + "{\"key\":\"joinDate\",\"label\":\"入职日期\",\"type\":\"date\",\"required\":false},"
                                + "{\"key\":\"selfSummary\",\"label\":\"工作总结\",\"type\":\"textarea\",\"required\":true,\"placeholder\":\"试用期工作成果与自我评估\"}"
                                + "]}"
                        , List.of(node("managerApprove", "经理审批", "MANAGER"), node("adminApprove", "总经理审批", "ADMIN"))),
                new TemplateBlueprint("recruitment", "招聘申请", "人事",
                        "招聘申请：经理→总经理",
                        "{\"fields\":["
                                + "{\"key\":\"position\",\"label\":\"招聘岗位\",\"type\":\"input\",\"required\":true},"
                                + "{\"key\":\"headcount\",\"label\":\"招聘人数\",\"type\":\"number\",\"required\":true,\"min\":1,\"max\":20,\"unit\":\"人\"},"
                                + "{\"key\":\"level\",\"label\":\"期望职级\",\"type\":\"input\",\"required\":false},"
                                + "{\"key\":\"reason\",\"label\":\"招聘原因\",\"type\":\"textarea\",\"required\":true}"
                                + "]}"
                        , List.of(node("managerApprove", "经理审批", "MANAGER"), node("adminApprove", "总经理审批", "ADMIN"))),
                new TemplateBlueprint("resignation", "离职申请", "人事",
                        "离职申请：经理→总经理",
                        "{\"fields\":["
                                + "{\"key\":\"lastWorkDate\",\"label\":\"最后工作日\",\"type\":\"date\",\"required\":true},"
                                + "{\"key\":\"handover\",\"label\":\"工作交接说明\",\"type\":\"textarea\",\"required\":false},"
                                + "{\"key\":\"reason\",\"label\":\"离职原因\",\"type\":\"textarea\",\"required\":true}"
                                + "]}"
                        , List.of(node("managerApprove", "经理审批", "MANAGER"), node("adminApprove", "总经理审批", "ADMIN"))),
                new TemplateBlueprint("document", "公文签发", "公文",
                        "公文签发：经理→总经理",
                        "{\"fields\":["
                                + "{\"key\":\"docTitle\",\"label\":\"公文标题\",\"type\":\"input\",\"required\":true},"
                                + "{\"key\":\"docType\",\"label\":\"公文类型\",\"type\":\"select\",\"required\":true,\"options\":[\"通知\",\"报告\",\"请示\",\"函\",\"纪要\"]},"
                                + "{\"key\":\"urgency\",\"label\":\"紧急程度\",\"type\":\"select\",\"required\":false,\"options\":[\"普通\",\"紧急\",\"特急\"]},"
                                + "{\"key\":\"content\",\"label\":\"正文内容\",\"type\":\"textarea\",\"required\":true}"
                                + "]}"
                        , List.of(node("managerApprove", "经理审批", "MANAGER"), node("adminSign", "签发", "ADMIN"))));
    }

    /**
     * 幂等补种 13 个系统模板（defKey 已存在则跳过）。
     * BPMN 由节点蓝图生成并过 ensureDi（保证流程图可渲染）。
     */
    private void seedSystemTemplates() {
        User admin = userRepository.findByUsername("admin").orElse(null);
        int created = 0;
        for (TemplateBlueprint bp : systemTemplates()) {
            if (!definitionRepository.findByDefKey(bp.defKey()).isEmpty()) {
                continue;
            }
            ProcessDefinition def = new ProcessDefinition();
            def.setDefKey(bp.defKey());
            def.setName(bp.name());
            def.setVersion(1);
            def.setCategory(bp.category());
            def.setDescription(bp.description());
            def.setFormConfig(bp.formConfig());
            def.setBpmnXml(BpmnDiHealer.ensureDi(buildBlueprintBpmn(bp)));
            def.setFlowSpec(buildFlowSpecJson(bp.nodes()));
            def.setStatus(ProcessDefinitionStatus.PUBLISHED);
            def.setCreator(admin);
            def.setPublishedAt(LocalDateTime.now());
            definitionRepository.save(def);
            created++;
        }
        if (created > 0) {
            log.info("已补种 {} 个系统审批模板（13 类预置类型全覆盖）", created);
        }
    }

    /** 由节点蓝图生成链式 BPMN（与前端设计器 buildFlowBpmnXml 同构）：主管节点前置解析 serviceTask。 */
    private String buildBlueprintBpmn(TemplateBlueprint bp) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
                + " xmlns:flowable=\"http://flowable.org/bpmn\" id=\"DEF_" + bp.defKey()
                + "\" targetNamespace=\"http://oa.local/" + bp.defKey() + "\">\n");
        sb.append("  <process id=\"").append(bp.defKey()).append("\" name=\"")
                .append(escXml(bp.name())).append("\" isExecutable=\"true\">\n");
        sb.append("    <startEvent id=\"start\" name=\"发起\"/>\n");
        String prev = "start";
        for (FlowNodeBlueprint n : bp.nodes()) {
            if ("supervisor".equals(n.approverType())) {
                String prepId = "prep_" + n.id();
                sb.append("    <sequenceFlow id=\"f_").append(prev).append('_').append(prepId)
                        .append("\" sourceRef=\"").append(prev).append("\" targetRef=\"").append(prepId).append("\"/>\n");
                sb.append("    <serviceTask id=\"").append(prepId)
                        .append("\" name=\"解析发起人主管\" flowable:delegateExpression=\"${assignSupervisorDelegate}\"/>\n");
                prev = prepId;
            }
            sb.append("    <sequenceFlow id=\"f_").append(prev).append('_').append(n.id())
                    .append("\" sourceRef=\"").append(prev).append("\" targetRef=\"").append(n.id()).append("\"/>\n");
            if ("supervisor".equals(n.approverType())) {
                sb.append("    <userTask id=\"").append(n.id()).append("\" name=\"").append(escXml(n.name()))
                        .append("\" flowable:candidateUsers=\"${supervisorUsername}\"/>\n");
            } else {
                sb.append("    <userTask id=\"").append(n.id()).append("\" name=\"").append(escXml(n.name()))
                        .append("\" flowable:candidateGroups=\"").append(n.roles()).append("\"/>\n");
            }
            prev = n.id();
        }
        sb.append("    <sequenceFlow id=\"f_").append(prev).append("_end\" sourceRef=\"").append(prev)
                .append("\" targetRef=\"end\"/>\n");
        sb.append("    <endEvent id=\"end\" name=\"结束\"/>\n");
        sb.append("  </process>\n");
        sb.append("</definitions>");
        return sb.toString();
    }

    /** 节点蓝图 → flowSpec JSON（前端 FlowSpec 契约）。 */
    private String buildFlowSpecJson(List<FlowNodeBlueprint> nodes) {
        StringBuilder sb = new StringBuilder("{\"nodes\":[");
        for (int i = 0; i < nodes.size(); i++) {
            FlowNodeBlueprint n = nodes.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"id\":\"").append(n.id())
                    .append("\",\"name\":\"").append(escJson(n.name()))
                    .append("\",\"approverType\":\"").append(n.approverType())
                    .append("\",\"roles\":[");
            if (!n.roles().isBlank()) {
                String[] roles = n.roles().split(",");
                for (int j = 0; j < roles.length; j++) {
                    if (j > 0) {
                        sb.append(',');
                    }
                    sb.append("\"").append(roles[j].trim()).append("\"");
                }
            }
            sb.append("],\"signMode\":\"").append(n.signMode()).append("\"}");
        }
        return sb.append("]}").toString();
    }

    /**
     * 存量模板 DI 自愈：bpmnXml 缺 BPMNDiagram 的补布局并落库。
     * 幂等：修复后含 BPMNDiagram，下次启动跳过。
     */
    private void healMissingDi() {
        int healed = 0;
        for (ProcessDefinition def : definitionRepository.findAll()) {
            String xml = def.getBpmnXml();
            if (xml == null || xml.isBlank() || BpmnDiHealer.hasDi(xml)) {
                continue;
            }
            String fixed = BpmnDiHealer.ensureDi(xml);
            if (!fixed.equals(xml)) {
                def.setBpmnXml(fixed);
                definitionRepository.save(def);
                healed++;
            }
        }
        if (healed > 0) {
            log.info("已为 {} 个存量模板补齐流程图 DI（修复流程图空白）", healed);
        }
    }

    private static String escXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String escJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
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

    /** simpleBpmn 结果过 DI 自愈（新装库直接带布局）。 */
    private static String simpleBpmnWithDi(String processId, String name, String taskName) {
        return BpmnDiHealer.ensureDi(simpleBpmn(processId, name, taskName));
    }

    private void seedLeaveTemplate() throws Exception {
        // 请假类型选项与 leave_type 表同源（规格 5.2 权威表序：权重降序）
        String optionsJson = leaveTypeRepository.findByEnabledTrueOrderByWeightDescIdAsc().stream()
                .map(LeaveType::getName)
                .map(n -> "\"" + n + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        String formConfig = "{\"fields\":["
                + "{\"key\":\"leaveType\",\"label\":\"请假类型\",\"type\":\"radio\",\"required\":true,\"options\":[" + optionsJson + "]},"
                + "{\"key\":\"leaveRange\",\"label\":\"请假时段\",\"type\":\"dateRange\",\"required\":true},"
                + "{\"key\":\"days\",\"label\":\"请假时长\",\"type\":\"number\",\"required\":true,\"min\":0,\"max\":60,\"unit\":\"天\"},"
                + "{\"key\":\"reason\",\"label\":\"请假事由\",\"type\":\"textarea\",\"required\":true,\"placeholder\":\"请说明请假原因\"}"
                + "]}";

        var existing = definitionRepository.findByDefKey("leave");
        if (!existing.isEmpty()) {
            // 存量模板：formConfig 与最新选项不一致则就地更新（保留用户自定义 BPMN）
            ProcessDefinition def = existing.get(0);
            if (!formConfig.equals(def.getFormConfig())) {
                def.setFormConfig(formConfig);
                definitionRepository.save(def);
                log.info("更新请假模板表单选项（假期类型联动，{} 项）",
                        leaveTypeRepository.findByEnabledTrueOrderByWeightDescIdAsc().size());
            }
            return;
        }
        User admin = userRepository.findByUsername("admin").orElse(null);
        ProcessDefinition def = new ProcessDefinition();
        def.setDefKey("leave");
        def.setName("请假审批");
        def.setVersion(1);
        def.setCategory("leave");
        def.setDescription("请假申请（年假/病假/事假等），部门经理审批");
        def.setFormConfig(formConfig);
        def.setBpmnXml(simpleBpmnWithDi("leave", "请假审批", "经理审批"));
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
        def.setBpmnXml(simpleBpmnWithDi("procurement", "采购审批", "经理审批"));
        def.setFlowSpec("{\"nodes\":[{\"id\":\"managerApprove\",\"name\":\"经理审批\",\"approverType\":\"role\",\"roles\":[\"MANAGER\"],\"signMode\":\"single\"}]}");
        def.setStatus(ProcessDefinitionStatus.PUBLISHED);
        def.setCreator(admin);
        def.setPublishedAt(LocalDateTime.now());
        definitionRepository.save(def);
        log.info("已内置采购审批模板 procurement");
    }

}
