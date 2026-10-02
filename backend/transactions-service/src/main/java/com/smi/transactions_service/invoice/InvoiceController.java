package com.smi.transactions_service.invoice;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {
    private final InvoiceRepository repository;
    private final InvoiceIdentity identity;
    public InvoiceController(InvoiceRepository repository, InvoiceIdentity identity) {
        this.repository = repository; this.identity = identity;
    }
    public record InvoiceView(UUID id, String vendor, String invoiceNumber, BigDecimal amount, String currency,
        LocalDate invoiceDate, LocalDate dueDate, String filename, OffsetDateTime createdAt, String status, String source) {
        static InvoiceView of(Invoice i) {
            return new InvoiceView(i.id, i.vendor, i.invoiceNumber, i.amount, i.currency, i.invoiceDate,
                i.dueDate, i.filename, i.createdAt, "PENDING", "INVOICE");
        }
        static InvoiceView of(InvoiceSummary i) {
            return new InvoiceView(i.getId(), i.getVendor(), i.getInvoiceNumber(), i.getAmount(), i.getCurrency(),
                i.getInvoiceDate(), i.getDueDate(), i.getFilename(), i.getCreatedAt(), "PENDING", "INVOICE");
        }
    }
    @GetMapping
    public List<InvoiceView> list(@RequestHeader(value = "Authorization", required = false) String authorization,
                                 @RequestParam(defaultValue = "0") int page) {
        UUID owner = identity.owner(authorization);
        if (page < 0) throw bad("Invalid page");
        return repository.findByOwnerIdOrderByCreatedAtDesc(owner, PageRequest.of(page, 50)).stream().map(InvoiceView::of).toList();
    }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InvoiceView> save(@RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestParam MultipartFile file, @RequestParam String vendor, @RequestParam(defaultValue = "") String invoiceNumber,
        @RequestParam BigDecimal amount, @RequestParam String currency,
        @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate invoiceDate,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate dueDate) throws Exception {
        UUID owner = identity.owner(authorization);
        vendor = vendor.trim(); invoiceNumber = invoiceNumber.trim(); currency = currency.trim().toUpperCase(Locale.ROOT);
        if (vendor.isBlank() || vendor.length() > 200 || invoiceNumber.length() > 100) throw bad("Check vendor and invoice number");
        if (amount.signum() <= 0 || amount.scale() > 4 || amount.precision() - amount.scale() > 15) throw bad("Invalid amount");
        try { Currency.getInstance(currency); } catch (IllegalArgumentException e) { throw bad("Invalid currency"); }
        if (dueDate != null && dueDate.isBefore(invoiceDate)) throw bad("Due date cannot precede invoice date");
        if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024) throw bad("Choose a file up to 10 MB");
        byte[] bytes = file.getBytes();
        String type = detectType(bytes);
        if (type == null) throw bad("Use a JPEG, PNG or PDF document");
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        var existing = repository.findByOwnerIdAndDocumentHash(owner, hash);
        if (existing.isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT, "This document is already saved");
        Invoice i = new Invoice();
        i.id = UUID.randomUUID(); i.ownerId = owner; i.vendor = vendor; i.invoiceNumber = invoiceNumber;
        i.amount = amount; i.currency = currency; i.invoiceDate = invoiceDate; i.dueDate = dueDate;
        String name = Objects.toString(file.getOriginalFilename(), "invoice").replaceAll("[\\\\/\\p{Cntrl}]", "_");
        i.filename = name.substring(0, Math.min(name.length(), 200)); i.contentType = type;
        i.document = bytes; i.documentHash = hash; i.createdAt = OffsetDateTime.now();
        try { repository.saveAndFlush(i); } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This document is already saved");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(InvoiceView.of(i));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable UUID id) {
        UUID owner = identity.owner(authorization);
        if (repository.deleteOwned(id, owner) == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/{id}/document")
    public ResponseEntity<byte[]> document(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable UUID id) {
        UUID owner = identity.owner(authorization);
        Invoice i = repository.findByIdAndOwnerId(id, owner).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.ok().header("Cache-Control", "no-store").header("X-Content-Type-Options", "nosniff")
            .header("Content-Disposition", ContentDisposition.attachment().filename(i.filename, java.nio.charset.StandardCharsets.UTF_8).build().toString())
            .contentType(MediaType.parseMediaType(i.contentType)).body(i.document);
    }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    static String detectType(byte[] b) {
        if (b.length >= 5 && new String(b, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-")) return "application/pdf";
        if (b.length >= 8 && Arrays.equals(Arrays.copyOf(b, 8), new byte[]{(byte)137,80,78,71,13,10,26,10})) return "image/png";
        if (b.length >= 3 && b[0] == (byte)255 && b[1] == (byte)216 && b[2] == (byte)255) return "image/jpeg";
        return null;
    }
}
