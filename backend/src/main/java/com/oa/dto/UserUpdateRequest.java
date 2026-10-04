package com.oa.dto;

import lombok.Data;

import java.util.List;

/**
 * 用户资料更新（流程设计器“发起人主管”配置用）。
 * 文本字段未传保持原值；supervisorId 总是同步（null = 清除主管）。
 */
@Data
public class UserUpdateRequest {

    private String realName;
    private String position;
    private String phone;
    private String email;
    private Long supervisorId;

    /** 角色编码列表（如 ["MANAGER"]）；未传 = 不动 */
    private List<String> roleCodes;
}
