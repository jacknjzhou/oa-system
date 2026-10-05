package com.oa.dto;

import lombok.Data;

/** 职称创建/编辑请求（SY-04）：code 缺省=名称，编辑时 code 不可变。 */
@Data
public class JobTitleRequest {

    private String code;

    private String name;

    private Boolean enabled;
}
