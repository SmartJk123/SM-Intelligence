package com.smi.budgets_service.controller;

import com.smi.budgets_service.dto.BudgetResponse;
import com.smi.budgets_service.dto.CreateBudgetRequest;
import com.smi.budgets_service.service.BudgetService;
import jakarta.validation.Valid;
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

import java.util.List;
import java.util.UUID;

/**
 * A category spending limit. Ownership is not verified against a signed-in
 * user's own token here, the same posture transactions-service already has:
 * a budget limit is low-sensitivity compared to a balance or a transfer.
 */
@RestController
@RequestMapping("/api/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @PostMapping
    public ResponseEntity<BudgetResponse> createBudget(@Valid @RequestBody CreateBudgetRequest request) {
        BudgetResponse created = budgetService.createBudget(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<BudgetResponse>> getBudgets(@RequestParam UUID userId) {
        return ResponseEntity.ok(budgetService.getBudgetsByUserId(userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> closeBudget(@PathVariable UUID id) {
        budgetService.closeBudget(id);
        return ResponseEntity.noContent().build();
    }
}
