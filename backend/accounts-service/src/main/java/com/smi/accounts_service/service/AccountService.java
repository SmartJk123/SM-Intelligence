package com.smi.accounts_service.service;

import com.smi.accounts_service.domain.Account;
import com.smi.accounts_service.dto.AccountResponse;
import com.smi.accounts_service.dto.CreateAccountRequest;
import com.smi.accounts_service.dto.UpdateBalanceRequest;
import com.smi.accounts_service.dto.UpdateStatusRequest;
import com.smi.accounts_service.exception.AccountNotFoundException;
import com.smi.accounts_service.exception.DuplicateAccountException;
import com.smi.accounts_service.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public AccountResponse createAccount(CreateAccountRequest request) {
        if (accountRepository.existsByInstitutionAndProviderAccountId(request.getInstitution(), request.getProviderAccountId())) {
            throw new DuplicateAccountException(
                String.format("Account with institution '%s' and provider account ID '%s' already exists",
                    request.getInstitution(), request.getProviderAccountId())
            );
        }

        Account account = new Account(
            request.getUserId(),
            request.getProviderAccountId(),
            request.getAccountName(),
            request.getInstitution(),
            request.getAccountType(),
            request.getMaskedIdentifier(),
            request.getCurrency(),
            request.getInitialBalance()
        );

        if (request.getCreditLimit() != null) {
            account.setCreditLimit(request.getCreditLimit());
        }
        if (request.getDataSource() != null) {
            account.setDataSource(request.getDataSource());
        }

        Account saved = accountRepository.save(account);
        return AccountResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountsByUserId(UUID userId, String status) {
        List<Account> accounts;
        if (status != null && !status.isBlank()) {
            accounts = accountRepository.findByUserIdAndAccountStatus(userId, status.toUpperCase());
        } else {
            accounts = accountRepository.findByUserId(userId);
        }

        return accounts.stream()
            .map(AccountResponse::fromEntity)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountById(UUID id, UUID userId) {
        Account account;
        if (userId != null) {
            account = accountRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found with ID: " + id + " for user: " + userId));
        } else {
            account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("Account not found with ID: " + id));
        }
        return AccountResponse.fromEntity(account);
    }

    public AccountResponse updateBalance(UUID id, UpdateBalanceRequest request) {
        Account account = accountRepository.findById(id)
            .orElseThrow(() -> new AccountNotFoundException("Account not found with ID: " + id));

        account.setAvailableBalance(request.getAvailableBalance());
        if (request.getLedgerBalance() != null) {
            account.setLedgerBalance(request.getLedgerBalance());
        }
        if (request.getCreditOutstanding() != null) {
            account.setCreditOutstanding(request.getCreditOutstanding());
        }
        account.setLastUpdated(OffsetDateTime.now());

        Account updated = accountRepository.save(account);
        return AccountResponse.fromEntity(updated);
    }

    public AccountResponse updateStatus(UUID id, UpdateStatusRequest request) {
        Account account = accountRepository.findById(id)
            .orElseThrow(() -> new AccountNotFoundException("Account not found with ID: " + id));

        if (request.getAccountStatus() != null) {
            account.setAccountStatus(request.getAccountStatus());
        }
        if (request.getConnectionStatus() != null) {
            account.setConnectionStatus(request.getConnectionStatus());
        }
        account.setLastUpdated(OffsetDateTime.now());

        Account updated = accountRepository.save(account);
        return AccountResponse.fromEntity(updated);
    }

    public AccountResponse closeAccount(UUID id) {
        Account account = accountRepository.findById(id)
            .orElseThrow(() -> new AccountNotFoundException("Account not found with ID: " + id));

        account.setAccountStatus("CLOSED");
        account.setConnectionStatus("DISCONNECTED");
        account.setLastUpdated(OffsetDateTime.now());

        Account updated = accountRepository.save(account);
        return AccountResponse.fromEntity(updated);
    }
}
