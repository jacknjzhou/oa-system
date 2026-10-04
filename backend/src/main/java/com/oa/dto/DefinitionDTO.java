package com.oa.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审批模板视图（流程定义元数据；单查时附带 bpmnXml）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DefinitionDTO {

    private Long id;

    private String defKey;

    private String name;

    private Integer version;

    private String category;

    private String description;

    /** 动态表单配置（JSON 字符串） */
    private String formConfig;

    /** DRAFT / PUBLISHED / DISABLED */
    private String status;

    /** 流程是否已设计（bpmnXml 非空）；模板可“表单已发布、流程未设计”双态 */
    private boolean flowReady;

    private String creatorName;

    private String publishedAt;

    /** BPMN 2.0 XML，仅在单查接口返回 */
    private String bpmnXml;

    /** 可视化流程设计器规格 JSON，仅在单查接口返回 */
    private String flowSpec;
}
