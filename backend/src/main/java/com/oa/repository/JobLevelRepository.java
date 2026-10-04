package com.oa.repository;

import com.oa.entity.JobLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobLevelRepository extends JpaRepository<JobLevel, Long> {
    Optional<JobLevel> findByCode(String code);
    List<JobLevel> findByEnabledTrueOrderByCodeAsc();
}
