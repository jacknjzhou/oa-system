package com.oa.repository;

import com.oa.entity.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {
    Optional<LeaveType> findByCode(String code);
    List<LeaveType> findByEnabledTrueOrderByWeightAscIdAsc();

    /** 请假表单选项用：按权重降序（规格 5.2 权威表序）。 */
    List<LeaveType> findByEnabledTrueOrderByWeightDescIdAsc();

    /** 管理页用：全部（含停用）。 */
    List<LeaveType> findAllByOrderByWeightDescIdAsc();
}
