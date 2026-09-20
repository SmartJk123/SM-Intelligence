package io.smartmoney.api.bankintegration;

import java.time.Instant;

/**
 * Health of one bank integration. Matches BankHealth in the Angular application.
 *
 * Every figure is measured. A bank that has never sent a notification reports
 * zero, and a value that has not been measured is null rather than a guess.
 */
public record IntegrationHealth(
        String bankId,
        BankEnvironment environment,
        IntegrationStatus apiStatus,
        IntegrationStatus webhookStatus,
        TokenStatus tokenStatus,
        Integer tokenExpiresInMinutes,
        Instant lastTokenRefresh,
        Instant lastWebhookReceived,
        Instant lastSuccessfulRequest,
        Integer latencyMs,
        long errorsLast24h,
        long notificationsTotal,
        long notificationsToday,
        Instant checkedAt) {
}
