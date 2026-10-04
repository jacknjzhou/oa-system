package com.oa.enums;

/**
 * 通知类型，映射 TINYINT: TASK=0, PROCESS=1, DOCUMENT=2, SYSTEM=3, CC=4
 * ordinal 约束：只能末尾追加。
 */
public enum NotifyType {
    TASK,
    PROCESS,
    DOCUMENT,
    SYSTEM,
    /** 抄送：流程抄送节点触发 */
    CC
}
