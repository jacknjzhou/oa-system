package com.oa.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * 假期额度授予/调整请求（HD-07）：
 * action=SET_QUOTA（额度设定，txnType=QUOTA）或 SET_REMAINING（剩余时长修正，txnType=ADJUST）。
 */
public record LeaveAdminGrantRequest(
        @NotNull Long userId,
        @NotBlank String typeCode,
        @NotBlank @Pattern(regexp = "SET_QUOTA|SET_REMAINING") String action,
        @NotNull @DecimalMin("0") BigDecimal amount,
        String remark) {
}
