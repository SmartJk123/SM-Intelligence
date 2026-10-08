package com.smi.accounts_service.controller;

import com.smi.accounts_service.dto.AccountResponse;
import com.smi.accounts_service.dto.AccountSummaryResponse;
import com.smi.accounts_service.dto.AdjustBalanceRequest;
import com.smi.accounts_service.dto.CreateAccountRequest;
import com.smi.accounts_service.dto.UpdateAccountRequest;
import com.smi.accounts_service.dto.UpdateBalanceRequest;
import com.smi.accounts_service.dto.UpdateStatusRequest;
import com.smi.accounts_service.service.AccountService;
import jakarta.validation.Valid;
import com.smi.accounts_service.service.AccountIdentity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;
    private final AccountIdentity identity;

    public AccountController(AccountService accountService, AccountIdentity identity) {
        this.accountService = accountService; this.identity = identity;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            @RequestHeader(value="Authorization", required=false) String authorization,
            @RequestHeader(value="X-Internal-Token", required=false) String internalToken,
            @Valid @RequestBody CreateAccountRequest request) {
        if (!identity.isInternalService(internalToken)) {
            request.setUserId(identity.owner(authorization));
        }
        AccountResponse created = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<AccountResponse>> getAccounts(
            @RequestHeader(value="Authorization", required=false) String authorization,
            @RequestHeader(value="X-Internal-Token", required=false) String internalToken,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String accountType,
            @RequestParam(required = false) String institution,
            @RequestParam(required = false) String connectionStatus) {
        UUID owner = identity.isInternalService(internalToken) ? UUID.fromString(userId) : identity.owner(authorization);
        List<AccountResponse> accounts = accountService.getAccountsByUserId(owner, status, accountType, institution, connectionStatus);
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/summary")
    public ResponseEntity<AccountSummaryResponse> getAccountSummary(
            @RequestHeader(value="Authorization", required=false) String authorization,
            @RequestHeader(value="X-Internal-Token", required=false) String internalToken,
            @RequestParam(required = false) String userId) {
        UUID owner = identity.isInternalService(internalToken) ? UUID.fromString(userId) : identity.owner(authorization);
        AccountSummaryResponse summary = accountService.getAccountSummary(owner);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccountById(
      @PathVariable String id,
      @RequestParam(required = false) UUID userId,
      @RequestHeader(value="Authorization", required=false) String authorization) {
        
    try {
        UUID uuid = UUID.fromString(id);
        AccountResponse account = accountService.getAccountById(uuid, identity.owner(authorization));
        return ResponseEntity.ok(account);
    } catch (IllegalArgumentException e) {
        AccountResponse account = accountService.getAccountById(id, identity.owner(authorization));
        return ResponseEntity.ok(account);
    }
}

    @RequestMapping(value = "/{id}/balance", method = {RequestMethod.PATCH, RequestMethod.PUT, RequestMethod.POST})
    public ResponseEntity<AccountResponse> updateBalance(
      @PathVariable String id,
      @RequestHeader(value="Authorization", required=false) String authorization, 
      @Valid @RequestBody UpdateBalanceRequest request) {
        
    try {
        UUID uuid = UUID.fromString(id);
        accountService.getAccountById(uuid, identity.owner(authorization));
        AccountResponse updated = accountService.updateBalance(uuid, request);
        return ResponseEntity.ok(updated);
    } catch (IllegalArgumentException e) {
        accountService.getAccountById(id, identity.owner(authorization));
        AccountResponse updated = accountService.updateBalance(id, request);
        return ResponseEntity.ok(updated);
    }
}

    /**
     * Moves the balance by a posted transaction's amount instead of replacing it,
     * so a linked bank account's balance tracks real activity. Only
     * bank-integration-service calls this, proven by the internal token, since
     * nobody else should be able to move a balance without a matching transaction.
     */
    @RequestMapping(value = "/{id}/balance/adjust", method = RequestMethod.PATCH)
    public ResponseEntity<AccountResponse> adjustBalance(
            @PathVariable UUID id,
            @RequestHeader(value="X-Internal-Token", required=false) String internalToken,
            @Valid @RequestBody AdjustBalanceRequest request) {
        if (!identity.isInternalService(internalToken)) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only a trusted internal service may adjust a balance directly");
        }
        AccountResponse updated = accountService.adjustBalance(id, request.getDelta());
        return ResponseEntity.ok(updated);
    }

    @RequestMapping(value = "/{id}/status", method = {RequestMethod.PATCH, RequestMethod.PUT, RequestMethod.POST})
    public ResponseEntity<AccountResponse> updateStatus(
        @PathVariable String id,
        @RequestHeader(value="Authorization", required=false) String authorization,
        @RequestHeader(value="X-Internal-Token", required=false) String internalToken,
        @Valid @RequestBody UpdateStatusRequest request) {
        boolean internal = identity.isInternalService(internalToken);

    try {
        UUID uuid = UUID.fromString(id);
        if (!internal) {
            accountService.getAccountById(uuid, identity.owner(authorization));
        }
        AccountResponse updated = accountService.updateStatus(uuid, request);
        return ResponseEntity.ok(updated);
    } catch (IllegalArgumentException e) {
        if (!internal) {
            accountService.getAccountById(id, identity.owner(authorization));
        }
        AccountResponse updated = accountService.updateStatus(id, request);
        return ResponseEntity.ok(updated);
    }
}

@DeleteMapping("/{id}")
public ResponseEntity<AccountResponse> deleteAccount(
        @PathVariable String id,
        @RequestParam(required = false) Boolean permanent,
        @RequestHeader(value="Authorization", required=false) String authorization) {
            
    if (Boolean.FALSE.equals(permanent)) {
        try {
            UUID uuid = UUID.fromString(id);
            accountService.getAccountById(uuid, identity.owner(authorization));
            AccountResponse closed = accountService.closeAccount(uuid);
            return ResponseEntity.ok(closed);
        } catch (IllegalArgumentException e) {
            accountService.getAccountById(id, identity.owner(authorization));
            AccountResponse closed = accountService.closeAccount(id);
            return ResponseEntity.ok(closed);
        }
    }
    
    try {
        UUID uuid = UUID.fromString(id);
        accountService.getAccountById(uuid, identity.owner(authorization));
        AccountResponse deleted = accountService.deleteAccount(uuid);
        return ResponseEntity.ok(deleted);
    } catch (IllegalArgumentException e) {
        accountService.getAccountById(id, identity.owner(authorization));
        AccountResponse deleted = accountService.deleteAccount(id);
        return ResponseEntity.ok(deleted);
    }
}

    @RequestMapping(value = "/{id}/rename", method = {RequestMethod.PATCH, RequestMethod.PUT, RequestMethod.POST})
    public ResponseEntity<AccountResponse> renameAccount(
            @PathVariable UUID id,
            @RequestHeader(value="Authorization", required=false) String authorization,
            @Valid @RequestBody UpdateAccountRequest request) {
        UUID owner = identity.owner(authorization);
        AccountResponse updated = accountService.renameAccount(id, owner, request.getAccountName());
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/sync")
    public ResponseEntity<AccountResponse> syncAccount(
            @PathVariable UUID id,
            @RequestHeader(value="Authorization", required=false) String authorization) {
        UUID owner = identity.owner(authorization);
        AccountResponse updated = accountService.syncAccount(id, owner);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/reopen")
    public ResponseEntity<AccountResponse> reopenAccount(
            @PathVariable UUID id,
            @RequestHeader(value="Authorization", required=false) String authorization) {
        UUID owner = identity.owner(authorization);
        AccountResponse updated = accountService.reopenAccount(id, owner);
        return ResponseEntity.ok(updated);
    }
}
