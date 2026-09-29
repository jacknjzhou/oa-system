package com.oa.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 站内通知视图（前端通知中心 / 铃铛角标契约）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {

    private Long id;

    private String title;

    private String content;

    /** TASK / PROCESS / DOCUMENT / SYSTEM */
    private String notifyType;

    /** 关联对象类型：TASK / PROCESS_INSTANCE / DOCUMENT（点击跳转路由依据） */
    private String refType;

    /** 关联业务 ID（Flowable 任务 ID / 流程实例 ID / 公文 ID） */
    private String refId;

    private Boolean isRead;

    private String createdAt;

    private String readAt;
}
