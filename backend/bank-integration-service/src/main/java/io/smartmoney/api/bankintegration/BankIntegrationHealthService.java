package io.smartmoney.api.bankintegration;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BankIntegrationHealthService {

    /** Banks the platform is designed to support. Connectors exist for some of them. */
    private static final List<String> SUPPORTED_BANKS = List.of("kcb", "ncba", "equity", "stanbic");

    // These banks push a notification only when a customer's own money actually
    // moves, so a quiet window of hours or days is normal, not a sign of a
    // broken connection. Thresholds are loose on purpose: a real customer
    // account can easily go a day without any activity.
    private static final Duration HEALTHY_WINDOW = Duration.ofHours(24);
    private static final Duration WARNING_WINDOW = Duration.ofDays(7);

    private final Map<String, BankConnector> connectors;
    private final BankIntegrationSettingsService settingsService;
    private final WebhookEventRepository webhookEvents;
    private final ConnectionTestRegistry tests;

    public BankIntegrationHealthService(
            List<BankConnector> connectors,
            BankIntegrationSettingsService settingsService,
            WebhookEventRepository webhookEvents,
            ConnectionTestRegistry tests) {
        this.connectors = connectors.stream()
                .collect(Collectors.toMap(BankConnector::bankId, Function.identity()));
        this.settingsService = settingsService;
        this.webhookEvents = webhookEvents;
        this.tests = tests;
    }

    public List<IntegrationHealth> health() {
        return SUPPORTED_BANKS.stream().map(this::healthFor).toList();
    }

    public IntegrationHealth healthFor(String bankId) {
        BankConnectionSettings settings = settingsService.settingsFor(bankId);
        BankConnector connector = connectors.get(bankId);

        Instant lastWebhook = webhookEvents.findFirstByBankIdAndSimulatedFalseOrderByReceivedAtDesc(bankId)
                .map(WebhookEventEntity::getReceivedAt)
                .orElse(null);

        Instant lastTest = tests.latestFor(bankId).map(BankConnectionTest::testedAt).orElse(null);
        Integer latency = tests.latestFor(bankId).map(test -> (int) test.latencyMs()).orElse(null);

        TokenSnapshot token = connector instanceof TokenStateProvider provider
                ? provider.tokenSnapshot()
                : TokenSnapshot.unknown();

        long failures = webhookEvents.countByBankIdAndProcessingStatusAndReceivedAtAfterAndSimulatedFalse(
                bankId, "FAILED", Instant.now().minus(Duration.ofHours(24)));

        long notifications = webhookEvents.countByBankIdAndSimulatedFalse(bankId);
        Instant startOfToday = LocalDate.now(ZoneOffset.UTC)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
        long today = webhookEvents.countByBankIdAndReceivedAtAfterAndSimulatedFalse(bankId, startOfToday);

        return new IntegrationHealth(
                bankId,
                settings.environment(),
                apiStatus(bankId, connector),
                webhookStatus(lastWebhook),
                token.status(),
                token.expiresInMinutes(),
                token.refreshedAt(),
                lastWebhook,
                lastTest,
                latency,
                failures,
                notifications,
                today,
                connector != null,
                lastTest);
    }

    private IntegrationStatus apiStatus(String bankId, BankConnector connector) {
        if (connector == null) {
            return IntegrationStatus.PENDING;
        }
        return tests.latestFor(bankId)
                .map(test -> statusFromSteps(test.steps()))
                .orElse(IntegrationStatus.PENDING);
    }

    private IntegrationStatus statusFromSteps(List<ConnectionStep> steps) {
        boolean tokenFailed = steps.stream()
                .anyMatch(step -> step.key() == StepKey.TOKEN && step.status() == StepStatus.FAIL);
        boolean accountFailed = steps.stream()
                .anyMatch(step -> step.key() == StepKey.ACCOUNT_PROBE && step.status() == StepStatus.FAIL);
        boolean accountWarned = steps.stream()
                .anyMatch(step -> step.key() == StepKey.ACCOUNT_PROBE && step.status() == StepStatus.WARN);

        if (tokenFailed || accountFailed) {
            return IntegrationStatus.ERROR;
        }
        return accountWarned ? IntegrationStatus.WARNING : IntegrationStatus.HEALTHY;
    }

    private IntegrationStatus webhookStatus(Instant lastWebhook) {
        if (lastWebhook == null) {
            return IntegrationStatus.PENDING;
        }
        Duration age = Duration.between(lastWebhook, Instant.now());
        if (age.compareTo(HEALTHY_WINDOW) <= 0) {
            return IntegrationStatus.HEALTHY;
        }
        return age.compareTo(WARNING_WINDOW) <= 0 ? IntegrationStatus.WARNING : IntegrationStatus.ERROR;
    }
}
