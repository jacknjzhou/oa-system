package com.oa.bpmn;

import com.oa.entity.User;
import com.oa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 解析“发起人主管”（流程设计器主管审批人）：
 * 读 initiatorId 变量 → 主管 username 写入 supervisorUsername 变量，
 * 后续 userTask 以 candidateUsers="${supervisorUsername}" 指派
 *（与本系统“assignee/candidate = username”约定一致）。
 * 发起人无主管时回退到发起人本人（避免任务悬空）并记录日志。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssignSupervisorDelegate implements JavaDelegate {

    private final UserRepository userRepository;

    @Override
    public void execute(DelegateExecution execution) {
        Object initiatorRaw = execution.getVariable("initiatorId");
        Long initiatorId = initiatorRaw instanceof Number number ? number.longValue() : null;
        User initiator = initiatorId == null ? null : userRepository.findById(initiatorId).orElse(null);
        if (initiator == null) {
            execution.setVariable("supervisorUsername", null);
            return;
        }
        User supervisor = initiator.getSupervisorId() == null ? null
                : userRepository.findById(initiator.getSupervisorId()).orElse(null);
        if (supervisor == null) {
            supervisor = initiator;
            log.info("流程 {} 发起人 {} 未设置主管，任务回退给发起人本人",
                    execution.getProcessInstanceId(), initiator.getUsername());
        }
        execution.setVariable("supervisorUsername", supervisor.getUsername());
    }
}
