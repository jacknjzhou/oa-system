package com.oa.repository;

import com.oa.entity.User;
import com.oa.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    List<User> findByOrgId(Long orgId);

    List<User> findByRolesRoleCode(String roleCode);

    long countByOrgIdAndStatus(Long orgId, UserStatus status);

    List<User> findByStatus(UserStatus status);

    long countByJobLevelIdAndStatusNot(Long jobLevelId, UserStatus status);

    long countByJobTitleIdAndStatusNot(Long jobTitleId, UserStatus status);
}
