package com.oa.repository;

import com.oa.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * 吊销整个 token 家族（重用检测触发时）。
     * 独立事务（REQUIRES_NEW）立即提交：安全关键写入不能因外层事务回滚而丢失。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying
    @Query("update RefreshToken r set r.active = false where r.familyId = :familyId and r.active = true")
    int deactivateFamily(@Param("familyId") String familyId);
}
