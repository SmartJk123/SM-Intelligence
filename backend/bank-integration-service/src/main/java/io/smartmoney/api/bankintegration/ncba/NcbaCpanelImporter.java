package io.smartmoney.api.bankintegration.ncba;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smartmoney.api.bankintegration.CpanelExportImporter;
import io.smartmoney.api.bankintegration.WebhookIngestionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Imports NCBA notifications from the cPanel receiver (ncba-webhook.php). The
 * cPanel side has already checked the credentials and HashVal, and its stored
 * copy has the password redacted.
 *
 * Off unless NCBA_CPANEL_EXPORT_URL and NCBA_CPANEL_EXPORT_TOKEN are both set.
 */
@Component
public class NcbaCpanelImporter extends CpanelExportImporter {

    public NcbaCpanelImporter(
            @Value("${smartmoney.ncba-cpanel.export-url:}") String exportUrl,
            @Value("${smartmoney.ncba-cpanel.export-token:}") String token,
            ObjectMapper mapper, WebhookIngestionService ingestion, ImportCursorRepository cursors) {
        super("ncba", exportUrl, token, mapper, ingestion, cursors);
    }

    @Scheduled(initialDelayString = "PT30S",
            fixedDelayString = "${smartmoney.ncba-cpanel.interval:PT1M}")
    public void scheduledImport() {
        runScheduledImport();
    }
}
