package io.smartmoney.api.bankintegration;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Real platform statistics, computed from what the platform has actually
 * received. There is no seeded or sample data anywhere in this service: an empty
 * platform returns zeros, which is the honest answer.
 */
@Service
public class PlatformStatsService {

    private static final int SERIES_DAYS = 14;
    private static final int RECENT_LIMIT = 12;

    private final WebhookEventRepository webhookEvents;
    private final BankIntegrationHealthService healthService;
    private final NormalizedTransactionRepository normalizedTransactions;

    public PlatformStatsService(
            WebhookEventRepository webhookEvents,
            BankIntegrationHealthService healthService,
            NormalizedTransactionRepository normalizedTransactions) {
        this.webhookEvents = webhookEvents;
        this.healthService = healthService;
        this.normalizedTransactions = normalizedTransactions;
    }

    public PlatformStats stats() {
        Instant now = Instant.now();
        Instant startOfToday = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant seriesStart = startOfToday.minus(Duration.ofDays(SERIES_DAYS - 1L));

        // Delivery figures only. The demonstration account is reported on its
        // own surface, not as traffic a bank sent.
        List<WebhookEventEntity> all = webhookEvents.findBySimulatedFalse();
        long total = all.size();
        long today = all.stream().filter(e -> isAfter(e.getReceivedAt(), startOfToday)).count();
        long processed = all.stream().filter(e -> "PROCESSED".equals(e.getProcessingStatus())).count();
        long failed = all.stream().filter(e -> "FAILED".equals(e.getProcessingStatus())).count();
        long pending = total - processed - failed;

        Instant lastReceived = all.stream()
                .map(WebhookEventEntity::getReceivedAt)
                .filter(java.util.Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);

        List<IntegrationHealth> health = healthService.health();
        long connected = health.stream()
                .filter(item -> item.apiStatus() == IntegrationStatus.HEALTHY
                        || item.apiStatus() == IntegrationStatus.CONNECTED)
                .count();
        long webhookHealthy = health.stream()
                .filter(item -> item.webhookStatus() == IntegrationStatus.HEALTHY)
                .count();

        double successRate = total == 0 ? 0d : (processed * 100d) / total;

        return new PlatformStats(
                now,
                health.size(),
                connected,
                webhookHealthy,
                total,
                today,
                processed,
                failed,
                pending,
                Math.round(successRate * 10d) / 10d,
                lastReceived,
                lastReceived == null ? null : (int) Duration.between(lastReceived, now).toMinutes(),
                series(seriesStart, startOfToday, all),
                recent());
    }

    private List<DailyPoint> series(
            Instant from, Instant startOfToday, List<WebhookEventEntity> all) {
        Map<String, long[]> byDay = new LinkedHashMap<>();
        LocalDate first = LocalDate.ofInstant(from, ZoneOffset.UTC);
        for (int day = 0; day < SERIES_DAYS; day++) {
            byDay.put(first.plusDays(day).toString(), new long[] {0, 0});
        }

        for (WebhookEventEntity event : all) {
            if (event.getReceivedAt() == null || event.getReceivedAt().isBefore(from)) {
                continue;
            }
            String key = LocalDate.ofInstant(event.getReceivedAt(), ZoneOffset.UTC).toString();
            long[] counts = byDay.get(key);
            if (counts == null) {
                continue;
            }
            counts[0]++;
            if ("FAILED".equals(event.getProcessingStatus())) {
                counts[1]++;
            }
        }

        List<DailyPoint> points = new ArrayList<>();
        byDay.forEach((date, counts) -> points.add(new DailyPoint(date, counts[0], counts[1])));
        return points;
    }

    public List<WebhookEventView> recent() {
        return webhookEvents.findTop25ByOrderByReceivedAtDesc().stream()
                .limit(RECENT_LIMIT)
                .map(this::toView)
                .toList();
    }

    private WebhookEventView toView(WebhookEventEntity event) {
        NormalizedTransactionEntity movement = movement(event);
        return new WebhookEventView(
                event.getId(),
                event.getBankId(),
                event.getExternalEventId(),
                event.getSignatureValid(),
                event.getProcessingStatus(),
                event.getReceivedAt(),
                event.getProcessedAt(),
                event.getErrorMessage(),
                movement == null ? null : movement.getDirection(),
                movement == null ? null : movement.getCounterpartyName(),
                event.isSimulated());
    }

    /**
     * Credit or Debit, and who sent or received it, when the notification
     * carried one and it normalised. The amount and account balance stay off
     * this surface; only the direction and the counterparty's name are shown.
     */
    private NormalizedTransactionEntity movement(WebhookEventEntity event) {
        if (event.getExternalEventId() == null) {
            return null;
        }
        return normalizedTransactions
                .findFirstByBankIdAndExternalEventId(event.getBankId(), event.getExternalEventId())
                .orElse(null);
    }

    private static boolean isAfter(Instant value, Instant boundary) {
        return value != null && !value.isBefore(boundary);
    }

    /** One point on the volume chart. */
    public record DailyPoint(String date, long received, long failed) {
    }

    /** A received notification, shown in the activity list. */
    public record WebhookEventView(
            Long id,
            String bankId,
            String externalEventId,
            Boolean signatureValid,
            String status,
            Instant receivedAt,
            Instant processedAt,
            String errorMessage,
            String direction,
            String counterpartyName,
            boolean simulated) {
    }

    /** Everything the operations dashboard needs, in one call. */
    public record PlatformStats(
            Instant generatedAt,
            long banksSupported,
            long banksConnected,
            long banksWebhookHealthy,
            long notificationsTotal,
            long notificationsToday,
            long processed,
            long failed,
            long pending,
            double successRate,
            Instant lastReceivedAt,
            Integer minutesSinceLastReceived,
            List<DailyPoint> volume,
            List<WebhookEventView> recent) {
    }
}
