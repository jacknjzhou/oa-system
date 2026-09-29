package com.oa.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 公文视图（实体 author/instance 为 @JsonIgnore，作者名在此显式带出）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentDTO {

    private Long id;

    private String docNo;

    private String title;

    private String content;

    /** NOTICE / DIRECTIVE / REPORT / LETTER / OTHER */
    private String docType;

    /** NORMAL / URGENT / CRITICAL */
    private String urgency;

    /** PUBLIC / INTERNAL / SECRET / TOP_SECRET */
    private String secrecyLevel;

    /** 起草人显示名（实名缺省回退用户名） */
    private String authorName;

    /** DRAFT / PUBLISHED / ARCHIVED */
    private String status;

    private String publishedAt;

    private String archivedAt;

    private String createdAt;

    private String updatedAt;
}
