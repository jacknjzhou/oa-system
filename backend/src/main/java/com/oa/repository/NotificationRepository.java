package com.oa.repository;

import com.oa.entity.Notification;
import com.oa.enums.RefType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdAndIsRead(Long userId, Boolean isRead);

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByUserIdAndRefTypeAndRefId(Long userId, RefType refType, String refId);
}
