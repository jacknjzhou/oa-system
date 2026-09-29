package com.oa.controller;

import com.oa.dto.ApiResponse;
import com.oa.dto.NotificationDTO;
import com.oa.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 站内通知：铃铛未读角标 + 通知中心列表 + 标记已读。
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** 我的通知列表（未读优先，其次时间倒序）。 */
    @GetMapping
    public ApiResponse<List<NotificationDTO>> list(
            @RequestParam(name = "limit", required = false, defaultValue = "20") int limit) {
        return ApiResponse.success(notificationService.listMine(limit));
    }

    /** 我的未读通知数（铃铛角标轮询用）。 */
    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount() {
        return ApiResponse.success(Map.of("count", notificationService.countUnread()));
    }

    /** 标记单条已读。 */
    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return ApiResponse.success();
    }

    /** 我的全部通知标记已读。 */
    @PostMapping("/read-all")
    public ApiResponse<Void> markAllRead() {
        notificationService.markAllRead();
        return ApiResponse.success();
    }
}
