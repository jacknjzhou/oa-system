package com.oa.service;

import com.oa.entity.Notification;
import com.oa.entity.User;
import com.oa.enums.NotifyType;
import com.oa.enums.RefType;
import com.oa.repository.NotificationRepository;
import com.oa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 站内通知：按用户 + 引用去重，避免同一任务重复提醒。
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final org.flowable.engine.TaskService taskService;

    @Transactional
    public void notify(User user, String title, String content, NotifyType type, RefType refType, String refId) {
        if (user == null) {
            return;
        }
        if (refType != null && refId != null
                && notificationRepository.existsByUserIdAndRefTypeAndRefId(user.getId(), refType, refId)) {
            return;
        }
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

    /**
     * 通知一个 Flowable 任务的全部潜在处理人（已认领人 + 候选角色下的用户）。
     */
    @Transactional
    public void notifyTaskHolders(Task task, String title, String content) {
        if (task == null) {
            return;
        }
        String refId = task.getId();
        if (task.getAssignee() != null) {
            userRepository.findByUsername(task.getAssignee()).ifPresent(u ->
                    notify(u, title, content, NotifyType.TASK, RefType.TASK, refId));
            return;
        }
        List<String> groupIds = taskService.getIdentityLinksForTask(task.getId()).stream()
                .map(IdentityLink::getGroupId)
                .filter(g -> g != null && !g.isBlank())
                .distinct()
                .collect(Collectors.toList());
        for (String role : groupIds) {
            for (User u : userRepository.findByRolesRoleCode(role)) {
                notify(u, title, content, NotifyType.TASK, RefType.TASK, refId);
            }
        }
    }
}
