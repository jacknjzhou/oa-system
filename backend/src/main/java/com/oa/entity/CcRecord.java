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
 * 抄送记录：同一实例对同一人唯一（uk_cc_instance_user 去重，防重复通知）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = {"instance", "user"})
@Entity
@Table(name = "cc_record")
public class CcRecord extends BaseEntity {

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "instance_id")
    private ProcessInstance instance;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    /** 触发抄送的节点 key（cc_notify） */
    @Column(name = "node_key", length = 64)
    private String nodeKey;
}
