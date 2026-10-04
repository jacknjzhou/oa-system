package com.oa.controller;

import com.oa.dto.ApiResponse;
import com.oa.dto.TaskCompleteRequest;
import com.oa.dto.TaskDTO;
import com.oa.dto.TaskRejectRequest;
import com.oa.dto.TaskTransferRequest;
import com.oa.service.TaskService;
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
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    /** 我的待办。 */
    @GetMapping
    public ApiResponse<List<TaskDTO>> myTasks() {
        return ApiResponse.success(taskService.getMyTasks());
    }

    /** 我的已办（历史任务）。 */
    @GetMapping("/done")
    public ApiResponse<List<TaskDTO>> myDoneTasks() {
        return ApiResponse.success(taskService.getMyDoneTasks());
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> taskDetail(@PathVariable String id) {
        return ApiResponse.success(taskService.getTaskDetail(id));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<TaskDTO> complete(@PathVariable String id, @Valid @RequestBody TaskCompleteRequest request) {
        return ApiResponse.success(taskService.completeTask(id, request));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<TaskDTO> reject(@PathVariable String id, @Valid @RequestBody TaskRejectRequest request) {
        return ApiResponse.success(taskService.rejectTask(id, request));
    }

    /** 拒绝：终止整个流程（区别于驳回到节点）。 */
    @PostMapping("/{id}/deny")
    public ApiResponse<TaskDTO> deny(@PathVariable String id, @Valid @RequestBody TaskRejectRequest request) {
        return ApiResponse.success(taskService.denyTask(id, request));
    }

    @PostMapping("/{id}/transfer")
    public ApiResponse<TaskDTO> transfer(@PathVariable String id, @Valid @RequestBody TaskTransferRequest request) {
        return ApiResponse.success(taskService.transferTask(id, request));
    }

}
