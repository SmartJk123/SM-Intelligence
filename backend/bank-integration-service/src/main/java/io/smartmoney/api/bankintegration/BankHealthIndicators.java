package io.smartmoney.api.bankintegration;

import org.springframework.boot.health.contributor.CompositeHealthContributor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One actuator health indicator per bank, under /actuator/health/banks
 * (for example /actuator/health/banks/kcb).
 *
 *   UP        the last connection test passed, or notifications are arriving
 *   DEGRADED  a PRODUCTION bank's last connection test failed, or its notifications failed
 *   UNKNOWN   no connector, nothing tested or received yet, or a sandbox bank failing
 *
 * Only production banks can degrade the service: they carry real money, and a
 * DEGRADED service emails the administrators every hour (identity-service's
 * ServiceHealthMonitor). A sandbox bank that is not set up yet still shows its
 * ERROR in the details and on the admin portal, without paging anyone.
 *
 * DEGRADED is a custom status ranked below DOWN, so one bank failing marks
 * the service degraded rather than down, and the HTTP answer stays 200: a
 * monitor should alert on it, not restart the service (see application.yml).
 * The indicators only read what the service already knows. They never call a
 * bank, so a health probe cannot use up a bank's rate limit.
 */
@Configuration
public class BankHealthIndicators {

    static final Status DEGRADED = new Status("DEGRADED");
    static final List<String> BANKS = List.of("kcb", "ncba", "stanbic", "equity");

    @Bean
    CompositeHealthContributor banks(BankIntegrationHealthService health) {
        Map<String, HealthIndicator> indicators = new LinkedHashMap<>();
        for (String bankId : BANKS) {
            indicators.put(bankId, () -> indicatorFor(health.healthFor(bankId)));
        }
        return CompositeHealthContributor.fromMap(indicators);
    }

    static Health indicatorFor(IntegrationHealth bank) {
        Health.Builder builder = new Health.Builder(statusOf(bank))
                .withDetail("environment", bank.environment() == null ? "Sandbox" : bank.environment().value())
                .withDetail("connectorImplemented", bank.connectorImplemented())
                .withDetail("connection", bank.apiStatus().name())
                .withDetail("notifications", bank.webhookStatus().name())
                .withDetail("token", bank.tokenStatus().name())
                .withDetail("notificationsToday", bank.notificationsToday())
                .withDetail("failedNotificationsLast24h", bank.errorsLast24h());
        if (bank.lastWebhookReceived() != null) {
            builder.withDetail("lastNotificationAt", bank.lastWebhookReceived().toString());
        }
        if (bank.lastSuccessfulRequest() != null) {
            builder.withDetail("lastConnectionTestAt", bank.lastSuccessfulRequest().toString());
        }
        return builder.build();
    }

    static Status statusOf(IntegrationHealth bank) {
        if (!bank.connectorImplemented()) {
            return Status.UNKNOWN;
        }
        if (bank.apiStatus() == IntegrationStatus.ERROR || bank.webhookStatus() == IntegrationStatus.ERROR) {
            return bank.environment() == BankEnvironment.PRODUCTION ? DEGRADED : Status.UNKNOWN;
        }
        if (bank.apiStatus() == IntegrationStatus.PENDING && bank.webhookStatus() == IntegrationStatus.PENDING) {
            return Status.UNKNOWN;
        }
        return Status.UP;
    }
}
