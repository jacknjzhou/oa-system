package com.oa.repository;

import com.oa.entity.CcRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CcRecordRepository extends JpaRepository<CcRecord, Long> {

    List<CcRecord> findByInstanceId(Long instanceId);

    List<CcRecord> findByUserId(Long userId);

    boolean existsByInstanceIdAndUserId(Long instanceId, Long userId);
}
