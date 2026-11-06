package com.smi.identity_service.activity;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    List<AuditEvent> findAllByOrderByOccurredAtDesc(Pageable page);

    long countByActionAndActorEmailAndOccurredAtAfter(String action, String actorEmail, java.time.OffsetDateTime since);

    long deleteByOccurredAtBefore(java.time.OffsetDateTime cutoff);
}
