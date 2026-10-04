package com.oa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApprovalTypeRequest {
    /** 类型代码（创建时必填，唯一；实例 business_type 取此值） */
    @NotBlank(message = "类型代码不能为空")
    private String code;

    @NotBlank(message = "类型名称不能为空")
    private String name;

    private String category;

    private String icon;

    private String description;

    private Integer weight;

    /** 关联流程模板 ID */
    @NotNull(message = "关联流程模板不能为空")
    private Long defId;

    private Boolean enabled;
}
