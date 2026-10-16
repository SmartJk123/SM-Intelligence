package com.smi.investments_service.repository;

import com.smi.investments_service.domain.InvestmentValuation;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestmentValuationRepository extends JpaRepository<InvestmentValuation, UUID> {

    Optional<InvestmentValuation> findFirstByInvestmentIdOrderByValuationDateDesc(UUID investmentId);

    Optional<InvestmentValuation> findByInvestmentIdAndValuationDate(UUID investmentId, LocalDate valuationDate);

    void deleteByInvestmentId(UUID investmentId);
}
