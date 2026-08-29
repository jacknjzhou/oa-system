package com.oa.dto;

import com.oa.enums.BusinessType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProcessStartRequest {

    @NotNull(message = "流程定义ID不能为空")
    private Long defId;

    @NotBlank(message = "标题不能为空")
    private String title;

    private BusinessType businessType;

    private String businessData;
}
