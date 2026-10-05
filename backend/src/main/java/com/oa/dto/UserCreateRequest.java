package com.oa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

/**
 * 员工创建请求（6.4.4）：含部门归属、性别、职称/职级、上级领导。
 * username 正则 [a-z0-9_.-]{2,32}；password >= 8 位；其余字典字段可空。
 */
@Data
public class UserCreateRequest {

    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "[a-z0-9_.-]{2,32}", message = "用户名格式: 2-32 位小写字母/数字/_ . -")
    private String username;

    @NotBlank(message = "初始密码不能为空")
    private String password;

    @NotBlank(message = "姓名不能为空")
    private String realName;

    private String gender;

    private Long orgId;

    private String position;

    private String phone;

    private String email;

    private Long supervisorId;

    private Long jobLevelId;

    private Long jobTitleId;

    private List<String> roleCodes;
}
