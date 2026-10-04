package com.oa.service;

import com.oa.dto.NotificationDTO;
import com.oa.entity.Notification;
import com.oa.entity.User;
import com.oa.enums.NotifyType;
import com.oa.enums.RefType;
import com.oa.repository.NotificationRepository;
import com.oa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 站内通知：写侧按用户 + 引用去重（避免同一任务重复提醒），读侧支撑铃铛角标与通知中心。
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final int LIST_LIMIT_MAX = 50;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
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

    // ==================== 读侧：铃铛角标 / 通知中心 ====================

    /** 我的通知列表：未读优先，其次时间倒序，最多 {@code limit} 条（上限 50）。 */
    @Transactional(readOnly = true)
    public List<NotificationDTO> listMine(int limit) {
        User current = authService.getCurrentUser();
        int size = Math.max(1, Math.min(limit, LIST_LIMIT_MAX));
        PageRequest page = PageRequest.of(0, size,
                Sort.by(Sort.Order.asc("isRead"), Sort.Order.desc("createdAt")));
        return notificationRepository.findByUserId(current.getId(), page).getContent().stream()
                .map(this::toDto)
                .toList();
    }

    /** 我的未读通知数（铃铛角标）。 */
    @Transactional(readOnly = true)
    public long countUnread() {
        User current = authService.getCurrentUser();
        return notificationRepository.countByUserIdAndIsReadFalse(current.getId());
    }

    /** 标记单条已读（仅本人可操作）。 */
    @Transactional
    public void markRead(Long id) {
        User current = authService.getCurrentUser();
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "通知不存在"));
        if (n.getUser() == null || !n.getUser().getId().equals(current.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权操作该通知");
        }
        if (!Boolean.TRUE.equals(n.getIsRead())) {
            n.setIsRead(true);
            n.setReadAt(LocalDateTime.now());
            notificationRepository.save(n);
        }
    }

    /** 我的全部通知标记已读。 */
    @Transactional
    public void markAllRead() {
        User current = authService.getCurrentUser();
        LocalDateTime now = LocalDateTime.now();
        for (Notification n : notificationRepository.findByUserIdAndIsRead(current.getId(), false)) {
            n.setIsRead(true);
            n.setReadAt(now);
            notificationRepository.save(n);
        }
    }

    private NotificationDTO toDto(Notification n) {
        return new NotificationDTO(
                n.getId(),
                n.getTitle(),
                n.getContent(),
                n.getNotifyType() != null ? n.getNotifyType().name() : null,
                n.getRefType() != null ? n.getRefType().name() : null,
                n.getRefId(),
                n.getIsRead(),
                n.getCreatedAt() != null ? n.getCreatedAt().format(TS) : null,
                n.getReadAt() != null ? n.getReadAt().format(TS) : null);
    }

    /** 任务的全部潜在处理人：已认领人 + 用户候选人 + 候选角色下的用户（去重）。 */
    private List<User> collectHolders(Task task) {
        LinkedHashSet<User> users = new LinkedHashSet<>();
        List<IdentityLink> links = taskService.getIdentityLinksForTask(task.getId());
        if (task.getAssignee() != null) {
            userRepository.findByUsername(task.getAssignee()).ifPresent(users::add);
        }
        // 用户候选人（指定用户 / 发起人主管等）
        for (IdentityLink link : links) {
            if ("candidate".equals(link.getType()) && link.getUserId() != null && !link.getUserId().isBlank()) {
                userRepository.findByUsername(link.getUserId()).ifPresent(users::add);
            }
        }
        List<String> groupIds = links.stream()
                .map(IdentityLink::getGroupId)
                .filter(g -> g != null && !g.isBlank())
                .distinct()
                .toList();
        for (String role : groupIds) {
            users.addAll(userRepository.findByRolesRoleCode(role));
        }
        return new ArrayList<>(users);
    }

    /**
     * 通知一个 Flowable 任务的全部潜在处理人（已认领人 + 用户候选人 + 候选角色下的用户）。
     */
    @Transactional
    public void notifyTaskHolders(Task task, String title, String content) {
        if (task == null) {
            return;
        }
        String refId = task.getId();
        for (User u : collectHolders(task)) {
            notify(u, title, content, NotifyType.TASK, RefType.TASK, refId);
        }
    }

    /** 催办专用：与 {@link #notifyTaskHolders} 相同的接收人，但不走引用去重（每次催办都产生新通知）。 */
    @Transactional
    public void remindTaskHolders(org.flowable.task.api.Task task, String title, String content, String refId) {
        if (task == null) {
            return;
        }
        List<User> recipients = collectHolders(task);
        for (User u : recipients) {
            Notification n = new Notification();
            n.setUser(u);
            n.setTitle(title);
            n.setContent(content);
            n.setNotifyType(NotifyType.TASK);
            n.setRefType(RefType.TASK);
            n.setRefId(refId);
            n.setIsRead(false);
            notificationRepository.save(n);
        }
    }
}
