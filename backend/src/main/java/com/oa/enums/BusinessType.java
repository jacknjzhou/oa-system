package com.oa.enums;

/**
 * 已知业务类型代码（仅常量参考）。V10 起实例 business_type 为字符串（审批类型 code），
 * 不再用枚举序号持久化；新增类型无需改此处。
 */
public enum BusinessType {
    REIMBURSEMENT,
    LEAVE,
    PROCUREMENT,
    DOCUMENT
}
