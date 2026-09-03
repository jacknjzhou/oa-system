package com.oa.repository;

import com.oa.entity.ProcessInstance;
import com.oa.enums.BusinessType;
import com.oa.enums.ProcessInstanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProcessInstanceRepository extends JpaRepository<ProcessInstance, Long> {

    List<ProcessInstance> findByInitiatorId(Long initiatorId);

    List<ProcessInstance> findByStatus(ProcessInstanceStatus status);

    List<ProcessInstance> findByBusinessTypeAndBusinessId(BusinessType businessType, String businessId);

    Optional<ProcessInstance> findByFlowableInstanceId(String flowableInstanceId);
}
