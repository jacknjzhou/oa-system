package com.oa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oa.dto.DefinitionDTO;
import com.oa.dto.InstanceDTO;
import com.oa.dto.ProcessDefinitionRequest;
import com.oa.dto.ProcessStartRequest;
import com.oa.entity.ApprovalRecord;
import com.oa.entity.ProcessDefinition;
import com.oa.entity.ProcessInstance;
import com.oa.entity.User;
import com.oa.enums.ApprovalAction;
import com.oa.enums.BusinessType;
import com.oa.enums.NotifyType;
import com.oa.enums.Priority;
import com.oa.enums.ProcessDefinitionStatus;
import com.oa.enums.ProcessInstanceStatus;
import com.oa.enums.RefType;
import com.oa.entity.Role;
import com.oa.repository.ApprovalRecordRepository;
import com.oa.repository.CcRecordRepository;
import com.oa.repository.ProcessDefinitionRepository;
import com.oa.repository.ProcessInstanceRepository;
import com.oa.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 审批模板与流程实例服务：基于 Flowable BPMN 引擎。
 * 自有表存业务快照（模板元数据/实例/审批历史），Flowable 为运行时事实源。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ProcessDefinitionRepository definitionRepository;
    private final ProcessInstanceRepository instanceRepository;
    private final ApprovalRecordRepository approvalRecordRepository;
    private final CcRecordRepository ccRecordRepository;
    private final RoleRepository roleRepository;
    private final AuthService authService;
    private final NotificationService notificationService;
    private final BpmnXmlService bpmnXmlService;
    private final ObjectMapper objectMapper;
    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final org.flowable.engine.TaskService flowableTaskService;
    private final org.flowable.engine.HistoryService historyService;
    private final JdbcTemplate jdbcTemplate;

    // ==================== 模板管理 ====================

    @Transactional(readOnly = true)
    public List<DefinitionDTO> listDefinitions(boolean deployableOnly) {
        List<ProcessDefinition> defs = deployableOnly
                ? definitionRepository.findByStatus(ProcessDefinitionStatus.PUBLISHED)
                : definitionRepository.findAll();
        return defs.stream().map(d -> toDto(d, false)).toList();
    }

    @Transactional(readOnly = true)
    public DefinitionDTO getDefinition(Long id) {
        return toDto(loadDefinition(id), true);
    }

    @Transactional
    public DefinitionDTO createDefinition(ProcessDefinitionRequest req) {
        String processKey = bpmnXmlService.extractProcessKey(req.getBpmnXml());
        if (!processKey.equals(req.getDefKey())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "BPMN process id [" + processKey + "] 与模板 key [" + req.getDefKey() + "] 不一致");
        }
        if (!definitionRepository.findByDefKey(req.getDefKey()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模板 key 已存在: " + req.getDefKey());
        }
        User creator = authService.getCurrentUser();

        ProcessDefinition def = new ProcessDefinition();
        def.setDefKey(req.getDefKey());
        def.setName(req.getName());
        def.setCategory(req.getCategory());
        def.setDescription(req.getDescription());
        def.setFormConfig(req.getFormConfig());
        def.setBpmnXml(req.getBpmnXml());
        def.setStatus(ProcessDefinitionStatus.DRAFT);
        def.setCreator(creator);
        def.setVersion(0);
        def = definitionRepository.save(def);

        def = deployAndSyncVersion(def);
        return toDto(def, false);
    }

    @Transactional
    public DefinitionDTO updateDefinition(Long id, ProcessDefinitionRequest req) {
        ProcessDefinition def = loadDefinition(id);
        String processKey = bpmnXmlService.extractProcessKey(req.getBpmnXml());
        if (!processKey.equals(def.getDefKey())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "BPMN process id [" + processKey + "] 与模板 key [" + def.getDefKey() + "] 不一致，模板 key 不可修改");
        }
        def.setName(req.getName());
        def.setCategory(req.getCategory());
        def.setDescription(req.getDescription());
        def.setFormConfig(req.getFormConfig());
        boolean xmlChanged = !req.getBpmnXml().equals(def.getBpmnXml());
        def.setBpmnXml(req.getBpmnXml());
        if (xmlChanged) {
            def = deployAndSyncVersion(def);
        }
        def = definitionRepository.save(def);
        return toDto(def, false);
    }

    @Transactional
    public DefinitionDTO publish(Long id) {
        ProcessDefinition def = loadDefinition(id);
        def.setStatus(ProcessDefinitionStatus.PUBLISHED);
        def.setPublishedAt(LocalDateTime.now());
        return toDto(definitionRepository.save(def), false);
    }

    @Transactional
    public DefinitionDTO disable(Long id) {
        ProcessDefinition def = loadDefinition(id);
        def.setStatus(ProcessDefinitionStatus.DISABLED);
        return toDto(definitionRepository.save(def), false);
    }

    /** 部署 BPMN XML 到 Flowable，并将版本号与 Flowable 内部版本对齐。 */
    private ProcessDefinition deployAndSyncVersion(ProcessDefinition def) {
        try {
            repositoryService.createDeployment()
                    .name(def.getName())
                    .key(def.getDefKey())
                    .addString(def.getDefKey() + ".bpmn20.xml", def.getBpmnXml())
                    .deploy();
        } catch (FlowableException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "BPMN 部署失败（请检查 XML 是否为合法的 BPMN 2.0 流程）: " + e.getMessage());
        }
        Integer flowableVersion = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(def.getDefKey())
                .latestVersion()
                .list().stream()
                .findFirst()
                .map(org.flowable.engine.repository.ProcessDefinition::getVersion)
                .orElse(1);
        def.setVersion(flowableVersion);
        return def;
    }

    // ==================== 实例管理 ====================

    @Transactional
    public InstanceDTO startInstance(ProcessStartRequest req) {
        ProcessDefinition def = loadDefinition(req.getDefId());
        if (def.getStatus() != ProcessDefinitionStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模板未发布，无法发起");
        }
        User initiator = authService.getCurrentUser();

        ProcessInstance instance = new ProcessInstance();
        instance.setInstanceNo(generateInstanceNo());
        instance.setDef(def);
        instance.setDefVersion(def.getVersion());
        instance.setTitle(req.getTitle());
        instance.setInitiator(initiator);
        instance.setBusinessType(req.getBusinessType() != null ? req.getBusinessType() : BusinessType.REIMBURSEMENT);
        instance.setBusinessData(req.getBusinessData());
        instance.setPriority(Priority.NORMAL);

        if (req.getCcUserIds() != null && !req.getCcUserIds().isEmpty()) {
            instance.setCcUserIds(new java.util.LinkedHashSet<>(req.getCcUserIds()));
        }

        if (Boolean.TRUE.equals(req.getDraft())) {
            // 草稿：只落自有表（status=DRAFT，无 Flowable 实例），不部署不启动引擎
            instance.setSubmittedAt(null);
            instance.setStatus(ProcessInstanceStatus.DRAFT);
            instance = instanceRepository.save(instance);
            return toInstanceDto(instance);
        }

        instance.setSubmittedAt(LocalDateTime.now());
        instance.setStatus(ProcessInstanceStatus.RUNNING);
        instance = instanceRepository.save(instance);

        launchEngine(instance);
        return toInstanceDto(instance);
    }

    /** 提交草稿：发起人把 DRAFT 实例启动为 RUNNING 流程。 */
    @Transactional
    public InstanceDTO submitInstance(Long id) {
        ProcessInstance instance = loadInstance(id);
        if (instance.getStatus() != ProcessInstanceStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅草稿状态可提交");
        }
        User operator = authService.getCurrentUser();
        if (!instance.getInitiator().getId().equals(operator.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅发起人可提交该草稿");
        }
        instance.setStatus(ProcessInstanceStatus.RUNNING);
        instance.setSubmittedAt(LocalDateTime.now());
        instance = instanceRepository.save(instance);

        launchEngine(instance);
        return toInstanceDto(instance);
    }

    /** 启动 Flowable 引擎实例、记录发起审批、同步当前节点与待办通知。 */
    private void launchEngine(ProcessInstance instance) {
        Map<String, Object> variables = parseVariables(instance.getBusinessData());
        // 抄送名单：草稿提交时同样携带（若发起时选择了抄送人）
        if (instance.getCcUserIds() != null && !instance.getCcUserIds().isEmpty()) {
            variables.put("ccUserIds", java.util.List.copyOf(instance.getCcUserIds()));
        }
        org.flowable.engine.runtime.ProcessInstance flowableInstance = runtimeService
                .startProcessInstanceByKey(instance.getDef().getDefKey(), instance.getInstanceNo(), variables);
        instance.setFlowableInstanceId(flowableInstance.getId());
        instance = instanceRepository.save(instance);

        User initiator = instance.getInitiator();
        recordApproval(instance, null, "start", "发起",
                ApprovalAction.SUBMIT, initiator, "发起流程", "start", null);

        syncInstanceAfterAction(instance);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getInstance(Long id) {
        ProcessInstance instance = loadInstance(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instance", toInstanceDto(instance));
        result.put("bpmnXml", instance.getDef().getBpmnXml());
        result.put("completedActivityIds", completedActivityIds(instance));
        result.put("currentActivityIds", currentActivityIds(instance));
        result.put("approvalRecords", toRecordDtos(
                approvalRecordRepository.findByInstanceIdOrderByCreatedAtAsc(id)));
        result.put("countersigns", getCountersignProgress(instance));
        return result;
    }

    /**
     * 会签进度：找出实例中「同一节点存在多个任务」的多实例节点，
     * 返回每个会签节点的元素明细（候选组、状态、经办人）与汇总计数。
     * 非会签节点不会出现（任务数 = 1）。
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCountersignProgress(ProcessInstance instance) {
        String fpiId = instance.getFlowableInstanceId();
        if (fpiId == null) {
            return List.of();
        }
        List<org.flowable.task.api.Task> activeTasks = flowableTaskService.createTaskQuery()
                .processInstanceId(fpiId).active().list();
        List<HistoricTaskInstance> historicTasks = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(fpiId).list();

        // 按节点 key 归并活跃任务与历史任务，多实例节点 = 任务实例数 >= 2
        Map<String, List<org.flowable.task.api.Task>> activeByKey = new LinkedHashMap<>();
        for (org.flowable.task.api.Task t : activeTasks) {
            activeByKey.computeIfAbsent(t.getTaskDefinitionKey(), k -> new java.util.ArrayList<>()).add(t);
        }
        Map<String, List<HistoricTaskInstance>> historicByKey = new LinkedHashMap<>();
        for (HistoricTaskInstance t : historicTasks) {
            historicByKey.computeIfAbsent(t.getTaskDefinitionKey(), k -> new java.util.ArrayList<>()).add(t);
        }

        // taskId -> 最近一次审批动作（会签元素状态判定用）
        Map<String, ApprovalAction> actionByTaskId = new HashMap<>();
        for (ApprovalRecord r : approvalRecordRepository.findByInstanceIdOrderByCreatedAtAsc(instance.getId())) {
            if (r.getTaskId() != null && r.getAction() != null) {
                actionByTaskId.put(r.getTaskId(), r.getAction());
            }
        }
        boolean instanceTerminated = instance.getStatus() == ProcessInstanceStatus.REJECTED
                || instance.getStatus() == ProcessInstanceStatus.CANCELLED;
        Map<String, String> roleNames = new HashMap<>();

        // 节点 key 取活跃与历史的并集——多实例节点整体完成后活跃任务为空，仍需展示
        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(activeByKey.keySet());
        allKeys.addAll(historicByKey.keySet());

        List<Map<String, Object>> nodes = new ArrayList<>();
        for (String key : allKeys) {
            List<org.flowable.task.api.Task> eActive = activeByKey.getOrDefault(key, List.of());
            List<HistoricTaskInstance> hist = historicByKey.getOrDefault(key, List.of());
            if (eActive.size() + hist.size() < 2) {
                continue; // 非多实例节点
            }
            int completed = 0, rejected = 0, pending = 0;
            List<Map<String, Object>> elements = new ArrayList<>();
            for (org.flowable.task.api.Task t : eActive) {
                pending++;
                String code = elementGroupCode(fpiId, t.getId(), true);
                elements.add(countersignElement(key, t.getId(), t.getAssignee(), null,
                        "PENDING", roleNameByCode(code, roleNames), code));
            }
            for (HistoricTaskInstance t : hist) {
                // 多实例已整体完成时，其子任务不会出现在活跃列表；否则活跃任务即对应元素
                boolean stillActive = eActive.stream().anyMatch(a -> a.getId().equals(t.getId()));
                if (stillActive) {
                    continue;
                }
                ApprovalAction action = actionByTaskId.get(t.getId());
                String status;
                if (action == ApprovalAction.APPROVE) {
                    status = "COMPLETED"; completed++;
                } else if (action == ApprovalAction.REJECT) {
                    status = "REJECTED"; rejected++;
                } else {
                    // 无审批记录：实例被整单终止（驳回/取消）→ TERMINATED；否则视为已完成
                    status = instanceTerminated ? "TERMINATED" : "COMPLETED";
                    if ("COMPLETED".equals(status)) {
                        completed++;
                    }
                }
                String code = elementGroupCode(fpiId, t.getId(), false);
                elements.add(countersignElement(key, t.getId(), t.getAssignee(),
                        t.getEndTime() != null ? LocalDateTime.ofInstant(t.getEndTime().toInstant(),
                                java.time.ZoneId.systemDefault()).format(TS) : null,
                        status, roleNameByCode(code, roleNames), code));
            }
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("nodeKey", key);
            node.put("nodeName", currentNodeNameFor(key, instance));
            node.put("total", elements.size());
            node.put("completed", completed);
            node.put("rejected", rejected);
            node.put("pending", pending);
            node.put("elements", elements);
            nodes.add(node);
        }
        nodes.sort((a, b) -> String.valueOf(a.get("nodeKey")).compareTo(String.valueOf(b.get("nodeKey"))));
        return nodes;
    }

    private Map<String, Object> countersignElement(String nodeKey, String taskId, String assignee,
                                                    String finishedAt, String status,
                                                    String groupName, String groupCode) {
        Map<String, Object> el = new LinkedHashMap<>();
        el.put("taskId", taskId);
        el.put("groupCode", groupCode);
        el.put("groupName", groupName != null ? groupName : (groupCode != null ? groupCode : nodeKey));
        el.put("status", status);
        el.put("assignee", assignee);
        el.put("finishedAt", finishedAt);
        return el;
    }

    /** 候选组 code → 角色名（找不到角色时返回 null，由调用方回退显示 code）。 */
    private String roleNameByCode(String code, Map<String, String> roleNames) {
        if (code == null) {
            return null;
        }
        return roleNames.computeIfAbsent(code, c -> roleRepository.findByRoleCode(c)
                .map(Role::getRoleName).orElse(null));
    }

    /**
     * 会签元素的候选组 code：元素变量存于多实例循环 execution 作用域（任务作用域查不到），
     * 故直接 join Flowable 引擎表按任务的 execution 取回。活跃查 ACT_RU_*，已结束查 ACT_HI_*。
     */
    private String elementGroupCode(String fpiId, String taskId, boolean active) {
        // 注意：Flowable 引擎表名为大写（MySQL 表名大小写敏感；H2 会折叠为大写，两库通用）
        String sql = (active
                        ? "select v.TEXT_ from ACT_RU_TASK t join ACT_RU_VARIABLE v "
                        : "select v.TEXT_ from ACT_HI_TASKINST t join ACT_HI_VARINST v ")
                + "on v.EXECUTION_ID_ = t.EXECUTION_ID_ and v.NAME_ = 'countersignGroup' "
                + "where t.ID_ = ? and t.PROC_INST_ID_ = ?";
        try {
            List<String> values = jdbcTemplate.queryForList(sql, String.class, taskId, fpiId);
            return values.isEmpty() ? null : values.get(0);
        } catch (Exception e) {
            log.debug("会签元素变量查询失败 taskId={}: {}", taskId, e.getMessage());
            return null;
        }
    }

    private String currentNodeNameFor(String nodeKey, ProcessInstance instance) {
        Map<String, String> names = bpmnXmlService.userTaskNameMap(instance.getDef().getBpmnXml());
        return names.getOrDefault(nodeKey, nodeKey);
    }

    @Transactional
    public InstanceDTO cancelInstance(Long id) {
        ProcessInstance instance = loadInstance(id);
        if (instance.getStatus() != ProcessInstanceStatus.RUNNING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅运行中的流程可取消");
        }
        User operator = authService.getCurrentUser();
        if (!instance.getInitiator().getId().equals(operator.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅发起人可取消流程");
        }
        if (instance.getFlowableInstanceId() != null
                && runtimeService.createProcessInstanceQuery()
                        .processInstanceId(instance.getFlowableInstanceId()).count() > 0) {
            runtimeService.deleteProcessInstance(instance.getFlowableInstanceId(), "发起人取消");
        }
        instance.setStatus(ProcessInstanceStatus.CANCELLED);
        instance.setCompletedAt(LocalDateTime.now());
        instance = instanceRepository.save(instance);

        recordApproval(instance, null, instance.getCurrentNode(), instance.getCurrentNode(),
                ApprovalAction.CANCEL, operator, "取消流程", instance.getCurrentNode(), null);
        notificationService.notify(instance.getInitiator(), "流程已取消",
                "流程【" + instance.getTitle() + "】已被取消。",
                NotifyType.PROCESS, RefType.PROCESS_INSTANCE, String.valueOf(instance.getId()));
        return toInstanceDto(instance);
    }

    @Transactional(readOnly = true)
    public List<InstanceDTO> listMyInstances() {
        User current = authService.getCurrentUser();
        return instanceRepository.findByInitiatorId(current.getId()).stream()
                .map(this::toInstanceDto)
                .toList();
    }

    /** 抄送给我：当前用户被抄送的实例列表（cc_record 每人每实例唯一，天然去重）。 */
    @Transactional(readOnly = true)
    public List<InstanceDTO> listCcInstances() {
        User current = authService.getCurrentUser();
        return ccRecordRepository.findByUserId(current.getId()).stream()
                .map(r -> toInstanceDto(r.getInstance()))
                .toList();
    }

    // ==================== 内部方法 ====================

    public ProcessDefinition loadDefinition(Long id) {
        return definitionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "流程模板不存在"));
    }

    public ProcessInstance loadInstance(Long id) {
        return instanceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "流程实例不存在"));
    }

    public InstanceDTO toInstanceDto(ProcessInstance instance) {
        InstanceDTO dto = new InstanceDTO();
        dto.setId(instance.getId());
        dto.setInstanceNo(instance.getInstanceNo());
        dto.setDefId(instance.getDef().getId());
        dto.setDefKey(instance.getDef().getDefKey());
        dto.setDefName(instance.getDef().getName());
        dto.setDefVersion(instance.getDefVersion());
        dto.setTitle(instance.getTitle());
        dto.setInitiatorId(instance.getInitiator().getId());
        dto.setInitiatorName(instance.getInitiator().getRealName() != null
                ? instance.getInitiator().getRealName() : instance.getInitiator().getUsername());
        dto.setBusinessType(instance.getBusinessType() != null ? instance.getBusinessType().name() : null);
        dto.setBusinessData(instance.getBusinessData());
        dto.setCurrentNode(instance.getCurrentNode());
        dto.setCurrentNodeName(currentNodeName(instance));
        dto.setStatus(instance.getStatus() != null ? instance.getStatus().name() : null);
        dto.setPriority(toUrgency(instance.getPriority()));
        dto.setSubmittedAt(instance.getSubmittedAt() != null ? instance.getSubmittedAt().format(TS) : null);
        dto.setCompletedAt(instance.getCompletedAt() != null ? instance.getCompletedAt().format(TS) : null);
        fillLastAction(dto, instance);
        return dto;
    }

    /** 最近一次非 SUBMIT 审批动作（拒绝/驳回/转办/取消）及其时间，用于结果视图与“驳回中”筛选。 */
    private void fillLastAction(InstanceDTO dto, ProcessInstance instance) {
        List<ApprovalRecord> records = approvalRecordRepository.findByInstanceIdOrderByCreatedAtAsc(instance.getId());
        for (int i = records.size() - 1; i >= 0; i--) {
            ApprovalRecord r = records.get(i);
            if (r.getAction() != null && r.getAction() != ApprovalAction.SUBMIT) {
                dto.setLastAction(r.getAction().name());
                dto.setLastActionAt(r.getCreatedAt() != null ? r.getCreatedAt().format(TS) : null);
                return;
            }
        }
    }

    /** 紧急度映射：LOW/NORMAL=0，HIGH=1，URGENT=2。 */
    public static Integer toUrgency(com.oa.enums.Priority priority) {
        if (priority == null) {
            return 0;
        }
        return switch (priority) {
            case HIGH -> 1;
            case URGENT -> 2;
            default -> 0;
        };
    }

    private String currentNodeName(ProcessInstance instance) {
        if (instance.getCurrentNode() == null) {
            return null;
        }
        Map<String, String> names = bpmnXmlService.userTaskNameMap(instance.getDef().getBpmnXml());
        if (names.containsKey(instance.getCurrentNode())) {
            return names.get(instance.getCurrentNode());
        }
        if ("start".equals(instance.getCurrentNode())) {
            return "已发起";
        }
        if ("end".equals(instance.getCurrentNode())) {
            return "已结束";
        }
        return instance.getCurrentNode();
    }

    public List<Map<String, Object>> toRecordDtos(List<ApprovalRecord> records) {
        return records.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("taskId", r.getTaskId());
            m.put("nodeKey", r.getNodeKey());
            m.put("nodeName", r.getNodeName());
            m.put("action", r.getAction() != null ? r.getAction().name() : null);
            m.put("operatorName", r.getOperatorName());
            m.put("comment", r.getComment());
            m.put("fromNode", r.getFromNode());
            m.put("toNode", r.getToNode());
            m.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().format(TS) : null);
            return m;
        }).toList();
    }

    public void recordApproval(ProcessInstance instance, String flowableTaskId, String nodeKey, String nodeName,
                                ApprovalAction action, User operator, String comment,
                                String fromNode, String toNode) {
        ApprovalRecord record = new ApprovalRecord();
        record.setInstance(instance);
        record.setTaskId(flowableTaskId);
        record.setNodeKey(nodeKey);
        record.setNodeName(nodeName);
        record.setAction(action);
        record.setOperator(operator);
        record.setOperatorName(operator.getRealName() != null ? operator.getRealName() : operator.getUsername());
        record.setComment(comment);
        record.setFromNode(fromNode);
        record.setToNode(toNode);
        approvalRecordRepository.save(record);
    }

    /** 若流程已在 Flowable 侧结束，同步业务实例状态（兜底，正常由 TaskService 维护）。 */
    public void syncCompletionIfEnded(ProcessInstance instance) {
        if (instance.getStatus() != ProcessInstanceStatus.RUNNING || instance.getFlowableInstanceId() == null) {
            return;
        }
        boolean running = runtimeService.createProcessInstanceQuery()
                .processInstanceId(instance.getFlowableInstanceId()).count() > 0;
        if (running) {
            return;
        }
        instance.setStatus(ProcessInstanceStatus.COMPLETED);
        instance.setCompletedAt(LocalDateTime.now());
        instance.setCurrentNode("end");
        instanceRepository.save(instance);
        notificationService.notify(instance.getInitiator(), "流程已审批完成",
                "流程【" + instance.getTitle() + "】已全部审批通过。",
                NotifyType.PROCESS, RefType.PROCESS_INSTANCE, String.valueOf(instance.getId()));
    }

    /**
     * 流程动作（发起/通过/驳回回退）后同步业务实例：
     * 更新当前节点，通知新的待办人；若流程已结束则置 COMPLETED。
     */
    @Transactional
    public void syncInstanceAfterAction(ProcessInstance instance) {
        if (instance.getFlowableInstanceId() == null) {
            return;
        }
        boolean running = runtimeService.createProcessInstanceQuery()
                .processInstanceId(instance.getFlowableInstanceId()).count() > 0;
        if (!running) {
            syncCompletionIfEnded(instance);
            return;
        }
        List<org.flowable.task.api.Task> activeTasks = flowableTaskService.createTaskQuery()
                .processInstanceId(instance.getFlowableInstanceId())
                .active()
                .list();
        if (!activeTasks.isEmpty()) {
            org.flowable.task.api.Task first = activeTasks.get(0);
            instance.setCurrentNode(first.getTaskDefinitionKey());
            instanceRepository.save(instance);
            for (org.flowable.task.api.Task t : activeTasks) {
                notificationService.notifyTaskHolders(t, "您有新的待办任务",
                        "流程【" + instance.getTitle() + "】待您审批。");
            }
        }
    }

    /** 已完成的活动节点 id（userTask + startEvent + endEvent），用于流程图绿色高亮。 */
    @Transactional(readOnly = true)
    public List<String> completedActivityIds(ProcessInstance instance) {
        if (instance.getFlowableInstanceId() == null) {
            return List.of();
        }
        return historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(instance.getFlowableInstanceId())
                .finished()
                .list().stream()
                .filter(a -> "userTask".equals(a.getActivityType())
                        || "startEvent".equals(a.getActivityType())
                        || "endEvent".equals(a.getActivityType()))
                .map(org.flowable.engine.history.HistoricActivityInstance::getActivityId)
                .distinct()
                .toList();
    }

    /** 当前活动节点 id，用于流程图蓝色高亮。 */
    @Transactional(readOnly = true)
    public List<String> currentActivityIds(ProcessInstance instance) {
        if (instance.getFlowableInstanceId() == null
                || instance.getStatus() != ProcessInstanceStatus.RUNNING) {
            return List.of();
        }
        return new java.util.ArrayList<>(runtimeService.getActiveActivityIds(instance.getFlowableInstanceId()));
    }

    private Map<String, Object> parseVariables(String businessData) {
        if (businessData == null || businessData.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(businessData, Map.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "业务数据不是合法的 JSON 对象");
        }
    }

    private DefinitionDTO toDto(ProcessDefinition def, boolean withXml) {
        DefinitionDTO dto = new DefinitionDTO();
        dto.setId(def.getId());
        dto.setDefKey(def.getDefKey());
        dto.setName(def.getName());
        dto.setVersion(def.getVersion());
        dto.setCategory(def.getCategory());
        dto.setDescription(def.getDescription());
        dto.setFormConfig(def.getFormConfig());
        dto.setStatus(def.getStatus() != null ? def.getStatus().name() : null);
        dto.setCreatorName(def.getCreator() != null && def.getCreator().getRealName() != null
                ? def.getCreator().getRealName() : (def.getCreator() != null ? def.getCreator().getUsername() : null));
        dto.setPublishedAt(def.getPublishedAt() != null ? def.getPublishedAt().format(TS) : null);
        if (withXml) {
            dto.setBpmnXml(def.getBpmnXml());
        }
        return dto;
    }

    private String generateInstanceNo() {
        return "PI" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
