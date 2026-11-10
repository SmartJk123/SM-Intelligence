package com.smi.notifications_service.repository;

import com.smi.notifications_service.domain.NotificationOutbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, UUID> {

    List<NotificationOutbox> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);

    List<NotificationOutbox> findByNotificationId(UUID notificationId);
}
