package com.oa.repository;

import com.oa.entity.JobTitle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobTitleRepository extends JpaRepository<JobTitle, Long> {
    Optional<JobTitle> findByCode(String code);
    List<JobTitle> findByEnabledTrueOrderByCodeAsc();
}
