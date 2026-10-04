package com.oa.dto;

import lombok.Data;

/** 职级/职称新增或屏蔽请求（职称新增暂不开放，仅屏蔽/启用）。 */
@Data
public class JobLevelRequest {
    private String code;
    private String name;
    private Boolean enabled;
}
