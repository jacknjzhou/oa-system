package com.oa.enums;

/**
 * 流程实例状态，映射 TINYINT: RUNNING=0, COMPLETED=1, CANCELLED=2, REJECTED=3, DRAFT=4
 * <p>
 * 持久化方式：{@code @Enumerated(ORDINAL)}——**只能在末尾追加**新值，
 * 不得插入或重排，否则存量行 ordinal 错位导致状态错乱。
 */
public enum ProcessInstanceStatus {
    RUNNING,
    COMPLETED,
    CANCELLED,
    REJECTED,
    /** 草稿：仅落自有表（status=DRAFT，无 Flowable 实例），提交后启动引擎 */
    DRAFT
}
