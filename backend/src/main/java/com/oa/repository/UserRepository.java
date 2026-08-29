package com.oa.repository;

import com.oa.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    List<User> findByOrgId(Long orgId);

    List<User> findByRolesRoleCode(String roleCode);
}
