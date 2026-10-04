package com.oa.repository;

import com.oa.entity.CheckRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface CheckRecordRepository extends JpaRepository<CheckRecord, Long> {

    List<CheckRecord> findByUserIdAndCheckTimeBetweenOrderByCheckTimeAsc(Long userId,
                                                                         LocalDateTime start,
                                                                         LocalDateTime end);
}
