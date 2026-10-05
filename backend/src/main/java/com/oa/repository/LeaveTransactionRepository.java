package com.oa.repository;

import com.oa.entity.LeaveTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LeaveTransactionRepository extends JpaRepository<LeaveTransaction, Long> {
    List<LeaveTransaction> findTop50ByUserIdAndLeaveTypeIdOrderByCreatedAtDescIdDesc(Long userId, Long leaveTypeId);

    List<LeaveTransaction> findTop50ByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    /** 幂等检查：同 ref 是否已有扣减流水（显式 @Query：派生方法名会被解析器误读）。 */
    @Query("select t from LeaveTransaction t where t.userId = :userId "
            + "and t.leaveTypeId = :typeId and t.refInstanceNo = :ref and t.delta < 0")
    Optional<LeaveTransaction> findConsumedByRef(@Param("userId") Long userId,
                                                  @Param("typeId") Long leaveTypeId,
                                                  @Param("ref") String ref);

    /** 冻结/释放标记计数（同 ref 是否已冻结/释放过）。 */
    @Query("select count(t) from LeaveTransaction t where t.userId = :userId "
            + "and t.leaveTypeId = :typeId and t.refInstanceNo = :ref and t.reason = :reason")
    long countByMarker(@Param("userId") Long userId, @Param("typeId") Long leaveTypeId,
                       @Param("ref") String ref, @Param("reason") String reason);

    List<LeaveTransaction> findTop100ByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    long countByLeaveTypeId(Long leaveTypeId);

    /** 管理页日志查询（HD-01/03；显式 @Query：派生方法名无法表达 null 参数可选语义）。 */
    @Query("select t from LeaveTransaction t where (:userId is null or t.userId = :userId) "
            + "and (:typeId is null or t.leaveTypeId = :typeId) "
            + "and (:txnType is null or t.txnType = :txnType) "
            + "and (:ref is null or t.refInstanceNo = :ref)")
    Page<LeaveTransaction> findLog(@Param("userId") Long userId, @Param("typeId") Long leaveTypeId,
                                   @Param("txnType") String txnType, @Param("ref") String ref,
                                   Pageable pageable);
}
