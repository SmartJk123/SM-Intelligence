package io.smartmoney.api;

import com.sun.net.httpserver.HttpServer;
import io.smartmoney.api.bankintegration.NormalizedTransactionRepository;
import io.smartmoney.api.bankintegration.WebhookEventRepository;
import io.smartmoney.api.bankintegration.ncba.NcbaCpanelImporter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The importer against a stand-in for the cPanel export: it sends the token,
 * pages with `after`, records each notification once, and carries on from its
 * cursor on the next run.
 */
@SpringBootTest
class NcbaCpanelImportTests {

    private static final String TOKEN = "export-token-for-tests-0123456789abcdef";
    private static final AtomicReference<String> lastToken = new AtomicReference<>();
    private static final AtomicReference<String> lastQuery = new AtomicReference<>();
    private static final HttpServer server = start();

    private static HttpServer start() {
        try {
            HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            http.createContext("/export", exchange -> {
                lastToken.set(exchange.getRequestHeaders().getFirst("X-SMI-Export-Token"));
                lastQuery.set(exchange.getRequestURI().getQuery());
                String after = exchange.getRequestURI().getQuery().replaceAll(".*after=(\\d+).*", "$1");
                String body = "0".equals(after)
                        ? "{\"items\":[" + item(7, "CPANEL-IMPORT-1", "1500.00") + "," + item(9, "CPANEL-IMPORT-2", "-250.00") + "]}"
                        : "{\"items\":[]}";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            http.start();
            return http;
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }

    private static String item(long id, String transId, String amount) {
        String xml = "<soapenv:Envelope xmlns:soapenv=\\\"http://schemas.xmlsoap.org/soap/envelope/\\\"><soapenv:Body>"
                + "<NCBAPaymentNotificationRequest><User>JoySMI</User><Password>[redacted]</Password>"
                + "<TransType>220</TransType><TransID>" + transId + "</TransID><TransTime>2609281030</TransTime>"
                + "<TransAmount>" + amount + "</TransAmount><AccountNr>1004906164</AccountNr>"
                + "<Narrative>Rent</Narrative><Status>SUCCESS</Status></NCBAPaymentNotificationRequest>"
                + "</soapenv:Body></soapenv:Envelope>";
        return "{\"id\":" + id + ",\"transId\":\"" + transId + "\",\"signatureValid\":true,\"rawBody\":\"" + xml + "\"}";
    }

    @DynamicPropertySource
    static void exportAddress(DynamicPropertyRegistry registry) {
        registry.add("smartmoney.ncba-cpanel.export-url",
                () -> "http://127.0.0.1:" + server.getAddress().getPort() + "/export");
        registry.add("smartmoney.ncba-cpanel.export-token", () -> TOKEN);
    }

    @AfterAll
    static void stop() {
        server.stop(0);
    }

    @Autowired
    private NcbaCpanelImporter importer;

    @Autowired
    private WebhookEventRepository events;

    @Autowired
    private NormalizedTransactionRepository movements;

    @Test
    void importsEachStoredNotificationOnceAndKeepsItsPlace() throws Exception {
        assertThat(importer.enabled()).isTrue();

        assertThat(importer.importAvailable()).isEqualTo(2);
        assertThat(lastToken.get()).isEqualTo(TOKEN);
        assertThat(events.findFirstByBankIdAndExternalEventId("ncba", "CPANEL-IMPORT-1")).isPresent();

        for (int i = 0; i < 50 && movements.findFirstByBankIdAndExternalEventId("ncba", "CPANEL-IMPORT-2").isEmpty(); i++) {
            Thread.sleep(100);
        }
        var debit = movements.findFirstByBankIdAndExternalEventId("ncba", "CPANEL-IMPORT-2").orElseThrow();
        assertThat(debit.getDirection()).isEqualTo("Debit");
        assertThat(debit.getAccountNumber()).isEqualTo("1004906164");

        // The next run starts after the last id it saw and records nothing new.
        assertThat(importer.importAvailable()).isZero();
        assertThat(lastQuery.get()).contains("after=9");
    }
}
