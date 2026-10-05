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

    /** 职级 id；未传 = 不动，clearJobLevel=true = 清除 */
    private Long jobLevelId;
    private Boolean clearJobLevel;

    /** 部门 id（调岗）；未传 = 不动，clearOrg=true = 清除归属 */
    private Long orgId;
    private Boolean clearOrg;

    /** 性别（男/女）；未传 = 不动 */
    private String gender;

    /** 职称 id；未传 = 不动，clearJobTitle=true = 清除 */
    private Long jobTitleId;
    private Boolean clearJobTitle;
}
