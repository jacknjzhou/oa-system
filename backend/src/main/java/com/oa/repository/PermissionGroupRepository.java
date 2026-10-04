package com.oa.repository;

import com.oa.entity.PermissionGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PermissionGroupRepository extends JpaRepository<PermissionGroup, Long> {

    Optional<PermissionGroup> findByCode(String code);

    @Query(value = "select * from permission_group g where g.id in (select group_id from user_groups where user_id = :userId)", nativeQuery = true)
    List<PermissionGroup> findByMembersId(@Param("userId") Long userId);
}
