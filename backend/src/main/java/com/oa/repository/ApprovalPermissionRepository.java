package com.oa.repository;

import com.oa.entity.ApprovalPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalPermissionRepository extends JpaRepository<ApprovalPermission, Long> {

    List<ApprovalPermission> findByDefId(Long defId);

    void deleteByDefId(Long defId);
}
