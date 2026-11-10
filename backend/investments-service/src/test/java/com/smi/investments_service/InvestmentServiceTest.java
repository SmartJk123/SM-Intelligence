package com.smi.investments_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smi.investments_service.dto.InvestmentRequest;
import com.smi.investments_service.dto.InvestmentResponse;
import com.smi.investments_service.service.InvestmentService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
class InvestmentServiceTest {

    @Autowired
    private InvestmentService service;

    private static InvestmentRequest request(UUID owner, String type, String principal, String value,
                                             LocalDate valued, LocalDate matures) {
        return new InvestmentRequest(owner, "Sacco fund", type, new BigDecimal(principal),
                value == null ? null : new BigDecimal(value), valued, matures, null, null);
    }

    @Test
    void savesAnInvestmentWithItsValueAndListsItForTheOwnerOnly() {
        UUID owner = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 10, 8);
        InvestmentResponse created = service.create(request(owner, "Money Market", "10000", "10450", today, null));

        assertThat(created.type()).isEqualTo("Money Market");
        assertThat(created.principal()).isEqualByComparingTo("10000");
        assertThat(created.currentValue()).isEqualByComparingTo("10450");
        assertThat(created.valuationDate()).isEqualTo(today);
        assertThat(created.institution()).isEqualTo("Not specified");

        assertThat(service.list(owner)).extracting(InvestmentResponse::id).containsExactly(created.id());
        assertThat(service.list(UUID.randomUUID())).isEmpty();
    }

    @Test
    void aLaterValuationBecomesTheCurrentValueAndTheSameDayIsReplaced() {
        UUID owner = UUID.randomUUID();
        LocalDate first = LocalDate.of(2026, 9, 1);
        InvestmentResponse created = service.create(request(owner, "Fixed Deposit", "50000", "50000", first, null));

        service.update(created.id(), request(owner, "Fixed Deposit", "50000", "51200", first.plusMonths(1), null));
        InvestmentResponse replaced = service.update(created.id(),
                request(owner, "Fixed Deposit", "50000", "51300", first.plusMonths(1), null));

        assertThat(replaced.currentValue()).isEqualByComparingTo("51300");
        assertThat(replaced.valuationDate()).isEqualTo(first.plusMonths(1));
    }

    @Test
    void anotherCustomerCannotChangeOrDeleteTheInvestment() {
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        InvestmentResponse created = service.create(
                request(owner, "Treasury Bill", "20000", null, LocalDate.of(2026, 10, 1), null));

        assertThatThrownBy(() -> service.update(created.id(),
                request(stranger, "Treasury Bill", "1", null, LocalDate.of(2026, 10, 1), null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.delete(created.id(), stranger))
                .isInstanceOf(ResponseStatusException.class);

        service.delete(created.id(), owner);
        assertThat(service.list(owner)).isEmpty();
    }

    @Test
    void aHoldingThatAlreadyMaturedIsAccepted() {
        UUID owner = UUID.randomUUID();
        InvestmentResponse created = service.create(request(owner, "Treasury Bill", "20000", "21000",
                LocalDate.of(2026, 10, 8), LocalDate.of(2026, 6, 30)));

        assertThat(created.maturityDate()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(created.currentValue()).isEqualByComparingTo("21000");
    }

    @Test
    void refusesAnUnknownTypeAndANegativePrincipal() {
        UUID owner = UUID.randomUUID();
        assertThatThrownBy(() -> service.create(request(owner, "Crypto", "1", null, LocalDate.now(), null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.create(request(owner, "Other", "-5", null, LocalDate.now(), null)))
                .isInstanceOf(ResponseStatusException.class);
    }
    @Test
    void keepsTheInterestRateAndStartDateAndRefusesARateOutsideZeroToHundred() {
        UUID owner = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 1, 15);
        InvestmentRequest withRate = new InvestmentRequest(owner, "Fixed deposit", "Fixed Deposit", new BigDecimal("100000"),
                new BigDecimal("100000"), start, LocalDate.of(2036, 1, 15), null, new BigDecimal("12.5"));
        InvestmentResponse created = service.create(withRate);
        assertThat(created.interestRate()).isEqualByComparingTo("12.5");
        assertThat(created.startDate()).isEqualTo(start);

        // An update without a rate (an older client) keeps the saved one.
        InvestmentResponse updated = service.update(created.id(), new InvestmentRequest(owner, "Fixed deposit", "Fixed Deposit",
                new BigDecimal("100000"), new BigDecimal("101000"), start.plusMonths(1), LocalDate.of(2036, 1, 15), null, null));
        assertThat(updated.interestRate()).isEqualByComparingTo("12.5");

        assertThatThrownBy(() -> service.create(new InvestmentRequest(owner, "Bad", "Other", BigDecimal.ONE, null, start, null, null,
                new BigDecimal("101"))))
                .isInstanceOf(ResponseStatusException.class);
    }
}
