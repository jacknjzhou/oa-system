package com.oa.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oa.entity.ApprovalRecord;
import com.oa.entity.Notification;
import com.oa.entity.ProcessInstance;
import com.oa.entity.Task;
import com.oa.entity.User;
import com.oa.enums.ApprovalAction;
import com.oa.enums.NotifyType;
import com.oa.enums.RefType;
import com.oa.enums.TaskStatus;
import com.oa.enums.TaskType;
import com.oa.repository.ApprovalRecordRepository;
import com.oa.repository.NotificationRepository;
import com.oa.repository.TaskRepository;
import com.oa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 轻量级流程引擎：解析流程定义 nodeJson，驱动节点流转、任务生成与通知。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowEngine {

    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final NotificationRepository notificationRepository;
    private final ApprovalRecordRepository approvalRecordRepository;

    public JsonNode parse(String nodeJson) {
        try {
            return objectMapper.readTree(nodeJson);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "流程节点配置解析失败");
        }
    }

    public JsonNode findNode(JsonNode root, String key) {
        JsonNode nodes = root.get("nodes");
        if (nodes == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "流程节点配置缺失");
        }
        for (JsonNode n : nodes) {
            if (n.get("key").asText().equals(key)) {
                return n;
            }
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "流程节点不存在: " + key);
    }

    public String getStartNextNode(String nodeJson) {
        JsonNode root = parse(nodeJson);
        for (JsonNode n : root.get("nodes")) {
            if ("start".equals(n.get("type").asText())) {
                return n.get("next").asText();
            }
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "流程缺少开始节点");
    }

    public boolean isEndNode(String nodeJson, String nodeKey) {
        JsonNode node = findNode(parse(nodeJson), nodeKey);
        return "end".equals(node.get("type").asText());
    }

    public String getNextNodeKey(String nodeJson, String currentKey, String businessData) {
        JsonNode root = parse(nodeJson);
        JsonNode node = findNode(root, currentKey);
        String nodeType = node.has("type") ? node.get("type").asText() : "approval";
        if ("condition".equals(nodeType)) {
            Map<String, Object> data = parseBusinessData(businessData);
            JsonNode conditions = node.get("conditions");
            String defaultNext = null;
            for (JsonNode c : conditions) {
                String expr = c.get("expr").asText();
                String next = c.get("next").asText();
                if ("default".equalsIgnoreCase(expr)) {
                    defaultNext = next;
                    continue;
                }
                if (evaluate(expr, data)) {
                    return next;
                }
            }
            return defaultNext;
        }
        return node.has("next") ? node.get("next").asText() : null;
    }

    /**
     * 从当前节点出发，计算下一个需要人工处理的“活动节点”key。
     * 会自动跳过条件网关节点（即时求值），直到命中审批/通知/结束等活动节点。
     */
    public String resolveNextActivityNode(String nodeJson, String currentKey, String businessData) {
        String next = getNextNodeKey(nodeJson, currentKey, businessData);
        int guard = 0;
        while (next != null && isConditionNode(nodeJson, next)) {
            if (++guard > 50) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "流程条件节点存在死循环");
            }
            next = getNextNodeKey(nodeJson, next, businessData);
        }
        return next;
    }

    public boolean isConditionNode(String nodeJson, String key) {
        JsonNode node = findNode(parse(nodeJson), key);
        return "condition".equals(node.has("type") ? node.get("type").asText() : "approval");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseBusinessData(String businessData) {
        if (businessData == null || businessData.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(businessData, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private boolean evaluate(String expr, Map<String, Object> data) {
        Matcher m = Pattern.compile("(\\w+)\\s*(>=|<=|==|!=|>|<)\\s*([0-9.]+)").matcher(expr);
        if (!m.matches()) {
            return false;
        }
        String field = m.group(1);
        String op = m.group(2);
        double value = Double.parseDouble(m.group(3));
        Object fv = data.get(field);
        if (fv == null) {
            return false;
        }
        double actual;
        if (fv instanceof Number n) {
            actual = n.doubleValue();
        } else {
            try {
                actual = Double.parseDouble(fv.toString());
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return switch (op) {
            case ">" -> actual > value;
            case "<" -> actual < value;
            case ">=" -> actual >= value;
            case "<=" -> actual <= value;
            case "==" -> actual == value;
            case "!=" -> actual != value;
            default -> false;
        };
    }

    @Transactional
    public Task createTaskForNode(ProcessInstance instance, String nodeJson, String nodeKey) {
        JsonNode node = findNode(parse(nodeJson), nodeKey);
        String nodeType = node.has("type") ? node.get("type").asText() : "approval";
        String nodeName = node.has("name") ? node.get("name").asText() : nodeKey;
        User assignee = resolveAssignee(node);

        Task task = new Task();
        task.setInstance(instance);
        task.setNodeKey(nodeKey);
        task.setNodeName(nodeName);
        task.setAssignee(assignee);
        task.setTaskType("notify".equals(nodeType) ? TaskType.NOTIFY : TaskType.APPROVAL);
        task.setStatus(TaskStatus.PENDING);
        task = taskRepository.save(task);

        notify(assignee, "您有新的待办任务", nodeName, NotifyType.TASK, RefType.TASK, String.valueOf(task.getId()));
        return task;
    }

    @Transactional
    public ApprovalRecord recordApproval(ProcessInstance instance, Task task, String nodeKey,
                                         ApprovalAction action, User operator,
                                         String comment, String fromNode, String toNode) {
        ApprovalRecord record = new ApprovalRecord();
        record.setInstance(instance);
        record.setTask(task);
        record.setNodeKey(nodeKey);
        record.setAction(action);
        record.setOperator(operator);
        record.setOperatorName(operator.getRealName() != null ? operator.getRealName() : operator.getUsername());
        record.setComment(comment);
        record.setFromNode(fromNode);
        record.setToNode(toNode);
        return approvalRecordRepository.save(record);
    }

    @Transactional
    public void notify(User user, String title, String content, NotifyType type, RefType refType, String refId) {
        Notification n = new Notification();
        n.setUser(user);
        n.setTitle(title);
        n.setContent(content);
        n.setNotifyType(type);
        n.setRefType(refType);
        n.setRefId(refId);
        n.setIsRead(false);
        notificationRepository.save(n);
    }

    private User resolveAssignee(JsonNode node) {
        String role = node.has("assigneeRole") ? node.get("assigneeRole").asText() : null;
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "节点未配置审批人角色");
        }
        List<User> users = userRepository.findByRolesRoleCode(role);
        if (users.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "未找到角色 [" + role + "] 的可用审批人");
        }
        return users.get(0);
    }
}
