package com.oa.repository;

import com.oa.entity.Task;
import com.oa.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByAssigneeIdAndStatus(Long assigneeId, TaskStatus status);

    List<Task> findByInstanceId(Long instanceId);

    List<Task> findByAssigneeIdAndStatusIn(Long assigneeId, Collection<TaskStatus> statuses);
}
