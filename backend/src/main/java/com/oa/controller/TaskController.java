package com.oa.controller;

import com.oa.dto.ApiResponse;
import com.oa.dto.TaskCompleteRequest;
import com.oa.dto.TaskRejectRequest;
import com.oa.dto.TaskTransferRequest;
import com.oa.entity.Task;
import com.oa.enums.TaskStatus;
import com.oa.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping
    public ApiResponse<List<Task>> myTasks(@RequestParam(required = false) TaskStatus status) {
        return ApiResponse.success(taskService.getMyTasks(status));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> taskDetail(@PathVariable Long id) {
        return ApiResponse.success(taskService.getTaskDetail(id));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<Task> complete(@PathVariable Long id, @Valid @RequestBody TaskCompleteRequest request) {
        return ApiResponse.success(taskService.completeTask(id, request));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<Task> reject(@PathVariable Long id, @Valid @RequestBody TaskRejectRequest request) {
        return ApiResponse.success(taskService.rejectTask(id, request));
    }

    @PostMapping("/{id}/transfer")
    public ApiResponse<Task> transfer(@PathVariable Long id, @Valid @RequestBody TaskTransferRequest request) {
        return ApiResponse.success(taskService.transferTask(id, request));
    }
}
