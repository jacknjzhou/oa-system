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
import com.oa.repository.ApprovalRecordRepository;
import com.oa.repository.ProcessDefinitionRepository;
import com.oa.repository.ProcessInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final AuthService authService;
    private final NotificationService notificationService;
    private final BpmnXmlService bpmnXmlService;
    private final ObjectMapper objectMapper;
    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final org.flowable.engine.TaskService flowableTaskService;
    private final org.flowable.engine.HistoryService historyService;

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
        instance.setSubmittedAt(LocalDateTime.now());
        instance.setStatus(ProcessInstanceStatus.RUNNING);
        instance = instanceRepository.save(instance);

        Map<String, Object> variables = parseVariables(req.getBusinessData());
        org.flowable.engine.runtime.ProcessInstance flowableInstance = runtimeService
                .startProcessInstanceByKey(def.getDefKey(), instance.getInstanceNo(), variables);
        instance.setFlowableInstanceId(flowableInstance.getId());
        instance = instanceRepository.save(instance);

        recordApproval(instance, null, "start", "发起",
                ApprovalAction.SUBMIT, initiator, "发起流程", "start", null);

        syncInstanceAfterAction(instance);

        return toInstanceDto(instance);
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
        return result;
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
        return dto;
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
