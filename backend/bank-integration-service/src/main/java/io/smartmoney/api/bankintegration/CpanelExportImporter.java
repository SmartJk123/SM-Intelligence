package io.smartmoney.api.bankintegration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smartmoney.api.bankintegration.ncba.ImportCursorEntity;
import io.smartmoney.api.bankintegration.ncba.ImportCursorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Imports one bank's notifications from its cPanel receiver.
 *
 * In production the bank posts to a PHP endpoint on cPanel, which verifies each
 * notification and stores it in MySQL (admin-interface/backend/deploy/cpanel).
 * This pulls those rows through the receiver's token protected export and
 * records each one exactly as if the bank had posted it here, so it is
 * normalised and, when the account is linked, delivered to the customer's
 * dashboard.
 *
 * The cPanel side has already verified the notification, so the verdict is
 * taken from the row rather than checked again. The bank's transaction
 * reference is the deduplication key on both sides, so a notification that
 * also reached this service directly is kept once.
 *
 * Off unless the export URL and a token of at least 32 characters are both set.
 */
public abstract class CpanelExportImporter {

    private static final Logger log = LoggerFactory.getLogger(CpanelExportImporter.class);
    private static final int PAGE = 100;
    /** Pages per run, so one run cannot hold the scheduler for long. */
    private static final int MAX_PAGES = 20;

    private final String bankId;
    private final String source;
    private final String exportUrl;
    private final String token;
    private final RestClient http;
    private final ObjectMapper mapper;
    private final WebhookIngestionService ingestion;
    private final ImportCursorRepository cursors;

    protected CpanelExportImporter(String bankId, String exportUrl, String token, ObjectMapper mapper,
                                   WebhookIngestionService ingestion, ImportCursorRepository cursors) {
        this.bankId = bankId;
        this.source = bankId + "-cpanel";
        this.exportUrl = exportUrl == null ? "" : exportUrl.trim();
        this.token = token == null ? "" : token.trim();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(30000);
        this.http = RestClient.builder().requestFactory(factory).build();
        this.mapper = mapper;
        this.ingestion = ingestion;
        this.cursors = cursors;
    }

    public boolean enabled() {
        return !exportUrl.isEmpty() && token.length() >= 32;
    }

    /** Called by the subclass's schedule. Never throws: the next run retries. */
    protected void runScheduledImport() {
        if (!enabled()) {
            return;
        }
        try {
            importAvailable();
        } catch (Exception error) {
            log.warn("{} import from cPanel failed, will retry: {}", bankId.toUpperCase(), error.getMessage());
        }
    }

    /** Reads everything new since the last run. @return how many notifications were recorded. */
    public int importAvailable() throws com.fasterxml.jackson.core.JsonProcessingException {
        ImportCursorEntity cursor = cursors.findById(source).orElseGet(() -> new ImportCursorEntity(source));
        int recorded = 0;
        for (int page = 0; page < MAX_PAGES; page++) {
            long after = cursor.getLastId();
            // Fetched as a String and parsed with this app's own ObjectMapper: Spring's
            // RestClient here defaults to a Jackson 3 message converter, which cannot
            // instantiate the classic com.fasterxml.jackson JsonNode this app uses
            // everywhere else.
            String raw = http.get()
                    .uri(exportUrl + (exportUrl.contains("?") ? "&" : "?") + "after=" + after + "&limit=" + PAGE)
                    .header("X-SMI-Export-Token", token)
                    .retrieve().body(String.class);
            JsonNode body = raw == null ? null : mapper.readTree(raw);
            JsonNode items = body == null ? null : body.path("items");
            if (items == null || !items.isArray() || items.isEmpty()) {
                break;
            }
            for (JsonNode item : items) {
                String transId = item.path("transId").asText("");
                String rawBody = item.path("rawBody").asText("");
                if (!transId.isBlank() && !rawBody.isBlank() && !ingestion.alreadyRecorded(bankId, transId)) {
                    Boolean signatureValid = item.path("signatureValid").isBoolean()
                            ? item.path("signatureValid").asBoolean() : null;
                    WebhookEventEntity event = ingestion.record(bankId, transId, signatureValid, rawBody);
                    ingestion.process(event.getId());
                    recorded++;
                }
                cursor.setLastId(Math.max(cursor.getLastId(), item.path("id").asLong(0)));
            }
            cursors.save(cursor);
            if (items.size() < PAGE) {
                break;
            }
        }
        if (recorded > 0) {
            log.info("Imported {} {} notification(s) from cPanel", recorded, bankId.toUpperCase());
        }
        return recorded;
    }
}
