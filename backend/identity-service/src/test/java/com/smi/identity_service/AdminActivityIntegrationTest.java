package com.smi.identity_service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

// Real HTTP, security and persistence (H2). No SMTP is set up in tests, so every
// email lands in the outbox as NOT_CONFIGURED, which is what these checks rely on.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminActivityIntegrationTest {
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    private HttpResponse<String> send(String method, String route, Object body, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + route))
            .header("Content-Type", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        var publisher = body == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body));
        return client.send(request.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
    }

    private String adminToken() throws Exception {
        send("POST", "/api/auth/register", Map.of("name", "Platform Admin", "emailAddress", "admin@example.invalid",
            "password", "AdminPassword123!"), null);
        var login = send("POST", "/api/auth/login",
            Map.of("emailAddress", "admin@example.invalid", "password", "AdminPassword123!"), null);
        assertEquals(200, login.statusCode(), login.body());
        return json.readTree(login.body()).get("token").asText();
    }

    private JsonNode get(String route, String token) throws Exception {
        var response = send("GET", route, null, token);
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    private static boolean any(JsonNode array, java.util.function.Predicate<JsonNode> test) {
        return StreamSupport.stream(array.spliterator(), false).anyMatch(test);
    }

    @Test
    void signUpAndSignInAreAuditedNotifiedAndEmailed() throws Exception {
        String admin = adminToken();
        String email = "activity-" + UUID.randomUUID() + "@example.invalid";
        assertEquals(201, send("POST", "/api/auth/register", Map.of("name", "Activity Person", "emailAddress", email,
            "password", "CustomerPassword123!", "accountType", "ORGANIZATION", "organizationName", "Acme Ltd"), null)
            .statusCode());
        assertEquals(401, send("POST", "/api/auth/login",
            Map.of("emailAddress", email, "password", "wrong-password"), null).statusCode());
        assertEquals(200, send("POST", "/api/auth/login",
            Map.of("emailAddress", email, "password", "CustomerPassword123!"), null).statusCode());

        JsonNode audit = get("/api/v1/admin/audit-events", admin);
        assertTrue(any(audit, e -> e.get("action").asText().equals("SIGN_UP") && email.equals(e.get("actorEmail").asText())));
        assertTrue(any(audit, e -> e.get("action").asText().equals("SIGN_IN_FAILED")
            && e.get("outcome").asText().equals("FAILURE") && email.equals(e.get("actorEmail").asText())));
        assertTrue(any(audit, e -> e.get("action").asText().equals("SIGN_IN")
            && e.get("actorRole").asText().equals("CUSTOMER") && email.equals(e.get("actorEmail").asText())));

        JsonNode notifications = get("/api/v1/admin/notifications", admin);
        assertTrue(any(notifications, n -> n.get("title").asText().equals("New sign up")
            && n.get("message").asText().contains(email)));

        // The welcome email goes out off the request thread; give it a moment.
        boolean welcomed = false;
        for (int attempt = 0; attempt < 50 && !welcomed; attempt++) {
            welcomed = any(get("/api/v1/admin/email-outbox", admin), m -> email.equals(m.get("recipient").asText())
                && "WELCOME".equals(m.get("templateKey").asText()));
            if (!welcomed) Thread.sleep(100);
        }
        assertTrue(welcomed, "the welcome email is in the outbox");

        // Mark everything read.
        assertEquals(200, send("POST", "/api/v1/admin/notifications/read-all", null, admin).statusCode());
        assertFalse(any(get("/api/v1/admin/notifications", admin), n -> !n.get("read").asBoolean()));
    }

    @Test
    void adminAddsAnOrganisationCustomerWhoCanSignInWithTheTemporaryPassword() throws Exception {
        String admin = adminToken();
        String email = "customer-" + UUID.randomUUID() + "@example.invalid";
        Map<String, Object> request = new HashMap<>();
        request.put("name", "Added Customer");
        request.put("email", email);
        request.put("accountType", "ORGANIZATION");
        request.put("newOrganizationName", "New Org " + UUID.randomUUID());
        request.put("businessType", "SME");

        var created = send("POST", "/api/v1/admin/users", request, admin);
        assertEquals(201, created.statusCode(), created.body());
        JsonNode result = json.readTree(created.body());
        assertEquals("ORGANIZATION", result.get("user").get("accountType").asText());
        assertFalse(result.get("organizationId").isNull());
        assertFalse(result.get("emailSent").asBoolean(), "no SMTP in tests");

        // The outbox keeps the invite but never the temporary password or the link token.
        JsonNode invite = StreamSupport.stream(get("/api/v1/admin/email-outbox", admin).spliterator(), false)
            .filter(m -> email.equals(m.get("recipient").asText())).findFirst().orElseThrow();
        assertEquals("CUSTOMER_INVITE", invite.get("templateKey").asText());
        assertEquals("NOT_CONFIGURED", invite.get("status").asText());
        String body = invite.get("body").asText();
        assertTrue(body.contains("Temporary password: [hidden]"), body);
        assertFalse(body.matches("(?s).*token=(?!\\[hidden]).*"), body);

        assertTrue(any(get("/api/v1/admin/audit-events", admin), e -> e.get("action").asText().equals("CUSTOMER_CREATED")
            && email.equals(e.get("targetLabel").asText()) && e.get("actorRole").asText().equals("STAFF")));

        // The same email cannot be added twice; an organisation account needs an organisation.
        assertEquals(409, send("POST", "/api/v1/admin/users", request, admin).statusCode());
        request.put("email", "other-" + UUID.randomUUID() + "@example.invalid");
        request.remove("newOrganizationName");
        assertEquals(400, send("POST", "/api/v1/admin/users", request, admin).statusCode());
        // Customers cannot use it.
        assertEquals(401, send("POST", "/api/v1/admin/users", request, null).statusCode());
    }

    @Test
    void anAdministratorCanEditAndResetAnEmailTemplate() throws Exception {
        String admin = adminToken();
        JsonNode templates = get("/api/v1/admin/email-templates", admin);
        assertTrue(any(templates, t -> t.get("key").asText().equals("WELCOME") && !t.get("customised").asBoolean()));

        var updated = send("PUT", "/api/v1/admin/email-templates/WELCOME",
            Map.of("subject", "Karibu {{name}}", "body", "Hello {{name}}, welcome."), admin);
        assertEquals(200, updated.statusCode(), updated.body());
        assertTrue(json.readTree(updated.body()).get("customised").asBoolean());
        assertEquals("admin@example.invalid", json.readTree(updated.body()).get("updatedBy").asText());

        assertEquals(400, send("PUT", "/api/v1/admin/email-templates/WELCOME", Map.of("subject", "", "body", "x"), admin)
            .statusCode());
        assertEquals(400, send("PUT", "/api/v1/admin/email-templates/NOPE", Map.of("subject", "a", "body", "b"), admin)
            .statusCode());

        var reset = send("POST", "/api/v1/admin/email-templates/WELCOME/reset", null, admin);
        assertEquals(200, reset.statusCode(), reset.body());
        assertFalse(json.readTree(reset.body()).get("customised").asBoolean());
    }
}
