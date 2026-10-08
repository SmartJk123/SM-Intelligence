package com.smi.accounts_service.service;

import com.smi.accounts_service.domain.Account;
import com.smi.accounts_service.dto.AccountResponse;
import com.smi.accounts_service.dto.AccountSummaryResponse;
import com.smi.accounts_service.dto.CreateAccountRequest;
import com.smi.accounts_service.dto.UpdateBalanceRequest;
import com.smi.accounts_service.dto.UpdateStatusRequest;
import com.smi.accounts_service.exception.AccountNotFoundException;
import com.smi.accounts_service.exception.DuplicateAccountException;
import com.smi.accounts_service.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
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

        Optional<Account> holder = findHolder(effectiveInstitution, request.getProviderAccountId());
        if (holder.isPresent()) {
            throw new DuplicateAccountException(!holder.get().getUserId().equals(effectiveUserId)
                ? "This account number is already registered to another user"
                : "MANUAL".equals(holder.get().getDataSource())
                    ? "This customer already added this account themselves. Link it to that account instead."
                    : "This account is already registered to this user");
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

    /**
     * The account already registered under this bank and account number, by
     * any user, however it was added. A bank account number belongs to one
     * person, so it may be registered only once across the platform.
     *
     * Bank-linked accounts store the plain number. Self-entered accounts store
     * a fingerprint instead; older ones mixed the owner into it, so those are
     * matched by trying each owner of a self-entered account at that bank.
     */
    @Transactional(readOnly = true)
    public Optional<Account> findHolder(String institution, String accountNumber) {
        if (institution == null || accountNumber == null || accountNumber.isBlank()) {
            return Optional.empty();
        }
        String number = accountNumber.trim();
        Optional<Account> holder = accountRepository.findByInstitutionAndProviderAccountId(institution, number)
            .or(() -> accountRepository.findByInstitutionAndProviderAccountId(institution, fingerprint(institution, number)));
        if (holder.isPresent()) {
            return holder;
        }
        for (UUID owner : accountRepository.findManualOwnersByInstitution(institution)) {
            holder = accountRepository.findByInstitutionAndProviderAccountId(institution,
                sha256(owner + ":" + institution + ":" + number));
            if (holder.isPresent()) {
                return holder;
            }
        }
        return Optional.empty();
    }

    /** What a self-entered account stores in place of its number. The same for every user, so the unique constraint applies. */
    public static String fingerprint(String institution, String accountNumber) {
        return sha256("account:" + institution + ":" + accountNumber.trim());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountsByUserId(UUID userId, String status) {
        return getAccountsByUserId(userId, status, null, null, null);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountsByUserId(
            UUID userId,
            String status,
            String accountType,
            String institution,
            String connectionStatus
    ) {
        if (userId == null) {
            return Collections.emptyList();
        }

        List<Account> accounts = accountRepository.findByUserId(userId);
        return accounts.stream()
                .filter(a -> status == null || status.isBlank() || a.getAccountStatus().equalsIgnoreCase(status.trim()))
                .filter(a -> accountType == null || accountType.isBlank() || a.getAccountType().equalsIgnoreCase(accountType.trim()))
                .filter(a -> institution == null || institution.isBlank() || a.getInstitution().equalsIgnoreCase(institution.trim()))
                .filter(a -> connectionStatus == null || connectionStatus.isBlank() || a.getConnectionStatus().equalsIgnoreCase(connectionStatus.trim()))
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

    /** Moves the balance by delta (negative for a debit) and returns the new figure. */
    public AccountResponse adjustBalance(UUID id, BigDecimal delta) {
        int updated = accountRepository.adjustAvailableBalance(id, delta, OffsetDateTime.now());
        if (updated == 0) {
            throw new AccountNotFoundException("Account not found with ID: " + id);
        }
        Account account = accountRepository.findById(id)
            .orElseThrow(() -> new AccountNotFoundException("Account not found with ID: " + id));
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

    @Transactional(readOnly = true)
    public AccountSummaryResponse getAccountSummary(UUID userId) {
        if (userId == null) {
            return new AccountSummaryResponse(
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, 0, 0,
                    "KES", Collections.emptyMap(), Collections.emptyMap()
            );
        }

        List<Account> accounts = accountRepository.findByUserId(userId);
        BigDecimal totalDeposits = BigDecimal.ZERO;
        BigDecimal totalCreditDebt = BigDecimal.ZERO;
        BigDecimal totalCreditLimit = BigDecimal.ZERO;
        int activeCount = 0;
        String currency = "KES";

        java.util.Map<String, BigDecimal> instMap = new java.util.HashMap<>();
        java.util.Map<String, BigDecimal> typeMap = new java.util.HashMap<>();

        for (Account a : accounts) {
            if (a.getCurrency() != null && !a.getCurrency().isBlank()) {
                currency = a.getCurrency();
            }
            boolean isActive = "ACTIVE".equalsIgnoreCase(a.getAccountStatus());
            if (isActive) {
                activeCount++;
            }

            BigDecimal bal = a.getAvailableBalance() != null ? a.getAvailableBalance() : BigDecimal.ZERO;
            if ("CREDIT".equalsIgnoreCase(a.getAccountType())) {
                BigDecimal debt = a.getCreditOutstanding() != null ? a.getCreditOutstanding() : BigDecimal.ZERO;
                BigDecimal limit = a.getCreditLimit() != null ? a.getCreditLimit() : BigDecimal.ZERO;
                totalCreditDebt = totalCreditDebt.add(debt);
                totalCreditLimit = totalCreditLimit.add(limit);
                typeMap.merge("CREDIT", debt, BigDecimal::add);
            } else {
                totalDeposits = totalDeposits.add(bal);
                typeMap.merge("DEPOSIT", bal, BigDecimal::add);
            }

            instMap.merge(a.getInstitution(), bal, BigDecimal::add);
        }

        BigDecimal netWorth = totalDeposits.subtract(totalCreditDebt);
        BigDecimal totalAvailableCredit = totalCreditLimit.subtract(totalCreditDebt).max(BigDecimal.ZERO);

        return new AccountSummaryResponse(
                netWorth,
                totalDeposits,
                totalCreditDebt,
                totalCreditLimit,
                totalAvailableCredit,
                accounts.size(),
                activeCount,
                currency,
                instMap,
                typeMap
        );
    }

    public AccountResponse renameAccount(UUID id, UUID userId, String newName) {
        Account account = findAccountByIdentifier(id.toString());
        if (userId != null && !account.getUserId().equals(userId)) {
            throw new AccountNotFoundException("Account not found with ID: " + id);
        }
        if (newName != null && !newName.isBlank()) {
            account.setAccountName(newName.trim());
            account.setLastUpdated(OffsetDateTime.now());
        }
        Account updated = accountRepository.save(account);
        return AccountResponse.fromEntity(updated);
    }

    public AccountResponse syncAccount(UUID id, UUID userId) {
        Account account = findAccountByIdentifier(id.toString());
        if (userId != null && !account.getUserId().equals(userId)) {
            throw new AccountNotFoundException("Account not found with ID: " + id);
        }
        account.setConnectionStatus("CONNECTED");
        account.setLastUpdated(OffsetDateTime.now());
        Account updated = accountRepository.save(account);
        return AccountResponse.fromEntity(updated);
    }

    public AccountResponse reopenAccount(UUID id, UUID userId) {
        Account account = findAccountByIdentifier(id.toString());
        if (userId != null && !account.getUserId().equals(userId)) {
            throw new AccountNotFoundException("Account not found with ID: " + id);
        }
        account.setAccountStatus("ACTIVE");
        account.setConnectionStatus("CONNECTED");
        account.setLastUpdated(OffsetDateTime.now());
        Account updated = accountRepository.save(account);
        return AccountResponse.fromEntity(updated);
    }
}
