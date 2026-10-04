package com.oa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 权限项（系统内置清单，按 group_name 十大分组）。 */
@Entity
@Table(name = "permission")
@Getter
@Setter
public class Permission extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 48)
    private String code;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    /** 分组（审批/考勤/假期/公文/印章/合同/费用/采购/人事/系统）。 */
    @Column(name = "group_name", nullable = false, length = 32)
    private String groupName;
}
