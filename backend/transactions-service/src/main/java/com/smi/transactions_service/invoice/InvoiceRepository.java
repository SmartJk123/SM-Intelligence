package com.smi.transactions_service.invoice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    List<InvoiceSummary> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId, Pageable pageable);
    Optional<Invoice> findByIdAndOwnerId(UUID id, UUID ownerId);
    Optional<Invoice> findByOwnerIdAndDocumentHash(UUID ownerId, String documentHash);
}
