package com.oa.service;

import com.oa.dto.TaskCompleteRequest;
import com.oa.dto.TaskRejectRequest;
import com.oa.dto.TaskTransferRequest;
import com.oa.entity.ApprovalRecord;
import com.oa.entity.ProcessInstance;
import com.oa.entity.Task;
import com.oa.entity.User;
import com.oa.enums.ApprovalAction;
import com.oa.enums.NotifyType;
import com.oa.enums.ProcessInstanceStatus;
import com.oa.enums.RefType;
import com.oa.enums.TaskStatus;
import com.oa.repository.ApprovalRecordRepository;
import com.oa.repository.ProcessInstanceRepository;
import com.oa.repository.TaskRepository;
import com.oa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ApprovalRecordRepository approvalRecordRepository;
    private final ProcessInstanceRepository instanceRepository;
    private final AuthService authService;
    private final WorkflowEngine workflowEngine;

    @Transactional(readOnly = true)
    public List<Task> getMyTasks(TaskStatus status) {
        User current = authService.getCurrentUser();
        if (status != null) {
            return taskRepository.findByAssigneeIdAndStatus(current.getId(), status);
        }
        return taskRepository.findByAssigneeIdAndStatusIn(current.getId(),
                List.of(TaskStatus.PENDING, TaskStatus.CLAIMED));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getTaskDetail(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        List<ApprovalRecord> records = approvalRecordRepository.findByTaskId(id);
        Map<String, Object> result = new HashMap<>();
        result.put("task", task);
        result.put("instance", task.getInstance());
        result.put("approvalRecords", records);
        return result;
    }

    @Transactional
    public Task completeTask(Long id, TaskCompleteRequest req) {
        Task task = loadOwnedTask(id);
        User operator = authService.getCurrentUser();
        ProcessInstance instance = task.getInstance();

        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now());
        if (req.getComment() != null) {
            task.setComment(req.getComment());
        }
        task = taskRepository.save(task);

        workflowEngine.recordApproval(instance, task, task.getNodeKey(),
                ApprovalAction.APPROVE, operator, req.getComment(), task.getNodeKey(), null);

        String nodeJson = instance.getDef().getNodeJson();
        String nextNodeKey = workflowEngine.resolveNextActivityNode(nodeJson, task.getNodeKey(), instance.getBusinessData());

        if (nextNodeKey == null || workflowEngine.isEndNode(nodeJson, nextNodeKey)) {
            instance.setStatus(ProcessInstanceStatus.COMPLETED);
            instance.setCompletedAt(LocalDateTime.now());
            instance.setCurrentNode("end");
            instanceRepository.save(instance);
            workflowEngine.notify(instance.getInitiator(), "流程已审批完成",
                    "流程【" + instance.getTitle() + "】已全部审批通过。",
                    NotifyType.PROCESS, RefType.PROCESS_INSTANCE, String.valueOf(instance.getId()));
        } else {
            instance.setCurrentNode(nextNodeKey);
            instanceRepository.save(instance);
            workflowEngine.createTaskForNode(instance, nodeJson, nextNodeKey);
        }
        return task;
    }

    @Transactional
    public Task rejectTask(Long id, TaskRejectRequest req) {
        Task task = loadOwnedTask(id);
        User operator = authService.getCurrentUser();
        ProcessInstance instance = task.getInstance();

        task.setStatus(TaskStatus.REJECTED);
        task.setCompletedAt(LocalDateTime.now());
        if (req.getComment() != null) {
            task.setComment(req.getComment());
        }
        task = taskRepository.save(task);

        String toNode = req.getToNodeKey();
        workflowEngine.recordApproval(instance, task, task.getNodeKey(),
                ApprovalAction.REJECT, operator, req.getComment(), task.getNodeKey(), toNode);

        if (toNode != null && !toNode.isBlank()) {
            instance.setCurrentNode(toNode);
            instanceRepository.save(instance);
            workflowEngine.createTaskForNode(instance, instance.getDef().getNodeJson(), toNode);
        } else {
            instance.setStatus(ProcessInstanceStatus.REJECTED);
            instance.setCompletedAt(LocalDateTime.now());
            instanceRepository.save(instance);
            workflowEngine.notify(instance.getInitiator(), "流程被驳回",
                    "流程【" + instance.getTitle() + "】已被驳回。",
                    NotifyType.PROCESS, RefType.PROCESS_INSTANCE, String.valueOf(instance.getId()));
        }
        return task;
    }

    @Transactional
    public Task transferTask(Long id, TaskTransferRequest req) {
        Task task = loadOwnedTask(id);
        User operator = authService.getCurrentUser();
        User target = userRepository.findById(req.getToUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "目标用户不存在"));

        task.setDelegateFrom(task.getAssignee());
        task.setAssignee(target);
        if (req.getComment() != null) {
            task.setComment(req.getComment());
        }
        task = taskRepository.save(task);

        workflowEngine.recordApproval(task.getInstance(), task, task.getNodeKey(),
                ApprovalAction.TRANSFER, operator, req.getComment(), task.getNodeKey(), task.getNodeKey());
        workflowEngine.notify(target, "您收到一条转办任务",
                "任务【" + task.getNodeName() + "】已转办给您处理。",
                NotifyType.TASK, RefType.TASK, String.valueOf(task.getId()));
        return task;
    }

    private Task loadOwnedTask(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        User current = authService.getCurrentUser();
        if (!task.getAssignee().getId().equals(current.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权操作该任务");
        }
        if (task.getStatus() != TaskStatus.PENDING && task.getStatus() != TaskStatus.CLAIMED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前任务状态不允许此操作");
        }
        return task;
    }
}
