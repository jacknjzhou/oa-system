package com.oa.controller;

import com.oa.dto.ApiResponse;
import com.oa.dto.DefinitionDTO;
import com.oa.dto.InstanceDTO;
import com.oa.dto.ProcessDefinitionRequest;
import com.oa.dto.ProcessStartRequest;
import com.oa.service.ProcessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProcessController {

    private final ProcessService processService;

    // ==================== 模板管理 ====================

    @GetMapping("/process-definitions")
    public ApiResponse<List<DefinitionDTO>> listDefinitions(
            @RequestParam(name = "deployable", required = false) Boolean deployable) {
        return ApiResponse.success(processService.listDefinitions(
                deployable != null && deployable));
    }

    @PostMapping("/process-definitions")
    public ApiResponse<DefinitionDTO> createDefinition(@Valid @RequestBody ProcessDefinitionRequest request) {
        return ApiResponse.success(processService.createDefinition(request));
    }

    @GetMapping("/process-definitions/{id}")
    public ApiResponse<DefinitionDTO> getDefinition(@PathVariable Long id) {
        return ApiResponse.success(processService.getDefinition(id));
    }

    @PutMapping("/process-definitions/{id}")
    public ApiResponse<DefinitionDTO> updateDefinition(@PathVariable Long id,
                                                       @Valid @RequestBody ProcessDefinitionRequest request) {
        return ApiResponse.success(processService.updateDefinition(id, request));
    }

    @PostMapping("/process-definitions/{id}/publish")
    public ApiResponse<DefinitionDTO> publishDefinition(@PathVariable Long id) {
        return ApiResponse.success(processService.publish(id));
    }

    @PostMapping("/process-definitions/{id}/disable")
    public ApiResponse<DefinitionDTO> disableDefinition(@PathVariable Long id) {
        return ApiResponse.success(processService.disable(id));
    }

    // ==================== 流程实例 ====================

    @PostMapping("/process-instances")
    public ApiResponse<InstanceDTO> startInstance(@Valid @RequestBody ProcessStartRequest request) {
        return ApiResponse.success(processService.startInstance(request));
    }

    @GetMapping("/process-instances/my")
    public ApiResponse<List<InstanceDTO>> myInstances() {
        return ApiResponse.success(processService.listMyInstances());
    }

    @GetMapping("/process-instances/{id}")
    public ApiResponse<Map<String, Object>> getInstance(@PathVariable Long id) {
        return ApiResponse.success(processService.getInstance(id));
    }

    @PostMapping("/process-instances/{id}/cancel")
    public ApiResponse<InstanceDTO> cancelInstance(@PathVariable Long id) {
        return ApiResponse.success(processService.cancelInstance(id));
    }

    /** 提交草稿（DRAFT → 启动 Flowable → RUNNING），仅发起人。 */
    @PostMapping("/process-instances/{id}/submit")
    public ApiResponse<InstanceDTO> submitInstance(@PathVariable Long id) {
        return ApiResponse.success(processService.submitInstance(id));
    }
}
