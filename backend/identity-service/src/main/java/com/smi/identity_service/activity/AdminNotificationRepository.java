package com.smi.identity_service.activity;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AdminNotificationRepository extends JpaRepository<AdminNotification, UUID> {
    List<AdminNotification> findAllByOrderByCreatedAtDesc(Pageable page);

    List<AdminNotification> findByReadAtIsNull();
}
