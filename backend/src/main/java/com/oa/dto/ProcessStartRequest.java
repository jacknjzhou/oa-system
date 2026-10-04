package com.oa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProcessStartRequest {

    @NotNull(message = "流程定义ID不能为空")
    private Long defId;

    @NotBlank(message = "标题不能为空")
    private String title;

    /** 业务类型代码（可选，取审批类型 code；缺省 REIMBURSEMENT） */
    private String businessType;

    private String businessData;

    /** true = 只存草稿（不落 Flowable），稍后通过 POST /process-instances/{id}/submit 启动 */
    private Boolean draft;

    /** 抄送人用户 ID 列表：流程完成时由 cc_notify 节点通知（每实例每人一次） */
    private List<Long> ccUserIds;
}
