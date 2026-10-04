package com.oa.controller;

import com.oa.dto.ApiResponse;
import com.oa.dto.ApprovalTypeDTO;
import com.oa.dto.ApprovalTypeRequest;
import com.oa.service.ApprovalTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 审批类型（P2-1a）：列表 / 启停列表 / CRUD。
 */
@RestController
@RequestMapping("/api/approval-types")
@RequiredArgsConstructor
public class ApprovalTypeController {

    private final ApprovalTypeService approvalTypeService;

    /** 全部类型（管理页，按 weight 升序） */
    @GetMapping
    public ApiResponse<List<ApprovalTypeDTO>> list() {
        return ApiResponse.success(approvalTypeService.listAll());
    }

    /** 仅启用（发起页） */
    @GetMapping("/enabled")
    public ApiResponse<List<ApprovalTypeDTO>> listEnabled() {
        return ApiResponse.success(approvalTypeService.listEnabled());
    }

    @PostMapping
    public ApiResponse<ApprovalTypeDTO> create(@Valid @RequestBody ApprovalTypeRequest req) {
        return ApiResponse.success(approvalTypeService.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<ApprovalTypeDTO> update(@PathVariable Long id, @Valid @RequestBody ApprovalTypeRequest req) {
        return ApiResponse.success(approvalTypeService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        approvalTypeService.delete(id);
        return ApiResponse.success(null);
    }
}
