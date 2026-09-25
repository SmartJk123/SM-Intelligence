package com.smi.transactions_service.invoice;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "invoices", uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "document_hash"}))
public class Invoice {
    @Id public UUID id;
    @Column(name = "owner_id", nullable = false) public UUID ownerId;
    @Column(nullable = false, length = 200) public String vendor;
    @Column(name = "invoice_number", nullable = false, length = 100) public String invoiceNumber;
    @Column(nullable = false, precision = 19, scale = 4) public BigDecimal amount;
    @Column(nullable = false, length = 3) public String currency;
    @Column(name = "invoice_date", nullable = false) public LocalDate invoiceDate;
    @Column(name = "due_date") public LocalDate dueDate;
    @Column(nullable = false, length = 200) public String filename;
    @Column(name = "content_type", nullable = false, length = 100) public String contentType;
    @Column(nullable = false) public byte[] document;
    @Column(name = "document_hash", nullable = false, length = 64) public String documentHash;
    @Column(name = "created_at", nullable = false) public OffsetDateTime createdAt;
}
