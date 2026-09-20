package io.smartmoney.api.bankintegration;

import io.smartmoney.api.bankintegration.PlatformStatsService.PlatformStats;
import io.smartmoney.api.bankintegration.PlatformStatsService.WebhookEventView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Real-time operations statistics for the administrator.
 *
 * These are counts and states only. No organisation's balances, counterparties
 * or amounts appear on the admin surface.
 */
@RestController
@RequestMapping("/api/v1/admin/stats")
public class PlatformStatsController {

    private final PlatformStatsService statsService;

    public PlatformStatsController(PlatformStatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping
    public PlatformStats stats() {
        return statsService.stats();
    }

    @GetMapping("/events")
    public List<WebhookEventView> recentEvents() {
        return statsService.recent();
    }
}
