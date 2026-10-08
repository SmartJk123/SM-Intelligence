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
    @Autowired com.smi.identity_service.security.TotpService totpService;
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

    @Test
    void profileUpdateAndChangePasswordFlow() throws Exception {
        String email = "self-profile-" + UUID.randomUUID() + "@example.invalid";
        String initialPassword = "InitialPassword123!";
        var regBody = Map.of("name", "Self User", "emailAddress", email, "password", initialPassword);
        var regResponse = post("/api/auth/register", regBody);
        assertEquals(201, regResponse.statusCode());
        String token = json.readTree(regResponse.body()).get("token").asText();

        // 1. Update Profile via PUT /api/auth/me
        var updateBody = Map.of("name", "Self User Updated", "phoneNumber", "+254712345678", "industry", "Fintech");
        var putReq = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/me"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .PUT(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(updateBody))).build();
        var putRes = client.send(putReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, putRes.statusCode(), putRes.body());
        var updatedProfile = json.readTree(putRes.body());
        assertEquals("Self User Updated", updatedProfile.get("name").asText());
        assertEquals("+254712345678", updatedProfile.get("phoneNumber").asText());
        assertEquals("Fintech", updatedProfile.get("industry").asText());

        // 2. Change Password via POST /api/auth/change-password
        String newPassword = "BrandNewPassword456!";
        var cpReq = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/change-password"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
                        Map.of("currentPassword", initialPassword, "newPassword", newPassword)))).build();
        var cpRes = client.send(cpReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, cpRes.statusCode(), cpRes.body());

        // 3. Verify old password fails login
        var oldLogin = post("/api/auth/login", Map.of("emailAddress", email, "password", initialPassword));
        assertEquals(401, oldLogin.statusCode());

        // 4. Verify new password succeeds login
        var newLogin = post("/api/auth/login", Map.of("emailAddress", email, "password", newPassword));
        assertEquals(200, newLogin.statusCode());
    }

    @Test
    void tokenIntrospectionAndValidationFlow() throws Exception {
        String email = "introspect-" + UUID.randomUUID() + "@example.invalid";
        var regBody = Map.of("name", "Introspect User", "emailAddress", email, "password", "Pass12345678!", "accountType", "INDIVIDUAL");
        var regResponse = post("/api/auth/register", regBody);
        assertEquals(201, regResponse.statusCode());
        String token = json.readTree(regResponse.body()).get("token").asText();
        String userId = json.readTree(regResponse.body()).get("userId").asText();

        // 1. POST /api/auth/introspect with token in body
        var introReq = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/introspect"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("token", token)))).build();
        var introRes = client.send(introReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, introRes.statusCode());
        var introBody = json.readTree(introRes.body());
        assertTrue(introBody.get("active").asBoolean());
        assertEquals(userId, introBody.get("userId").asText());
        assertEquals(email, introBody.get("email").asText());
        assertEquals("USER", introBody.get("role").asText());

        // 2. GET /api/auth/validate with Authorization header
        var valReq = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/validate"))
                .header("Authorization", "Bearer " + token)
                .GET().build();
        var valRes = client.send(valReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, valRes.statusCode());
        assertTrue(json.readTree(valRes.body()).get("active").asBoolean());

        // 3. GET /api/auth/validate with invalid token -> 401
        var badValReq = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/validate"))
                .header("Authorization", "Bearer invalid.token.xyz")
                .GET().build();
        var badValRes = client.send(badValReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, badValRes.statusCode());
        assertFalse(json.readTree(badValRes.body()).get("active").asBoolean());
    }

    @Test
    void refreshTokenLifecycleAndLogoutFlow() throws Exception {
        String email = "refresh-" + UUID.randomUUID() + "@example.invalid";
        var regBody = Map.of("name", "Refresh User", "emailAddress", email, "password", "SecurePass123!", "accountType", "INDIVIDUAL");
        var regResponse = post("/api/auth/register", regBody);
        assertEquals(201, regResponse.statusCode());
        var regJson = json.readTree(regResponse.body());
        String initialRefreshToken = regJson.get("refreshToken").asText();
        assertNotNull(initialRefreshToken);
        assertFalse(initialRefreshToken.isBlank());

        // 1. Rotate refresh token via POST /api/auth/refresh
        var refreshBody = Map.of("refreshToken", initialRefreshToken);
        var refreshRes = post("/api/auth/refresh", refreshBody);
        assertEquals(200, refreshRes.statusCode(), refreshRes.body());
        var refJson = json.readTree(refreshRes.body());
        String newAccessToken = refJson.get("token").asText();
        String newRefreshToken = refJson.get("refreshToken").asText();
        assertNotNull(newAccessToken);
        assertNotNull(newRefreshToken);
        assertNotEquals(initialRefreshToken, newRefreshToken);

        // 2. Old refresh token cannot be reused (Single-use rotation protection)
        var reusedOldRes = post("/api/auth/refresh", Map.of("refreshToken", initialRefreshToken));
        assertEquals(401, reusedOldRes.statusCode());

        // 3. New access token is valid for authenticated endpoints
        assertEquals(200, me(newAccessToken).statusCode());

        // 4. Logout via POST /api/auth/logout with Bearer token & refresh token body
        var logoutReq = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/logout"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + newAccessToken)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("refreshToken", newRefreshToken))))
                .build();
        var logoutRes = client.send(logoutReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, logoutRes.statusCode());

        // 5. Revoked refresh token cannot be refreshed anymore
        var postLogoutRefreshRes = post("/api/auth/refresh", Map.of("refreshToken", newRefreshToken));
        assertEquals(401, postLogoutRefreshRes.statusCode());
    }

    @Test
    void mfaLifecycleAndTwoStageLoginFlow() throws Exception {
        String email = "mfa-" + UUID.randomUUID() + "@example.invalid";
        String password = "Password123!";
        var reg = post("/api/auth/register", Map.of("name", "MFA User", "emailAddress", email, "password", password));
        assertEquals(201, reg.statusCode());
        String initialToken = json.readTree(reg.body()).get("token").asText();

        // 1. Setup MFA via POST /api/auth/mfa/setup
        var setupReq = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/mfa/setup"))
                .header("Authorization", "Bearer " + initialToken)
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        var setupRes = client.send(setupReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, setupRes.statusCode(), setupRes.body());
        var setupJson = json.readTree(setupRes.body());
        String secret = setupJson.get("secret").asText();
        assertNotNull(secret);
        String backupCode = setupJson.get("backupCodes").get(0).asText();
        assertNotNull(backupCode);

        // 2. Enable MFA using valid TOTP code
        String currentCode = totpService.generateCurrentCode(secret);
        var enableReq = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/mfa/enable"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + initialToken)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("code", currentCode))))
                .build();
        var enableRes = client.send(enableReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, enableRes.statusCode(), enableRes.body());

        // 3. User profile now shows mfaEnabled == true
        var meRes = me(initialToken);
        assertEquals(200, meRes.statusCode());
        assertTrue(json.readTree(meRes.body()).get("mfaEnabled").asBoolean());

        // 4. Logging in returns MFA challenge (200 with mfaRequired = true and mfaToken)
        var loginRes = post("/api/auth/login", Map.of("emailAddress", email, "password", password));
        assertEquals(200, loginRes.statusCode());
        var loginJson = json.readTree(loginRes.body());
        assertTrue(loginJson.get("mfaRequired").asBoolean());
        String mfaToken = loginJson.get("mfaToken").asText();
        assertNotNull(mfaToken);

        // 5. Ephemeral mfaToken cannot be used to access protected resources
        assertEquals(401, me(mfaToken).statusCode());

        // 6. Complete login using emergency backup code via POST /api/auth/mfa/verify
        var verifyRes = post("/api/auth/mfa/verify", Map.of("mfaToken", mfaToken, "code", backupCode));
        assertEquals(200, verifyRes.statusCode(), verifyRes.body());
        var sessionJson = json.readTree(verifyRes.body());
        String fullSessionToken = sessionJson.get("token").asText();
        assertNotNull(fullSessionToken);
        assertNotNull(sessionJson.get("refreshToken").asText());

        // 7. Full session token works against protected resources
        assertEquals(200, me(fullSessionToken).statusCode());

        // 8. Used backup code cannot be reused for second login
        var login2Res = post("/api/auth/login", Map.of("emailAddress", email, "password", password));
        String mfaToken2 = json.readTree(login2Res.body()).get("mfaToken").asText();
        var reusedBackupRes = post("/api/auth/mfa/verify", Map.of("mfaToken", mfaToken2, "code", backupCode));
        assertEquals(401, reusedBackupRes.statusCode());

        // 9. Login works with live TOTP code
        String freshCode = totpService.generateCurrentCode(secret);
        var totpVerifyRes = post("/api/auth/mfa/verify", Map.of("mfaToken", mfaToken2, "code", freshCode));
        assertEquals(200, totpVerifyRes.statusCode());

        // 10. Disable MFA via POST /api/auth/mfa/disable
        String disableCode = totpService.generateCurrentCode(secret);
        var disableReq = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/mfa/disable"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + fullSessionToken)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
                        Map.of("password", password, "code", disableCode))))
                .build();
        var disableRes = client.send(disableReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, disableRes.statusCode());

        // 11. Profile now reflects mfaEnabled == false
        assertEquals(200, me(fullSessionToken).statusCode());
        assertFalse(json.readTree(me(fullSessionToken).body()).get("mfaEnabled").asBoolean());
    }
}
