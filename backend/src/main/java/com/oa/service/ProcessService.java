package com.oa.service;

import com.oa.dto.ProcessDefinitionRequest;
import com.oa.dto.ProcessStartRequest;
import com.oa.entity.ApprovalRecord;
import com.oa.entity.ProcessDefinition;
import com.oa.entity.ProcessInstance;
import com.oa.entity.Task;
import com.oa.entity.User;
import com.oa.enums.ApprovalAction;
import com.oa.enums.BusinessType;
import com.oa.enums.Priority;
import com.oa.enums.ProcessDefinitionStatus;
import com.oa.enums.ProcessInstanceStatus;
import com.oa.repository.ApprovalRecordRepository;
import com.oa.repository.ProcessDefinitionRepository;
import com.oa.repository.ProcessInstanceRepository;
import com.oa.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class ProcessService {

    private final ProcessDefinitionRepository definitionRepository;
    private final ProcessInstanceRepository instanceRepository;
    private final TaskRepository taskRepository;
    private final ApprovalRecordRepository approvalRecordRepository;
    private final AuthService authService;
    private final WorkflowEngine workflowEngine;

    @Transactional
    public ProcessDefinition createDefinition(ProcessDefinitionRequest req) {
        User creator = authService.getCurrentUser();
        ProcessDefinition def = new ProcessDefinition();
        def.setDefKey(req.getDefKey());
        def.setName(req.getName());
        def.setCategory(req.getCategory());
        def.setFormConfig(req.getFormConfig());
        def.setNodeJson(req.getNodeJson());
        def.setVersion(1);
        def.setStatus(ProcessDefinitionStatus.PUBLISHED);
        def.setCreator(creator);
        def.setPublishedAt(LocalDateTime.now());
        return definitionRepository.save(def);
    }

    @Transactional(readOnly = true)
    public List<ProcessDefinition> listDefinitions() {
        return definitionRepository.findAll();
    }

    @Transactional
    public ProcessInstance startInstance(ProcessStartRequest req) {
        ProcessDefinition def = definitionRepository.findById(req.getDefId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "流程定义不存在"));
        if (def.getStatus() != ProcessDefinitionStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "流程定义未发布，无法发起");
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

        String firstNodeKey = workflowEngine.getStartNextNode(def.getNodeJson());
        instance.setCurrentNode(firstNodeKey);
        instance = instanceRepository.save(instance);

        workflowEngine.recordApproval(instance, null, "start", ApprovalAction.SUBMIT,
                initiator, "发起流程", "start", firstNodeKey);
        workflowEngine.createTaskForNode(instance, def.getNodeJson(), firstNodeKey);

        return instance;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getInstance(Long id) {
        ProcessInstance instance = instanceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "流程实例不存在"));
        List<Task> tasks = taskRepository.findByInstanceId(id);
        List<ApprovalRecord> records = approvalRecordRepository.findByInstanceIdOrderByCreatedAtAsc(id);
        Map<String, Object> result = new HashMap<>();
        result.put("instance", instance);
        result.put("tasks", tasks);
        result.put("approvalRecords", records);
        return result;
    }

    @Transactional
    public ProcessInstance cancelInstance(Long id) {
        ProcessInstance instance = instanceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "流程实例不存在"));
        if (instance.getStatus() != ProcessInstanceStatus.RUNNING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅运行中的流程可取消");
        }
        User operator = authService.getCurrentUser();
        instance.setStatus(ProcessInstanceStatus.CANCELLED);
        instance.setCompletedAt(LocalDateTime.now());
        instance = instanceRepository.save(instance);

        workflowEngine.recordApproval(instance, null, instance.getCurrentNode(),
                ApprovalAction.CANCEL, operator, "取消流程", instance.getCurrentNode(), null);
        return instance;
    }

    @Transactional(readOnly = true)
    public List<ProcessInstance> listMyInstances() {
        User current = authService.getCurrentUser();
        return instanceRepository.findByInitiatorId(current.getId());
    }

    private String generateInstanceNo() {
        return "PI" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
