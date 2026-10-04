package com.oa.repository;

import com.oa.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    Optional<Permission> findByCode(String code);
    List<Permission> findAllByOrderByIdAsc();
    List<Permission> findByIdInOrderByIdAsc(List<Long> ids);
}
