package com.oa.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 审批类型：发起入口的业务分类（报销/采购/请假…），关联一个流程模板。
 * {@code code} 稳定唯一，作为流程实例 business_type 字符串的取值来源。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = {"def"})
@Entity
@Table(name = "approval_type")
public class ApprovalType extends BaseEntity {

    /** 类型代码（唯一，实例 business_type 取此值） */
    @Column(name = "code", length = 64, nullable = false)
    private String code;

    /** 展示名称 */
    @Column(name = "name", length = 64, nullable = false)
    private String name;

    /** 分类（人事/财务/考勤/休假/日常/法务/行政/其他） */
    @Column(name = "category", length = 32)
    private String category;

    /** 图标（emoji） */
    @Column(name = "icon", length = 32)
    private String icon;

    @Column(name = "description", length = 255)
    private String description;

    /** 排序权重（小者在前） */
    @Column(name = "weight", nullable = false)
    private Integer weight;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "def_id")
    private ProcessDefinition def;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;
}
