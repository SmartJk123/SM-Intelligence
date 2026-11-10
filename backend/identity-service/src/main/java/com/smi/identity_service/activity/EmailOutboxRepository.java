package com.smi.identity_service.activity;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmailOutboxRepository extends JpaRepository<EmailOutboxEntry, UUID> {
    List<EmailOutboxEntry> findAllByOrderByCreatedAtDesc(Pageable page);
}
