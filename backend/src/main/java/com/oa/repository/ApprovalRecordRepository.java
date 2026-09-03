package com.oa.repository;

import com.oa.entity.ApprovalRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalRecordRepository extends JpaRepository<ApprovalRecord, Long> {

    List<ApprovalRecord> findByInstanceIdOrderByCreatedAtAsc(Long instanceId);
}
