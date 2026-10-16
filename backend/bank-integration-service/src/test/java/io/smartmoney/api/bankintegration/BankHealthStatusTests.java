package io.smartmoney.api.bankintegration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** How a bank's state maps to an actuator status. */
class BankHealthStatusTests {

    private static IntegrationHealth bank(boolean connector, IntegrationStatus api, IntegrationStatus webhook) {
        return bank(BankEnvironment.PRODUCTION, connector, api, webhook);
    }

    private static IntegrationHealth bank(BankEnvironment environment, boolean connector,
                                          IntegrationStatus api, IntegrationStatus webhook) {
        return new IntegrationHealth("kcb", environment, api, webhook, TokenStatus.UNKNOWN, null, null,
                null, null, null, 0, 0, 0, connector, Instant.now());
    }

    @Test
    void mapsEachStateToAStatus() {
        assertThat(BankHealthIndicators.statusOf(bank(false, IntegrationStatus.PENDING, IntegrationStatus.PENDING)))
                .isEqualTo(Status.UNKNOWN);
        assertThat(BankHealthIndicators.statusOf(bank(true, IntegrationStatus.PENDING, IntegrationStatus.PENDING)))
                .isEqualTo(Status.UNKNOWN);
        assertThat(BankHealthIndicators.statusOf(bank(true, IntegrationStatus.HEALTHY, IntegrationStatus.PENDING)))
                .isEqualTo(Status.UP);
        assertThat(BankHealthIndicators.statusOf(bank(true, IntegrationStatus.PENDING, IntegrationStatus.HEALTHY)))
                .isEqualTo(Status.UP);
        assertThat(BankHealthIndicators.statusOf(bank(true, IntegrationStatus.ERROR, IntegrationStatus.HEALTHY)))
                .isEqualTo(BankHealthIndicators.DEGRADED);
        assertThat(BankHealthIndicators.statusOf(bank(true, IntegrationStatus.HEALTHY, IntegrationStatus.ERROR)))
                .isEqualTo(BankHealthIndicators.DEGRADED);
    }

    @Test
    void aFailingSandboxBankDoesNotDegradeTheService() {
        // A sandbox bank that is not set up yet must not email the administrators every hour.
        assertThat(BankHealthIndicators.statusOf(
                bank(BankEnvironment.SANDBOX, true, IntegrationStatus.ERROR, IntegrationStatus.PENDING)))
                .isEqualTo(Status.UNKNOWN);
        assertThat(BankHealthIndicators.statusOf(
                bank(BankEnvironment.SANDBOX, true, IntegrationStatus.HEALTHY, IntegrationStatus.ERROR)))
                .isEqualTo(Status.UNKNOWN);
        assertThat(BankHealthIndicators.statusOf(
                bank(BankEnvironment.PRODUCTION, true, IntegrationStatus.ERROR, IntegrationStatus.PENDING)))
                .isEqualTo(BankHealthIndicators.DEGRADED);
    }
}
