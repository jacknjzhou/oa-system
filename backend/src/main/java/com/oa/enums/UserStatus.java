package com.oa.enums;

/**
 * 用户状态，映射 TINYINT: INACTIVE=0, ACTIVE=1, LOCKED=2
 */
public enum UserStatus {
    INACTIVE,
    ACTIVE,
    LOCKED,
    /** 已删除（回收站）：不可登录、不出现在常规列表。 */
    DELETED
}
