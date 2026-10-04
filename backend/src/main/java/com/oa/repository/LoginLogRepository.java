package com.oa.repository;

import com.oa.entity.LoginLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoginLogRepository extends JpaRepository<LoginLog, Long> {

    Page<LoginLog> findByUsernameContaining(String keyword, Pageable pageable);

    @Query("select l from LoginLog l order by l.loginTime desc")
    Page<LoginLog> findAllDesc(Pageable pageable);
}
