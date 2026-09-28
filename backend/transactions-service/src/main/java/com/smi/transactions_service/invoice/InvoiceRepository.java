package com.smi.transactions_service.invoice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from Invoice i where i.id = :id and i.ownerId = :ownerId")
    int deleteOwned(@org.springframework.data.repository.query.Param("id") UUID id,
                    @org.springframework.data.repository.query.Param("ownerId") UUID ownerId);
    List<InvoiceSummary> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId, Pageable pageable);
    Optional<Invoice> findByIdAndOwnerId(UUID id, UUID ownerId);
    Optional<Invoice> findByOwnerIdAndDocumentHash(UUID ownerId, String documentHash);
}
