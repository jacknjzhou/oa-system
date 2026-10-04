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

    /** BPMN XML（可空 = 流程未设计；发布后发起仍被后端拦截） */
    private String bpmnXml;

    /** 可视化流程设计器规格 JSON（可空） */
    private String flowSpec;
}
