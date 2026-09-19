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
            @RequestParam UUID userId,
            @RequestParam(required = false) String status) {
        List<AccountResponse> accounts = accountService.getAccountsByUserId(userId, status);
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccountById(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID userId) {
        AccountResponse account = accountService.getAccountById(id, userId);
        return ResponseEntity.ok(account);
    }

    @PatchMapping("/{id}/balance")
    public ResponseEntity<AccountResponse> updateBalance(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBalanceRequest request) {
        AccountResponse updated = accountService.updateBalance(id, request);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<AccountResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest request) {
        AccountResponse updated = accountService.updateStatus(id, request);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<AccountResponse> closeAccount(@PathVariable UUID id) {
        AccountResponse closed = accountService.closeAccount(id);
        return ResponseEntity.ok(closed);
    }
}
