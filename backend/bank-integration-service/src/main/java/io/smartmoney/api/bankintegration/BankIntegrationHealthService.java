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

        BankConnectionTest latestTest = tests.latestFor(bankId).orElse(null);
        Instant lastTest = latestTest == null ? null : latestTest.testedAt();
        Integer latency = latestTest == null ? null : (int) latestTest.latencyMs();

        TokenSnapshot token = connector instanceof TokenStateProvider provider
                ? provider.tokenSnapshot()
                : TokenSnapshot.unknown();
        // A token is only held in memory, so after a restart it reads as unknown even
        // though the stored test proves the credentials work. Report the test instead,
        // so the screen after signing in again matches the screen after the test.
        if (token.status() == TokenStatus.UNKNOWN && latestTest != null
                && stepStatus(latestTest, StepKey.TOKEN) == StepStatus.OK) {
            token = new TokenSnapshot(TokenStatus.VALID, null, latestTest.testedAt());
        }

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
                webhookStatus(lastWebhook, latestTest),
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

    private static StepStatus stepStatus(BankConnectionTest test, StepKey key) {
        return test.steps().stream()
                .filter(step -> step.key() == key)
                .map(ConnectionStep::status)
                .findFirst()
                .orElse(null);
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

    private IntegrationStatus webhookStatus(Instant lastWebhook, BankConnectionTest latestTest) {
        if (lastWebhook == null) {
            // Nothing received yet: the stored test's registration step is the best evidence,
            // which is also what the screen showed right after that test.
            StepStatus registration = latestTest == null ? null : stepStatus(latestTest, StepKey.WEBHOOK_REGISTRATION);
            if (registration == StepStatus.OK) {
                return IntegrationStatus.HEALTHY;
            }
            if (registration == StepStatus.WARN) {
                return IntegrationStatus.WARNING;
            }
            return registration == StepStatus.FAIL ? IntegrationStatus.ERROR : IntegrationStatus.PENDING;
        }
        Duration age = Duration.between(lastWebhook, Instant.now());
        if (age.compareTo(HEALTHY_WINDOW) <= 0) {
            return IntegrationStatus.HEALTHY;
        }
        return age.compareTo(WARNING_WINDOW) <= 0 ? IntegrationStatus.WARNING : IntegrationStatus.ERROR;
    }
}
