package com.smi.accounts_service.controller;

import com.smi.accounts_service.dto.AccountResponse;
import com.smi.accounts_service.dto.CreateAccountRequest;
import com.smi.accounts_service.dto.UpdateBalanceRequest;
import com.smi.accounts_service.dto.UpdateStatusRequest;
import com.smi.accounts_service.service.AccountService;
import jakarta.validation.Valid;
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

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        AccountResponse created = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<AccountResponse>> getAccounts(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String status) {
        List<AccountResponse> accounts = accountService.getAccountsByUserId(userId, status);
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccountById(
            @PathVariable String id,
            @RequestParam(required = false) UUID userId) {
        try {
            UUID uuid = UUID.fromString(id);
            return ResponseEntity.ok(accountService.getAccountById(uuid, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(accountService.getAccountById(id, userId));
        }
    }

    @RequestMapping(value = "/{id}/balance", method = {RequestMethod.PATCH, RequestMethod.PUT, RequestMethod.POST})
    public ResponseEntity<AccountResponse> updateBalance(
            @PathVariable String id,
            @Valid @RequestBody UpdateBalanceRequest request) {
        try {
            UUID uuid = UUID.fromString(id);
            return ResponseEntity.ok(accountService.updateBalance(uuid, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(accountService.updateBalance(id, request));
        }
    }

    @RequestMapping(value = "/{id}/status", method = {RequestMethod.PATCH, RequestMethod.PUT, RequestMethod.POST})
    public ResponseEntity<AccountResponse> updateStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateStatusRequest request) {
        try {
            UUID uuid = UUID.fromString(id);
            return ResponseEntity.ok(accountService.updateStatus(uuid, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(accountService.updateStatus(id, request));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<AccountResponse> deleteAccount(
            @PathVariable String id,
            @RequestParam(required = false) Boolean permanent) {
        if (Boolean.FALSE.equals(permanent)) {
            try {
                UUID uuid = UUID.fromString(id);
                return ResponseEntity.ok(accountService.closeAccount(uuid));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.ok(accountService.closeAccount(id));
            }
        }
        try {
            UUID uuid = UUID.fromString(id);
            return ResponseEntity.ok(accountService.deleteAccount(uuid));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(accountService.deleteAccount(id));
        }
    }
}
