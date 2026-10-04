package com.oa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 审批功能权限：模板 × 角色 × 权限项（发起/查看/管理/编辑）。 */
@Data
@Entity
@Table(name = "approval_permission",
        uniqueConstraints = @UniqueConstraint(name = "uk_ap_def_role_perm", columnNames = {"def_id", "role_code", "perm"}))
@EqualsAndHashCode(callSuper = true)
public class ApprovalPermission extends BaseEntity {

    @Column(name = "def_id", nullable = false)
    private Long defId;

    /** 冗余模板名（展示用，避免联查） */
    @Column(name = "def_name", length = 128)
    private String defName;

    @Column(name = "role_code", nullable = false, length = 32)
    private String roleCode;

    /** start / view / manage / edit */
    @Column(name = "perm", nullable = false, length = 16)
    private String perm;
}
