package com.oa.dto;

import lombok.Data;

@Data
public class ApprovalTypeDTO {
    private Long id;
    private String code;
    private String name;
    private String category;
    private String icon;
    private String description;
    private Integer weight;
    private Long defId;
    private String defName;
    private Boolean enabled;
}
