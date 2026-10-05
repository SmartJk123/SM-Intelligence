package io.smartmoney.api.bankintegration.equity;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smartmoney.api.bankintegration.CpanelExportImporter;
import io.smartmoney.api.bankintegration.WebhookIngestionService;
import io.smartmoney.api.bankintegration.ncba.ImportCursorRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Imports Equity (Jenga) Instant Payment Notifications from the cPanel
 * receiver (equity-webhook.php), which has already checked the Basic Auth
 * credentials.
 *
 * Off unless EQUITY_CPANEL_EXPORT_URL and EQUITY_CPANEL_EXPORT_TOKEN are both set.
 */
@Component
public class EquityCpanelImporter extends CpanelExportImporter {

    public EquityCpanelImporter(
            @Value("${smartmoney.equity-cpanel.export-url:}") String exportUrl,
            @Value("${smartmoney.equity-cpanel.export-token:}") String token,
            ObjectMapper mapper, WebhookIngestionService ingestion, ImportCursorRepository cursors) {
        super("equity", exportUrl, token, mapper, ingestion, cursors);
    }

    @Scheduled(initialDelayString = "PT40S",
            fixedDelayString = "${smartmoney.equity-cpanel.interval:PT1M}")
    public void scheduledImport() {
        runScheduledImport();
    }
}
