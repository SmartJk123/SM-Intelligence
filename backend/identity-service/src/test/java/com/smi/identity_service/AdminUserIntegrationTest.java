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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// Real HTTP, security, JWT and persistence (H2). The test config lists
// admin@example.invalid in app.admin.emails, so that account registers as a platform admin.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminUserIntegrationTest {
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

    private JsonNode login(String email, String password) throws Exception {
        var response = send("POST", "/api/auth/login", Map.of("emailAddress", email, "password", password), null);
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    private String adminToken() throws Exception {
        send("POST", "/api/auth/register", Map.of("name", "Platform Admin", "emailAddress", "admin@example.invalid",
            "password", "AdminPassword123!"), null);
        JsonNode admin = login("admin@example.invalid", "AdminPassword123!");
        assertEquals("PLATFORM_ADMIN", admin.get("role").asText());
        return admin.get("token").asText();
    }

    @Test
    void adminManagesAWebSignupAndTheChangeReachesThatUser() throws Exception {
        String adminToken = adminToken();
        String email = "web-" + UUID.randomUUID() + "@example.invalid";
        String password = "CustomerPassword123!";
        var registered = json.readTree(send("POST", "/api/auth/register",
            Map.of("name", "Web Signup", "emailAddress", email, "password", password), null).body());
        String userId = registered.get("userId").asText();
        assertEquals("USER", registered.get("role").asText());
        String customerToken = login(email, password).get("token").asText();

        // Only a platform admin may use the admin API.
        assertEquals(401, send("GET", "/api/v1/admin/users", null, null).statusCode());
        assertEquals(403, send("GET", "/api/v1/admin/users", null, customerToken).statusCode());

        // The web signup is listed, with its status and last sign in.
        var list = send("GET", "/api/v1/admin/users", null, adminToken);
        assertEquals(200, list.statusCode(), list.body());
        assertFalse(list.body().contains("passwordHash"));
        JsonNode listed = null;
        for (JsonNode user : json.readTree(list.body())) if (user.get("id").asText().equals(userId)) listed = user;
        assertNotNull(listed);
        assertEquals("ACTIVE", listed.get("status").asText());
        assertFalse(listed.get("lastLoginAt").isNull());

        // Edit.
        var edited = send("PUT", "/api/v1/admin/users/" + userId,
            Map.of("name", "Corrected Name", "phoneNumber", "+254700000000"), adminToken);
        assertEquals(200, edited.statusCode(), edited.body());
        assertEquals("Corrected Name", json.readTree(edited.body()).get("name").asText());
        assertEquals(400, send("PUT", "/api/v1/admin/users/" + userId, Map.of("name", "X"), adminToken).statusCode());

        // Suspend: sign in and the existing session both stop working.
        var suspended = send("PATCH", "/api/v1/admin/users/" + userId + "/status", Map.of("status", "SUSPENDED"), adminToken);
        assertEquals(200, suspended.statusCode(), suspended.body());
        assertEquals("SUSPENDED", json.readTree(suspended.body()).get("status").asText());
        var refused = send("POST", "/api/auth/login", Map.of("emailAddress", email, "password", password), null);
        assertEquals(403, refused.statusCode());
        assertEquals("ACCOUNT_SUSPENDED", json.readTree(refused.body()).get("code").asText());
        assertEquals(403, send("GET", "/api/auth/me", null, customerToken).statusCode());
        // A wrong password still reads as wrong credentials, never as "suspended".
        assertEquals(401, send("POST", "/api/auth/login", Map.of("emailAddress", email, "password", "wrong-password"), null).statusCode());

        // Restore.
        assertEquals(200, send("PATCH", "/api/v1/admin/users/" + userId + "/status", Map.of("status", "ACTIVE"), adminToken).statusCode());
        assertEquals(200, send("GET", "/api/auth/me", null, customerToken).statusCode());
        login(email, password);

        // Guard rails.
        String adminId = json.readTree(send("GET", "/api/auth/me", null, adminToken).body()).get("id").asText();
        assertEquals(400, send("PATCH", "/api/v1/admin/users/" + adminId + "/status", Map.of("status", "SUSPENDED"), adminToken).statusCode());
        assertEquals(400, send("PATCH", "/api/v1/admin/users/" + userId + "/status", Map.of("status", "DELETED"), adminToken).statusCode());
        assertEquals(404, send("GET", "/api/v1/admin/users/" + UUID.randomUUID(), null, adminToken).statusCode());
    }
}
