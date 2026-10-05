package com.oa.dto;

import jakarta.validation.constraints.NotBlank;

/** 假期类型创建/编辑请求（HD-05）。code 可选：空则默认取名称。 */
public record LeaveTypeRequest(
        @NotBlank String name,
        String code,
        String category,
        String quotaType,
        Integer annualQuota,
        @NotBlank String unit,
        Integer weight,
        Boolean enabled
) {}
