package com.oa.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 部门创建/编辑请求（SY-02）。
 * orgCode 可空（缺省=orgName）；parentId 可空=顶层；编辑时 null 字段不动。
 */
@Data
public class OrganizationRequest {

    @NotBlank(message = "部门名称不能为空")
    private String orgName;

    private String orgCode;

    private Long parentId;

    private Integer sortOrder;
}
