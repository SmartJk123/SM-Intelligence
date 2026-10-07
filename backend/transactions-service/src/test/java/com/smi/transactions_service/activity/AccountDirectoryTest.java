package com.smi.transactions_service.activity;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Reads the user's accounts from a stand-in accounts-service, with the user's own token. */
class AccountDirectoryTest {

    HttpServer server;
    final AtomicReference<String> seenAuthorization = new AtomicReference<>();
    final AtomicReference<Integer> statusToSend = new AtomicReference<>(200);
    final UUID active = UUID.randomUUID();
    final UUID closed = UUID.randomUUID();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/accounts", exchange -> {
            seenAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            String body = "[{\"id\":\"" + active + "\",\"userId\":\"x\",\"institution\":\"KCB\",\"accountName\":\"Business\","
                    + "\"maskedIdentifier\":\"***0167\",\"availableBalance\":10,\"accountStatus\":\"ACTIVE\"},"
                    + "{\"id\":\"" + closed + "\",\"institution\":\"NCBA\",\"accountName\":\"Old\",\"accountStatus\":\"CLOSED\"}]";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            int status = statusToSend.get();
            exchange.sendResponseHeaders(status, status == 200 ? bytes.length : -1);
            if (status == 200) exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private AccountDirectory directory() {
        return new AccountDirectory("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @Test
    void readsOnlyActiveAccountsWithTheCallersToken() {
        var accounts = directory().accountsOf("Bearer customer-token");
        assertEquals("Bearer customer-token", seenAuthorization.get());
        assertEquals(1, accounts.size());
        assertEquals(active, accounts.get(0).id());
        assertEquals("KCB", accounts.get(0).institution());
        assertEquals("***0167", accounts.get(0).maskedIdentifier());
    }

    @Test
    void anExpiredSessionIsReportedAsSuch() {
        statusToSend.set(401);
        var error = assertThrows(ResponseStatusException.class, () -> directory().accountsOf("Bearer old"));
        assertEquals(401, error.getStatusCode().value());
    }
}
