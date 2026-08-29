package com.oa.controller;

import com.oa.dto.ApiResponse;
import com.oa.dto.ProcessDefinitionRequest;
import com.oa.dto.ProcessStartRequest;
import com.oa.entity.ProcessDefinition;
import com.oa.entity.ProcessInstance;
import com.oa.service.ProcessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProcessController {

    private final ProcessService processService;

    @GetMapping("/process-definitions")
    public ApiResponse<List<ProcessDefinition>> listDefinitions() {
        return ApiResponse.success(processService.listDefinitions());
    }

    @PostMapping("/process-definitions")
    public ApiResponse<ProcessDefinition> createDefinition(@Valid @RequestBody ProcessDefinitionRequest request) {
        return ApiResponse.success(processService.createDefinition(request));
    }

    @PostMapping("/process-instances")
    public ApiResponse<ProcessInstance> startInstance(@Valid @RequestBody ProcessStartRequest request) {
        return ApiResponse.success(processService.startInstance(request));
    }

    @GetMapping("/process-instances/my")
    public ApiResponse<List<ProcessInstance>> myInstances() {
        return ApiResponse.success(processService.listMyInstances());
    }

    @GetMapping("/process-instances/{id}")
    public ApiResponse<Map<String, Object>> getInstance(@PathVariable Long id) {
        return ApiResponse.success(processService.getInstance(id));
    }

    @PostMapping("/process-instances/{id}/cancel")
    public ApiResponse<ProcessInstance> cancelInstance(@PathVariable Long id) {
        return ApiResponse.success(processService.cancelInstance(id));
    }
}
