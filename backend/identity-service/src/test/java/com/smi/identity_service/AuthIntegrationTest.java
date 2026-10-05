package com.smi.identity_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smi.identity_service.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

// Real HTTP, security, password hashing, JWT verification and repository persistence (H2 test DB).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthIntegrationTest {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    private HttpResponse<String> post(String route, Map<String, String> body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + route))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> me(String token) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/me"))
            .header("Authorization", "Bearer " + token).build(), HttpResponse.BodyHandlers.ofString());
    }
    @Test void registrationLoginAndVerifiedProfile() throws Exception {
        String email = "integration-" + UUID.randomUUID() + "@example.invalid";
        var body = Map.of("name", "Integration User", "emailAddress", email.toUpperCase(), "password", "ValidPassword123!", "accountType", "ORGANIZATION");
        var registration = post("/api/auth/register", body);
        assertEquals(201, registration.statusCode(), registration.body());
        var registered = json.readTree(registration.body());
        String token = registered.get("token").asText();
        String id = registered.get("userId").asText();
        assertNotNull(UUID.fromString(id));
        assertFalse(registration.body().contains("passwordHash"));
        assertEquals("ORGANIZATION", registered.get("accountType").asText());
        var saved = users.findById(UUID.fromString(id)).orElseThrow();
        assertNotEquals(body.get("password"), saved.getPasswordHash());
        assertEquals(email, saved.getEmailAddress());
        assertEquals(200, me(token).statusCode());
        assertEquals(id, json.readTree(me(token).body()).get("id").asText());
        assertEquals(409, post("/api/auth/register", body).statusCode());
        assertEquals(401, post("/api/auth/login", Map.of("emailAddress", email, "password", "wrong")).statusCode());
        var login = post("/api/auth/login", Map.of("emailAddress", email, "password", body.get("password")));
        assertEquals(200, login.statusCode());
        assertEquals(id, json.readTree(login.body()).get("userId").asText());
        assertEquals(401, me("invalid.jwt.token").statusCode());
        assertEquals(400, post("/api/auth/register", Map.of("name", "Bad Kind", "emailAddress", "invalid@example.invalid", "password", "ValidPassword123!", "accountType", "ADMIN")).statusCode());
        // Signing in updates last_login_at, so reload before changing the row again.
        saved = users.findById(UUID.fromString(id)).orElseThrow();
        saved.setDeletedAt(java.time.OffsetDateTime.now()); users.save(saved);
        assertEquals(401, me(token).statusCode());
    }
}
