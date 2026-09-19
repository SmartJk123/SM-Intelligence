package com.smi.transactions_service.service;

import com.smi.transactions_service.domain.Transaction;
import com.smi.transactions_service.dto.CreateTransactionRequest;
import com.smi.transactions_service.dto.TransactionResponse;
import com.smi.transactions_service.exception.DuplicateTransactionException;
import com.smi.transactions_service.exception.TransactionNotFoundException;
import com.smi.transactions_service.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public TransactionResponse recordTransaction(CreateTransactionRequest request) {
        if (request.getProviderReference() != null && !request.getProviderReference().isBlank()) {
            if (transactionRepository.existsByProviderReference(request.getProviderReference())) {
                throw new DuplicateTransactionException(
                    String.format("Transaction with provider reference '%s' already exists", request.getProviderReference())
                );
            }
        }

        Transaction transaction = new Transaction(
            request.getAccountId(),
            request.getAmount(),
            request.getCurrency(),
            request.getTransactionType(),
            request.getCounterparty(),
            request.getPaymentMethod(),
            request.getProviderReference(),
            request.getDescription(),
            request.getTransactionDate()
        );

        if (request.getCategoryId() != null) {
            transaction.setCategoryId(request.getCategoryId());
        }

        Transaction saved = transactionRepository.save(transaction);
        return TransactionResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionsByAccountId(UUID accountId, String status) {
        List<Transaction> transactions;
        if (status != null && !status.isBlank()) {
            transactions = transactionRepository.findByAccountIdAndStatusOrderByTransactionDateDesc(accountId, status.toUpperCase());
        } else {
            transactions = transactionRepository.findByAccountIdOrderByTransactionDateDesc(accountId);
        }

        return transactions.stream()
            .map(TransactionResponse::fromEntity)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(UUID id) {
        Transaction transaction = transactionRepository.findById(id)
            .orElseThrow(() -> new TransactionNotFoundException("Transaction not found with ID: " + id));
        return TransactionResponse.fromEntity(transaction);
    }

    public TransactionResponse updateCategory(UUID id, UUID categoryId) {
        Transaction transaction = transactionRepository.findById(id)
            .orElseThrow(() -> new TransactionNotFoundException("Transaction not found with ID: " + id));

        transaction.setCategoryId(categoryId);
        Transaction updated = transactionRepository.save(transaction);
        return TransactionResponse.fromEntity(updated);
    }

    public TransactionResponse reverseTransaction(UUID id, String reason) {
        Transaction original = transactionRepository.findById(id)
            .orElseThrow(() -> new TransactionNotFoundException("Transaction not found with ID: " + id));

        original.setStatus("REVERSED");
        transactionRepository.save(original);

        String oppositeType = "CREDIT".equalsIgnoreCase(original.getTransactionType()) ? "DEBIT" : "CREDIT";
        Transaction reversal = new Transaction(
            original.getAccountId(),
            original.getAmount(),
            original.getCurrency(),
            oppositeType,
            original.getCounterparty(),
            original.getPaymentMethod(),
            original.getProviderReference() != null ? "REV-" + original.getProviderReference() : null,
            "Reversal: " + (reason != null ? reason : original.getDescription()),
            OffsetDateTime.now()
        );
        reversal.setRelatedTransactionId(original.getId());
        reversal.setCategoryId(original.getCategoryId());

        Transaction savedReversal = transactionRepository.save(reversal);
        return TransactionResponse.fromEntity(savedReversal);
    }
}
