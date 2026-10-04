package com.oa.bpmn;

import com.oa.entity.CcRecord;
import com.oa.entity.ProcessInstance;
import com.oa.entity.User;
import com.oa.enums.NotifyType;
import com.oa.enums.RefType;
import com.oa.repository.CcRecordRepository;
import com.oa.repository.ProcessInstanceRepository;
import com.oa.repository.UserRepository;
import com.oa.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 抄送委托：流程经过 cc_notify 节点时，向流程变量 ccUserIds 中的用户写抄送记录并通知。
 *
 * <p>去重双层保障：
 * <ol>
 *   <li>{@code cc_record.uk_cc_instance_user}——同一实例对同一人只有一行；</li>
 *   <li>{@code notification} 写侧按 (用户, 引用) 去重——同一实例只通知一次。</li>
 * </ol>
 */
@Slf4j
@Component("ccDelegate")
@RequiredArgsConstructor
public class CcDelegate implements JavaDelegate {

    private final CcRecordRepository ccRecordRepository;
    private final ProcessInstanceRepository instanceRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Override
    public void execute(DelegateExecution execution) {
        List<Long> userIds = extractUserIds(execution);
        if (userIds.isEmpty()) {
            return;
        }
        ProcessInstance instance = instanceRepository
                .findByFlowableInstanceId(execution.getProcessInstanceId())
                .orElse(null);
        if (instance == null) {
            log.warn("cc_notify: 未找到业务实例 flowableInstanceId={}", execution.getProcessInstanceId());
            return;
        }

        for (Long userId : userIds) {
            if (ccRecordRepository.existsByInstanceIdAndUserId(instance.getId(), userId)) {
                continue;
            }
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                log.warn("cc_notify: 抄送用户不存在 userId={}", userId);
                continue;
            }
            CcRecord record = new CcRecord();
            record.setInstance(instance);
            record.setUser(user);
            record.setNodeKey(execution.getCurrentActivityId());
            ccRecordRepository.save(record);
            notificationService.notify(user, "您有一条抄送",
                    "流程【" + instance.getTitle() + "】已抄送给您。",
                    NotifyType.CC, RefType.PROCESS_INSTANCE, String.valueOf(instance.getId()));
        }
    }

    /** 流程变量 ccUserIds 可能是 List&lt;Long&gt;/List&lt;Integer&gt;/数字列表（JSON 反序列化产物），统一转 Long。 */
    private List<Long> extractUserIds(DelegateExecution execution) {
        List<Long> ids = new ArrayList<>();
        Object raw = execution.getVariable("ccUserIds");
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Number n) {
                    ids.add(n.longValue());
                }
            }
        }
        return ids;
    }
}
