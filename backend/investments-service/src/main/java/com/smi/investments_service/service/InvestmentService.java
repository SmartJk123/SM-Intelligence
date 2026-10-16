package com.smi.investments_service.service;

import com.smi.investments_service.domain.Investment;
import com.smi.investments_service.domain.InvestmentValuation;
import com.smi.investments_service.dto.InvestmentRequest;
import com.smi.investments_service.dto.InvestmentResponse;
import com.smi.investments_service.repository.InvestmentRepository;
import com.smi.investments_service.repository.InvestmentValuationRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * A customer's investments. Every operation is scoped to the owner, so one
 * customer can never read or change another customer's holdings.
 */
@Service
public class InvestmentService {

    /** What the app shows, mapped to the product_type values the schema allows. */
    private static final Map<String, String> PRODUCT_TYPES = Map.of(
            "Money Market", "FUND",
            "Fixed Deposit", "FIXED_DEPOSIT",
            "Treasury Bill", "TREASURY",
            "Equity", "EQUITY",
            "Other", "OTHER");

    private final InvestmentRepository investments;
    private final InvestmentValuationRepository valuations;

    public InvestmentService(InvestmentRepository investments, InvestmentValuationRepository valuations) {
        this.investments = investments;
        this.valuations = valuations;
    }

    @Transactional(readOnly = true)
    public List<InvestmentResponse> list(UUID ownerId) {
        return investments.findByOwnerIdOrderByCreatedAtAsc(ownerId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public InvestmentResponse create(InvestmentRequest request) {
        if (request.ownerId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ownerId is required");
        }
        Investment investment = new Investment(request.ownerId());
        apply(investment, request);
        investment = investments.save(investment);
        recordValuation(investment, request);
        return toResponse(investment);
    }

    @Transactional
    public InvestmentResponse update(UUID id, InvestmentRequest request) {
        Investment investment = owned(id, request.ownerId());
        apply(investment, request);
        recordValuation(investment, request);
        return toResponse(investment);
    }

    @Transactional
    public void delete(UUID id, UUID ownerId) {
        Investment investment = owned(id, ownerId);
        valuations.deleteByInvestmentId(investment.getId());
        investments.delete(investment);
    }

    private Investment owned(UUID id, UUID ownerId) {
        if (ownerId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ownerId is required");
        }
        return investments.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Investment not found"));
    }

    private void apply(Investment investment, InvestmentRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the investment name");
        }
        String productType = PRODUCT_TYPES.get(request.type());
        if (productType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Investment type must be one of " + String.join(", ", PRODUCT_TYPES.keySet()));
        }
        if (request.principal() == null || request.principal().signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a principal of 0 or more");
        }
        if (request.currentValue() != null && request.currentValue().signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The current value cannot be negative");
        }
        if (request.valuationDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the valuation date");
        }
        investment.setName(request.name().trim());
        investment.setProductType(productType);
        investment.setInstitution(request.institution() == null || request.institution().isBlank()
                ? "Not specified" : request.institution().trim());
        investment.setContributions(request.principal());
        investment.setMaturityDate(request.maturityDate());
        // The schema requires maturity_date >= start_date, and an older holding can
        // already have matured, so the start is the earlier of the two dates.
        LocalDate start = investment.getStartDate() == null ? request.valuationDate() : investment.getStartDate();
        if (request.maturityDate() != null && request.maturityDate().isBefore(start)) {
            start = request.maturityDate();
        }
        investment.setStartDate(start);
    }

    /** One valuation per day: saving again on the same day replaces that day's value. */
    private void recordValuation(Investment investment, InvestmentRequest request) {
        if (request.currentValue() == null) {
            return;
        }
        valuations.findByInvestmentIdAndValuationDate(investment.getId(), request.valuationDate())
                .ifPresentOrElse(
                        existing -> existing.setCurrentValue(request.currentValue()),
                        () -> valuations.save(new InvestmentValuation(
                                investment.getId(), request.valuationDate(), request.currentValue())));
    }

    private InvestmentResponse toResponse(Investment investment) {
        InvestmentValuation latest = valuations
                .findFirstByInvestmentIdOrderByValuationDateDesc(investment.getId()).orElse(null);
        String label = PRODUCT_TYPES.entrySet().stream()
                .filter(e -> e.getValue().equals(investment.getProductType()))
                .map(Map.Entry::getKey).findFirst().orElse("Other");
        BigDecimal currentValue = latest == null ? null : latest.getCurrentValue();
        LocalDate valuationDate = latest == null ? investment.getStartDate() : latest.getValuationDate();
        return new InvestmentResponse(investment.getId(), investment.getName(), label, investment.getInstitution(),
                investment.getCurrency(), investment.getContributions(), currentValue, valuationDate,
                investment.getMaturityDate(), investment.getStatus());
    }
}
