package com.smi.investments_service.repository;

import com.smi.investments_service.domain.Investment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestmentRepository extends JpaRepository<Investment, UUID> {

    List<Investment> findByOwnerIdOrderByCreatedAtAsc(UUID ownerId);

    Optional<Investment> findByIdAndOwnerId(UUID id, UUID ownerId);
}
