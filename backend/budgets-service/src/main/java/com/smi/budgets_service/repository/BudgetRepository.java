package com.smi.budgets_service.repository;

import com.smi.budgets_service.domain.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    List<Budget> findByOwnerIdAndStatus(UUID ownerId, String status);

    boolean existsByOwnerIdAndCategoryNameIgnoreCaseAndStatus(UUID ownerId, String categoryName, String status);
}
