package com.oa.repository;

import com.oa.entity.LeaveTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LeaveTransactionRepository extends JpaRepository<LeaveTransaction, Long> {

    List<LeaveTransaction> findTop50ByUserIdAndLeaveTypeIdOrderByCreatedAtDescIdDesc(Long userId, Long leaveTypeId);

    List<LeaveTransaction> findTop50ByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    /** 派生查询对 DeltaLessThan0 解析有歧义，用显式 @Query。 */
    @Query("select t from LeaveTransaction t where t.userId = :userId and t.leaveTypeId = :typeId "
            + "and t.refInstanceNo = :ref and t.delta < 0")
    Optional<LeaveTransaction> findConsumedByRef(@Param("userId") Long userId,
                                                  @Param("typeId") Long typeId,
                                                  @Param("ref") String ref);
}
