package com.oa.service;

import com.oa.dto.TaskDTO;
import com.oa.dto.TaskCompleteRequest;
import com.oa.dto.TaskRejectRequest;
import com.oa.dto.TaskTransferRequest;
import com.oa.entity.ProcessInstance;
import com.oa.entity.User;
import com.oa.enums.ApprovalAction;
import com.oa.enums.NotifyType;
import com.oa.enums.ProcessInstanceStatus;
import com.oa.enums.RefType;
import com.oa.repository.ApprovalRecordRepository;
import com.oa.repository.ProcessInstanceRepository;
import com.oa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 审批任务服务：待办/已办查询、审批通过、驳回、转办，全部基于 Flowable 引擎。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final org.flowable.engine.TaskService flowableTaskService;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final ProcessInstanceRepository instanceRepository;
    private final ApprovalRecordRepository approvalRecordRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final ProcessService processService;
    private final NotificationService notificationService;
    private final BpmnXmlService bpmnXmlService;

    // ==================== 查询 ====================

    /** 我的待办：已分配给我 + 我的角色为候选组的任务。 */
    @Transactional(readOnly = true)
    public List<TaskDTO> getMyTasks() {
        User current = authService.getCurrentUser();
        LinkedHashSet<Task> merged = new LinkedHashSet<>();
        merged.addAll(flowableTaskService.createTaskQuery()
                .taskAssignee(current.getUsername())
                .active()
                .orderByTaskCreateTime().desc()
                .list());
        List<String> roleCodes = List.copyOf(current.getRoleCodes());
        if (!roleCodes.isEmpty()) {
            merged.addAll(flowableTaskService.createTaskQuery()
                    .taskCandidateGroupIn(roleCodes)
                    .active()
                    .orderByTaskCreateTime().desc()
                    .list());
        }
        List<TaskDTO> result = new ArrayList<>();
        for (Task task : merged) {
            result.add(toDto(task, "PENDING", null));
        }
        result.sort((a, b) -> String.valueOf(b.getCreateTime()).compareTo(String.valueOf(a.getCreateTime())));
        return result;
    }

    /** 我的已办：已办结的历史任务。 */
    @Transactional(readOnly = true)
    public List<TaskDTO> getMyDoneTasks() {
        User current = authService.getCurrentUser();
        List<HistoricTaskInstance> history = historyService.createHistoricTaskInstanceQuery()
                .taskAssignee(current.getUsername())
                .finished()
                .orderByHistoricTaskInstanceEndTime().desc()
                .list();
        return history.stream()
                .map(t -> toDto(t))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getTaskDetail(String id) {
        TaskDTO taskDto;
        ProcessInstance instance;
        List<Map<String, Object>> records;

        Task task = flowableTaskService.createTaskQuery().taskId(id).singleResult();
        if (task != null) {
            instance = loadInstanceByFlowableId(task.getProcessInstanceId());
            taskDto = toDto(task, "PENDING", null);
        } else {
            HistoricTaskInstance hist = historyService.createHistoricTaskInstanceQuery().taskId(id).singleResult();
            if (hist == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
            }
            instance = loadInstanceByFlowableId(hist.getProcessInstanceId());
            taskDto = toDto(hist);
        }
        records = processService.toRecordDtos(
                approvalRecordRepository.findByInstanceIdOrderByCreatedAtAsc(instance.getId()));
        return buildDetail(taskDto, instance, records);
    }

    private Map<String, Object> buildDetail(TaskDTO taskDto, ProcessInstance instance,
                                            List<Map<String, Object>> records) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("task", taskDto);
        result.put("instance", processService.toInstanceDto(instance));
        result.put("bpmnXml", instance.getDef().getBpmnXml());
        result.put("completedActivityIds", processService.completedActivityIds(instance));
        result.put("currentActivityIds", processService.currentActivityIds(instance));
        result.put("approvalRecords", records);
        return result;
    }

    // ==================== 审批动作 ====================

    /** 审批通过。 */
    @Transactional
    public TaskDTO completeTask(String id, TaskCompleteRequest req) {
        Task task = loadOperableTask(id);
        User operator = authService.getCurrentUser();
        ProcessInstance instance = loadInstanceByFlowableId(task.getProcessInstanceId());

        processService.recordApproval(instance, task.getId(), task.getTaskDefinitionKey(), task.getName(),
                ApprovalAction.APPROVE, operator, req.getComment(), task.getTaskDefinitionKey(), null);

        if (task.getAssignee() == null) {
            flowableTaskService.claim(task.getId(), operator.getUsername());
        }
        flowableTaskService.complete(task.getId());

        processService.syncInstanceAfterAction(instance);
        return toDtoFromHistory(id);
    }

    /** 驳回：可指定回到的目标节点（BPMN userTask id），为空则整单驳回。 */
    @Transactional
    public TaskDTO rejectTask(String id, TaskRejectRequest req) {
        Task task = loadOperableTask(id);
        User operator = authService.getCurrentUser();
        ProcessInstance instance = loadInstanceByFlowableId(task.getProcessInstanceId());
        String toNodeKey = req.getToNodeKey();

        if (toNodeKey != null && !toNodeKey.isBlank()) {
            boolean valid = bpmnXmlService.extractUserTasks(instance.getDef().getBpmnXml()).stream()
                    .anyMatch(t -> t.id().equals(toNodeKey));
            if (!valid) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "驳回目标节点不存在: " + toNodeKey);
            }
        }

        processService.recordApproval(instance, task.getId(), task.getTaskDefinitionKey(), task.getName(),
                ApprovalAction.REJECT, operator, req.getComment(), task.getTaskDefinitionKey(), toNodeKey);

        if (toNodeKey != null && !toNodeKey.isBlank()) {
            runtimeService.createChangeActivityStateBuilder()
                    .processInstanceId(task.getProcessInstanceId())
                    .moveActivityIdTo(task.getTaskDefinitionKey(), toNodeKey)
                    .changeState();
            processService.syncInstanceAfterAction(instance);
        } else {
            runtimeService.deleteProcessInstance(task.getProcessInstanceId(),
                    "驳回: " + (req.getComment() != null ? req.getComment() : ""));
            instance.setStatus(ProcessInstanceStatus.REJECTED);
            instance.setCompletedAt(LocalDateTime.now());
            instanceRepository.save(instance);
            notificationService.notify(instance.getInitiator(), "流程被驳回",
                    "流程【" + instance.getTitle() + "】已被驳回。",
                    NotifyType.PROCESS, RefType.PROCESS_INSTANCE, String.valueOf(instance.getId()));
        }
        return toDtoFromHistory(id);
    }

    /** 转办：改派给其他用户。 */
    @Transactional
    public TaskDTO transferTask(String id, TaskTransferRequest req) {
        Task task = loadOperableTask(id);
        User operator = authService.getCurrentUser();
        User target = userRepository.findById(req.getToUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "目标用户不存在"));
        ProcessInstance instance = loadInstanceByFlowableId(task.getProcessInstanceId());

        flowableTaskService.setAssignee(task.getId(), target.getUsername());

        processService.recordApproval(instance, task.getId(), task.getTaskDefinitionKey(), task.getName(),
                ApprovalAction.TRANSFER, operator, req.getComment(), task.getTaskDefinitionKey(),
                task.getTaskDefinitionKey());
        notificationService.notify(target, "您收到一条转办任务",
                "任务【" + task.getName() + "】已转办给您处理。",
                NotifyType.TASK, RefType.TASK, task.getId());

        Task updated = flowableTaskService.createTaskQuery().taskId(id).singleResult();
        return toDto(updated != null ? updated : task, "PENDING", req.getComment());
    }

    // ==================== 内部方法 ====================

    private ProcessInstance loadInstanceByFlowableId(String flowableInstanceId) {
        return instanceRepository.findByFlowableInstanceId(flowableInstanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "任务对应的流程实例不存在"));
    }

    private Task loadOperableTask(String id) {
        Task task = flowableTaskService.createTaskQuery().taskId(id).singleResult();
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在或已办结");
        }
        User current = authService.getCurrentUser();
        if (task.getAssignee() != null) {
            if (!task.getAssignee().equals(current.getUsername())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权操作该任务");
            }
            return task;
        }
        Set<String> groups = flowableTaskService.getIdentityLinksForTask(task.getId()).stream()
                .map(IdentityLink::getGroupId)
                .filter(g -> g != null && !g.isBlank())
                .collect(Collectors.toSet());
        boolean candidate = current.getRoleCodes().stream().anyMatch(groups::contains);
        if (!candidate) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权操作该任务");
        }
        return task;
    }

    private TaskDTO toDtoFromHistory(String taskId) {
        HistoricTaskInstance hist = historyService.createHistoricTaskInstanceQuery()
                .taskId(taskId).singleResult();
        return hist != null ? toDto(hist) : null;
    }

    private TaskDTO toDto(Task task, String status, String comment) {
        ProcessInstance instance = loadInstanceByFlowableId(task.getProcessInstanceId());
        TaskDTO dto = new TaskDTO();
        fillCommon(dto, instance, task.getId(), task.getTaskDefinitionKey(), task.getName());
        dto.setAssignee(task.getAssignee());
        dto.setCandidateRoles(candidateRoles(task.getId()));
        dto.setCreateTime(task.getCreateTime() != null
                ? LocalDateTime.ofInstant(task.getCreateTime().toInstant(), ZoneId.systemDefault()).format(TS) : null);
        dto.setEndTime(null);
        dto.setStatus(status);
        dto.setComment(comment);
        return dto;
    }

    private TaskDTO toDto(HistoricTaskInstance hist) {
        ProcessInstance instance = loadInstanceByFlowableId(hist.getProcessInstanceId());
        String status = instance.getStatus() == ProcessInstanceStatus.REJECTED
                && instance.getCurrentNode() != null
                && instance.getCurrentNode().equals(hist.getTaskDefinitionKey())
                ? "REJECTED" : "COMPLETED";
        TaskDTO dto = new TaskDTO();
        fillCommon(dto, instance, hist.getId(), hist.getTaskDefinitionKey(), hist.getName());
        dto.setAssignee(hist.getAssignee());
        dto.setCandidateRoles(List.of());
        dto.setCreateTime(hist.getStartTime() != null
                ? LocalDateTime.ofInstant(hist.getStartTime().toInstant(), ZoneId.systemDefault()).format(TS) : null);
        dto.setEndTime(hist.getEndTime() != null
                ? LocalDateTime.ofInstant(hist.getEndTime().toInstant(), ZoneId.systemDefault()).format(TS) : null);
        dto.setStatus(status);
        return dto;
    }

    private void fillCommon(TaskDTO dto, ProcessInstance instance, String taskId, String nodeKey, String nodeName) {
        dto.setId(taskId);
        dto.setInstanceId(instance.getId());
        dto.setTitle(instance.getTitle());
        dto.setDefKey(instance.getDef().getDefKey());
        dto.setDefName(instance.getDef().getName());
        dto.setNodeKey(nodeKey);
        dto.setNodeName(nodeName);
        dto.setPriority(ProcessService.toUrgency(instance.getPriority()));
        dto.setInitiatorName(instance.getInitiator().getRealName() != null
                ? instance.getInitiator().getRealName() : instance.getInitiator().getUsername());
    }

    private List<String> candidateRoles(String taskId) {
        return flowableTaskService.getIdentityLinksForTask(taskId).stream()
                .map(IdentityLink::getGroupId)
                .filter(g -> g != null && !g.isBlank())
                .distinct()
                .toList();
    }
}
