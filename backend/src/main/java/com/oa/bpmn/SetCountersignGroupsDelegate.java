package com.oa.bpmn;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 会签名单委托：在会签多实例节点前设置候选组集合变量。
 *
 * <p>JUEL 不支持列表字面量，多实例的 {@code flowable:collection} 必须引用变量，
 * 故用 ServiceTask + 委托的方式声明式地提供名单。该节点位于「金额 > 1 万」分支，
 * 只有走会签路径才会执行。名单变更只需改这里（或后续改为按部门动态计算）。
 */
@Component("setCountersignGroupsDelegate")
public class SetCountersignGroupsDelegate implements JavaDelegate {

    /** 会签候选组：财务 + 部门经理，两组须全部通过。 */
    private static final List<String> COUNTERSIGN_GROUPS = List.of("FINANCE", "MANAGER");

    @Override
    public void execute(DelegateExecution execution) {
        execution.setVariable("countersignGroups", COUNTERSIGN_GROUPS);
    }
}
