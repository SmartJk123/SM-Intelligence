package com.smi.transactions_service.invoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

// Closed projection: listing a page must not load up to 500 MB of document bytes.
public interface InvoiceSummary {
    UUID getId(); String getVendor(); String getInvoiceNumber(); BigDecimal getAmount(); String getCurrency();
    LocalDate getInvoiceDate(); LocalDate getDueDate(); String getFilename(); OffsetDateTime getCreatedAt();
}
