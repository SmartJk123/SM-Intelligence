package com.smi.transactions_service.controller;

import com.smi.transactions_service.dto.CreateTransactionRequest;
import com.smi.transactions_service.dto.TransactionResponse;
import com.smi.transactions_service.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> recordTransaction(@Valid @RequestBody CreateTransactionRequest request) {
        TransactionResponse response = transactionService.recordTransaction(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<TransactionResponse>> getTransactions(
            @RequestParam UUID accountId,
            @RequestParam(required = false) String status) {
        List<TransactionResponse> transactions = transactionService.getTransactionsByAccountId(accountId, status);
        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getTransactionById(@PathVariable UUID id) {
        TransactionResponse transaction = transactionService.getTransactionById(id);
        return ResponseEntity.ok(transaction);
    }

    @PatchMapping("/{id}/category")
    public ResponseEntity<TransactionResponse> updateCategory(
            @PathVariable UUID id,
            @RequestBody Map<String, UUID> payload) {
        UUID categoryId = payload.get("categoryId");
        TransactionResponse updated = transactionService.updateCategory(id, categoryId);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/reverse")
    public ResponseEntity<TransactionResponse> reverseTransaction(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> payload) {
        String reason = payload != null ? payload.get("reason") : "Requested by user";
        TransactionResponse reversed = transactionService.reverseTransaction(id, reason);
        return ResponseEntity.ok(reversed);
    }
}
