package com.oa.repository;

import com.oa.entity.LeaveBalance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {
    Optional<LeaveBalance> findByUserIdAndLeaveTypeId(Long userId, Long leaveTypeId);
    List<LeaveBalance> findByUserIdOrderByLeaveTypeId(Long userId);

    /** 管理页批量取余额。 */
    List<LeaveBalance> findByUserIdInAndLeaveTypeIdIn(Collection<Long> userIds, Collection<Long> typeIds);

    /** 某类型总余额（类型编辑/删除前提示用）。 */
    long countByLeaveTypeId(Long leaveTypeId);
}
