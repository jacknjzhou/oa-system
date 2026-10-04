package com.oa.dto;

import lombok.Data;

import java.util.List;

/** 审批功能权限保存请求：角色 × 权限项列表（start/view/manage/edit）。 */
@Data
public class ApprovalPermissionRequest {

    private String roleCode;

    private List<String> perms;
}
