package com.oa.repository;

import com.oa.entity.Notification;
import com.oa.enums.RefType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdAndIsRead(Long userId, Boolean isRead);

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByUserIdAndRefTypeAndRefId(Long userId, RefType refType, String refId);

    long countByUserIdAndIsReadFalse(Long userId);

    /** 按传入排序分页查询（通知列表用：isRead asc, createdAt desc）。 */
    Page<Notification> findByUserId(Long userId, Pageable pageable);
}
