package com.oa.repository;

import com.oa.entity.ApprovalType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApprovalTypeRepository extends JpaRepository<ApprovalType, Long> {

    List<ApprovalType> findAllByOrderByWeightAscIdAsc();

    List<ApprovalType> findByEnabledOrderByWeightAscIdAsc(Boolean enabled);

    Optional<ApprovalType> findByCode(String code);
}
