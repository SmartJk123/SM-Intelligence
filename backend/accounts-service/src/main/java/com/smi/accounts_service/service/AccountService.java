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
import java.util.Collections;
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
        UUID effectiveUserId = request.getUserId() != null 
            ? request.getUserId() 
            : UUID.fromString("00000000-0000-0000-0000-000000000001");
        String effectiveInstitution = (request.getInstitution() != null && !request.getInstitution().isBlank())
            ? request.getInstitution().trim()
            : "Default Bank";
        String effectiveAccountType = request.getAccountType() != null
            ? request.getAccountType().trim().toUpperCase()
            : "DEPOSIT";
        if (effectiveAccountType.equals("DEBIT") || effectiveAccountType.equals("SAVINGS") || effectiveAccountType.equals("CHECKING")) {
            effectiveAccountType = "DEPOSIT";
        }
        String effectiveAccountName = (request.getAccountName() != null && !request.getAccountName().isBlank())
            ? request.getAccountName().trim()
            : effectiveInstitution + " Account";
        String effectiveMaskedId = request.getMaskedIdentifier();
        if (effectiveMaskedId == null || effectiveMaskedId.isBlank()) {
            String provId = request.getProviderAccountId() != null ? request.getProviderAccountId().trim() : "";
            effectiveMaskedId = provId.length() > 4 ? "**** " + provId.substring(provId.length() - 4) : "**** " + provId;
        }

        if (accountRepository.existsByInstitutionAndProviderAccountId(effectiveInstitution, request.getProviderAccountId())) {
            throw new DuplicateAccountException(
                String.format("Account with institution '%s' and provider account ID '%s' already exists",
                    effectiveInstitution, request.getProviderAccountId())
            );
        }

        Account account = new Account(
            effectiveUserId,
            request.getProviderAccountId(),
            effectiveAccountName,
            effectiveInstitution,
            effectiveAccountType,
            effectiveMaskedId,
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
        if (userId == null) {
            return Collections.emptyList();
        }

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
    public Account findAccountByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new AccountNotFoundException("Account identifier cannot be empty");
        }
        try {
            UUID uuid = UUID.fromString(identifier.trim());
            return accountRepository.findById(uuid)
                .or(() -> accountRepository.findByProviderAccountId(identifier.trim()).stream().findFirst())
                .orElseThrow(() -> new AccountNotFoundException("Account not found with ID or account number: " + identifier));
        } catch (IllegalArgumentException e) {
            return accountRepository.findByProviderAccountId(identifier.trim()).stream().findFirst()
                .orElseThrow(() -> new AccountNotFoundException("Account not found with account number: " + identifier));
        }
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountById(UUID id, UUID userId) {
        return getAccountById(id.toString(), userId);
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountById(String identifier, UUID userId) {
        Account account = findAccountByIdentifier(identifier);
        if (userId != null && !account.getUserId().equals(userId)) {
            throw new AccountNotFoundException("Account not found with identifier: " + identifier + " for user: " + userId);
        }
        return AccountResponse.fromEntity(account);
    }

    public AccountResponse updateBalance(UUID id, UpdateBalanceRequest request) {
        return updateBalance(id.toString(), request);
    }

    public AccountResponse updateBalance(String identifier, UpdateBalanceRequest request) {
        Account account = findAccountByIdentifier(identifier);

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
        return updateStatus(id.toString(), request);
    }

    public AccountResponse updateStatus(String identifier, UpdateStatusRequest request) {
        Account account = findAccountByIdentifier(identifier);

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
        return closeAccount(id.toString());
    }

    public AccountResponse closeAccount(String identifier) {
        Account account = findAccountByIdentifier(identifier);

        account.setAccountStatus("CLOSED");
        account.setConnectionStatus("DISCONNECTED");
        account.setLastUpdated(OffsetDateTime.now());

        Account updated = accountRepository.save(account);
        return AccountResponse.fromEntity(updated);
    }

    public AccountResponse deleteAccount(UUID id) {
        return deleteAccount(id.toString());
    }

    public AccountResponse deleteAccount(String identifier) {
        Account account = findAccountByIdentifier(identifier);
        AccountResponse response = AccountResponse.fromEntity(account);
        accountRepository.delete(account);
        return response;
    }
}
