package com.smi.accounts_service.controller;

import com.smi.accounts_service.dto.AccountResponse;
import com.smi.accounts_service.dto.CreateAccountRequest;
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
import org.springframework.web.bind.annotation.PatchMapping;
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
    public ResponseEntity<AccountResponse> createAccount(@RequestHeader(value="Authorization", required=false) String authorization, @Valid @RequestBody CreateAccountRequest request) {
        request.setUserId(identity.owner(authorization));
        AccountResponse created = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<AccountResponse>> getAccounts(
            @RequestHeader(value="Authorization", required=false) String authorization,
            @RequestParam(required = false) String status) {
        List<AccountResponse> accounts = accountService.getAccountsByUserId(identity.owner(authorization), status);
        return ResponseEntity.ok(accounts);
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

    @RequestMapping(value = "/{id}/status", method = {RequestMethod.PATCH, RequestMethod.PUT, RequestMethod.POST})
    public ResponseEntity<AccountResponse> updateStatus(
        @PathVariable String id,
        @RequestHeader(value="Authorization", required=false) String authorization,
        @Valid @RequestBody UpdateStatusRequest request) {
        
    try {
        UUID uuid = UUID.fromString(id);
        accountService.getAccountById(uuid, identity.owner(authorization));
        AccountResponse updated = accountService.updateStatus(uuid, request);
        return ResponseEntity.ok(updated);
    } catch (IllegalArgumentException e) {
        accountService.getAccountById(id, identity.owner(authorization));
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
}
