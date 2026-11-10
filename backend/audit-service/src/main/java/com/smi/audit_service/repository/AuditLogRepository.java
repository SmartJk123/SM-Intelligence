package com.smi.audit_service.repository;

import com.smi.audit_service.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    List<AuditLog> findByTableNameAndRecordIdOrderByChangedAtDesc(String tableName, UUID recordId);

    Page<AuditLog> findBySourceServiceOrderByChangedAtDesc(String sourceService, Pageable pageable);

    Page<AuditLog> findByChangedByOrderByChangedAtDesc(UUID changedBy, Pageable pageable);
}
