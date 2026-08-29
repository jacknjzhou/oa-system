package com.oa.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskTransferRequest {

    @NotNull(message = "转办目标用户不能为空")
    private Long toUserId;

    private String comment;
}
