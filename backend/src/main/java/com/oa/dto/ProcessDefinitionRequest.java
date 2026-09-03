package com.oa.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProcessDefinitionRequest {

    @NotBlank(message = "流程定义key不能为空")
    private String defKey;

    @NotBlank(message = "流程名称不能为空")
    private String name;

    private String category;

    private String description;

    private String formConfig;

    @NotBlank(message = "BPMN XML不能为空")
    private String bpmnXml;
}
