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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

// Real HTTP, security and persistence (H2). admin@example.invalid is in ADMIN_EMAILS in the test config.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StaffSettingsSessionsIntegrationTest {
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    private HttpResponse<String> send(String method, String route, Object body, String token, String userAgent)
            throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + route))
            .header("Content-Type", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (userAgent != null) request.header("User-Agent", userAgent);
        var publisher = body == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body));
        return client.send(request.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> send(String method, String route, Object body, String token) throws Exception {
        return send(method, route, body, token, null);
    }

    private String login(String email, String password, String userAgent) throws Exception {
        var response = send("POST", "/api/auth/login", Map.of("emailAddress", email, "password", password), null, userAgent);
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body()).get("token").asText();
    }

    private String adminToken() throws Exception {
        send("POST", "/api/auth/register", Map.of("name", "Platform Admin", "emailAddress", "admin@example.invalid",
            "password", "AdminPassword123!"), null);
        return login("admin@example.invalid", "AdminPassword123!", null);
    }

    private JsonNode body(HttpResponse<String> response, int status) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        return response.body().isEmpty() ? null : json.readTree(response.body());
    }

    @Test
    void rolesLimitWhatAStaffMemberCanDo() throws Exception {
        String admin = adminToken();
        JsonNode me = body(send("GET", "/api/v1/admin/staff/me", null, admin), 200);
        assertTrue(me.get("fullAccess").asBoolean());

        JsonNode role = body(send("POST", "/api/v1/admin/staff-roles", Map.of("name", "Support " + UUID.randomUUID(),
            "description", "Customers only", "permissions", List.of("customers", "dashboard")), admin), 201);
        String roleId = role.get("id").asText();
        assertEquals(400, send("POST", "/api/v1/admin/staff-roles",
            Map.of("name", "Bad", "permissions", List.of("not-a-permission")), admin).statusCode());

        // A brand new address gets an account with a temporary password (kept out of the outbox).
        String email = "staff-" + UUID.randomUUID() + "@example.invalid";
        JsonNode added = body(send("POST", "/api/v1/admin/staff",
            Map.of("name", "New Staff", "email", email, "roleId", roleId), admin), 201);
        assertFalse(added.get("existingAccount").asBoolean());
        String staffId = added.get("staff").get("id").asText();
        JsonNode invite = StreamSupport.stream(body(send("GET", "/api/v1/admin/email-outbox", null, admin), 200)
            .spliterator(), false).filter(m -> email.equals(m.get("recipient").asText())).findFirst().orElseThrow();
        assertEquals("STAFF_INVITE", invite.get("templateKey").asText());
        assertTrue(invite.get("body").asText().contains("Temporary password: [hidden]"));

        // Set a known password through the reset flow is not possible here, so promote an existing account instead.
        String existing = "existing-" + UUID.randomUUID() + "@example.invalid";
        body(send("POST", "/api/auth/register", Map.of("name", "Existing Person", "emailAddress", existing,
            "password", "ExistingPassword123!"), null), 201);
        JsonNode promoted = body(send("POST", "/api/v1/admin/staff",
            Map.of("email", existing, "roleId", roleId), admin), 201);
        assertTrue(promoted.get("existingAccount").asBoolean());
        String staffToken = login(existing, "ExistingPassword123!", null);

        JsonNode staffMe = body(send("GET", "/api/v1/admin/staff/me", null, staffToken), 200);
        assertFalse(staffMe.get("fullAccess").asBoolean());
        assertTrue(staffMe.get("permissions").toString().contains("customers"));
        // Customers is allowed, settings and the audit log are not.
        assertEquals(200, send("GET", "/api/v1/admin/users", null, staffToken).statusCode());
        assertEquals(403, send("GET", "/api/v1/admin/audit-events", null, staffToken).statusCode());
        assertEquals(403, send("GET", "/api/v1/admin/staff", null, staffToken).statusCode());
        assertEquals(403, send("PUT", "/api/v1/admin/settings/system", Map.of("maintenanceMode", true), staffToken)
            .statusCode());

        // Edit the role: give it the audit log.
        body(send("PUT", "/api/v1/admin/staff-roles/" + roleId, Map.of("name", role.get("name").asText(),
            "permissions", List.of("customers", "dashboard", "audit-logs")), admin), 200);
        assertEquals(200, send("GET", "/api/v1/admin/audit-events", null, staffToken).statusCode());

        // A role in use cannot be deleted; removing access ends the staff member's sessions.
        assertEquals(400, send("DELETE", "/api/v1/admin/staff-roles/" + roleId, null, admin).statusCode());
        assertEquals(204, send("DELETE", "/api/v1/admin/staff/" + staffId, null, admin).statusCode());
        assertEquals(204, send("DELETE", "/api/v1/admin/staff/" + promoted.get("staff").get("id").asText(), null, admin)
            .statusCode());
        assertEquals(401, send("GET", "/api/v1/admin/users", null, staffToken).statusCode());
        assertEquals(204, send("DELETE", "/api/v1/admin/staff-roles/" + roleId, null, admin).statusCode());

        // You cannot take your own access away.
        String adminId = body(send("GET", "/api/auth/me", null, admin), 200).get("id").asText();
        assertEquals(400, send("DELETE", "/api/v1/admin/staff/" + adminId, null, admin).statusCode());
    }

    @Test
    void settingsAreSavedValidatedAndApplied() throws Exception {
        String admin = adminToken();
        JsonNode security = body(send("GET", "/api/v1/admin/settings/security", null, admin), 200);
        assertEquals(15, security.get("values").get("sessionTimeoutMinutes").asInt());

        JsonNode saved = body(send("PUT", "/api/v1/admin/settings/notifications",
            Map.of("emailFirstSignIns", true, "recipients", "ops@example.invalid"), admin), 200);
        assertTrue(saved.get("values").get("emailFirstSignIns").asBoolean());
        assertEquals("admin@example.invalid", saved.get("updatedBy").asText());
        assertTrue(body(send("GET", "/api/v1/admin/settings", null, admin), 200)
            .get("notifications").get("values").get("emailFirstSignIns").asBoolean());

        assertEquals(400, send("PUT", "/api/v1/admin/settings/security", Map.of("sessionTimeoutMinutes", 0), admin)
            .statusCode());
        assertEquals(400, send("PUT", "/api/v1/admin/settings/security", Map.of("unknownKey", 1), admin).statusCode());

        // Lockout after repeated wrong passwords.
        body(send("PUT", "/api/v1/admin/settings/security", Map.of("maxFailedAttempts", 2, "lockoutMinutes", 5), admin), 200);
        String email = "lock-" + UUID.randomUUID() + "@example.invalid";
        body(send("POST", "/api/auth/register", Map.of("name", "Lock Test", "emailAddress", email,
            "password", "LockPassword123!"), null), 201);
        for (int i = 0; i < 2; i++) {
            assertEquals(401, send("POST", "/api/auth/login", Map.of("emailAddress", email, "password", "wrong-pass"), null)
                .statusCode());
        }
        var locked = send("POST", "/api/auth/login", Map.of("emailAddress", email, "password", "LockPassword123!"), null);
        assertEquals(429, locked.statusCode(), locked.body());
        assertEquals("TOO_MANY_ATTEMPTS", json.readTree(locked.body()).get("code").asText());
        body(send("PUT", "/api/v1/admin/settings/security", Map.of("maxFailedAttempts", 0), admin), 200);

        // Maintenance keeps customers out, but not staff.
        body(send("PUT", "/api/v1/admin/settings/system", Map.of("maintenanceMode", true), admin), 200);
        try {
            var refused = send("POST", "/api/auth/login", Map.of("emailAddress", email, "password", "LockPassword123!"), null);
            assertEquals(503, refused.statusCode(), refused.body());
            assertEquals(503, send("POST", "/api/auth/register", Map.of("name", "Late", "emailAddress",
                "late-" + UUID.randomUUID() + "@example.invalid", "password", "LatePassword123!"), null).statusCode());
            login("admin@example.invalid", "AdminPassword123!", null);
        } finally {
            body(send("PUT", "/api/v1/admin/settings/system", Map.of("maintenanceMode", false), admin), 200);
        }
    }

    @Test
    void sessionsNameTheDeviceAndCanBeSignedOut() throws Exception {
        String email = "devices-" + UUID.randomUUID() + "@example.invalid";
        body(send("POST", "/api/auth/register", Map.of("name", "Device Person", "emailAddress", email,
            "password", "DevicePassword123!"), null), 201);
        String windows = login(email, "DevicePassword123!",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36 Edg/130.0");
        String phone = login(email, "DevicePassword123!",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1");

        JsonNode sessions = body(send("GET", "/api/auth/sessions", null, windows), 200);
        Map<String, JsonNode> byDevice = new HashMap<>();
        sessions.forEach(s -> byDevice.put(s.get("device").asText(), s));
        assertTrue(byDevice.containsKey("Edge on Windows"), sessions.toString());
        assertTrue(byDevice.containsKey("Safari on iPhone"), sessions.toString());
        assertTrue(byDevice.get("Edge on Windows").get("current").asBoolean());
        assertFalse(byDevice.get("Safari on iPhone").get("current").asBoolean());

        // Signing the phone out from the computer ends the phone's token at once.
        String phoneSession = byDevice.get("Safari on iPhone").get("id").asText();
        assertEquals(204, send("DELETE", "/api/auth/sessions/" + phoneSession, null, windows).statusCode());
        assertEquals(401, send("GET", "/api/auth/me", null, phone).statusCode());
        assertEquals(200, send("GET", "/api/auth/me", null, windows).statusCode());

        // Signing out on the computer ends only that device.
        assertEquals(200, send("POST", "/api/auth/logout", null, windows).statusCode());
        assertEquals(401, send("GET", "/api/auth/me", null, windows).statusCode());
    }
}
