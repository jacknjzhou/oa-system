package com.oa.enums;

/**
 * 任务状态，映射 TINYINT: PENDING=0, CLAIMED=1, COMPLETED=2, REJECTED=3, TRANSFERRED=4
 */
public enum TaskStatus {
    PENDING,
    CLAIMED,
    COMPLETED,
    REJECTED,
    TRANSFERRED
}
