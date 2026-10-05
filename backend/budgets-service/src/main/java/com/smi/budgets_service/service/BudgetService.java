package com.smi.budgets_service.service;

import com.smi.budgets_service.domain.Budget;
import com.smi.budgets_service.dto.BudgetResponse;
import com.smi.budgets_service.dto.CreateBudgetRequest;
import com.smi.budgets_service.exception.BudgetNotFoundException;
import com.smi.budgets_service.exception.DuplicateBudgetException;
import com.smi.budgets_service.repository.BudgetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class BudgetService {

    private final BudgetRepository budgetRepository;

    public BudgetService(BudgetRepository budgetRepository) {
        this.budgetRepository = budgetRepository;
    }

    public BudgetResponse createBudget(CreateBudgetRequest request) {
        if (budgetRepository.existsByOwnerIdAndCategoryNameIgnoreCaseAndStatus(
                request.getUserId(), request.getCategory(), "ACTIVE")) {
            throw new DuplicateBudgetException(
                    "A budget for \"" + request.getCategory() + "\" already exists");
        }
        Budget budget = new Budget(request.getUserId(), request.getCategory(), request.getMonthlyLimit());
        return BudgetResponse.fromEntity(budgetRepository.save(budget));
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> getBudgetsByUserId(UUID userId) {
        return budgetRepository.findByOwnerIdAndStatus(userId, "ACTIVE").stream()
                .map(BudgetResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public void closeBudget(UUID id) {
        Budget budget = budgetRepository.findById(id)
                .orElseThrow(() -> new BudgetNotFoundException("Budget not found with ID: " + id));
        budget.setStatus("CLOSED");
        budgetRepository.save(budget);
    }
}
