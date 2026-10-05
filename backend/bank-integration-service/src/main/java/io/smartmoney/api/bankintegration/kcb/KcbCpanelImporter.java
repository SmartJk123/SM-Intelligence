package io.smartmoney.api.bankintegration.kcb;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smartmoney.api.bankintegration.CpanelExportImporter;
import io.smartmoney.api.bankintegration.WebhookIngestionService;
import io.smartmoney.api.bankintegration.ncba.ImportCursorRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Imports KCB instant payment notifications from the cPanel receiver
 * (kcb-webhook.php), which has already verified the Signature header with the
 * KCB public key.
 *
 * Off unless KCB_CPANEL_EXPORT_URL and KCB_CPANEL_EXPORT_TOKEN are both set.
 */
@Component
public class KcbCpanelImporter extends CpanelExportImporter {

    public KcbCpanelImporter(
            @Value("${smartmoney.kcb-cpanel.export-url:}") String exportUrl,
            @Value("${smartmoney.kcb-cpanel.export-token:}") String token,
            ObjectMapper mapper, WebhookIngestionService ingestion, ImportCursorRepository cursors) {
        super("kcb", exportUrl, token, mapper, ingestion, cursors);
    }

    @Scheduled(initialDelayString = "PT35S",
            fixedDelayString = "${smartmoney.kcb-cpanel.interval:PT1M}")
    public void scheduledImport() {
        runScheduledImport();
    }
}
