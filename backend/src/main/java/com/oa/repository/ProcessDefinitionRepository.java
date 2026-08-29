package com.oa.repository;

import com.oa.entity.ProcessDefinition;
import com.oa.enums.ProcessDefinitionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProcessDefinitionRepository extends JpaRepository<ProcessDefinition, Long> {

    List<ProcessDefinition> findByDefKey(String defKey);

    List<ProcessDefinition> findByStatus(ProcessDefinitionStatus status);

    List<ProcessDefinition> findByCategory(String category);
}
