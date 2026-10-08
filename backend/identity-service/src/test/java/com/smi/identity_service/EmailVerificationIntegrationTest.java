package com.smi.identity_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smi.identity_service.service.EmailVerificationMailer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EmailVerificationIntegrationTest {

    @LocalServerPort int port;
    @MockitoBean EmailVerificationMailer mailer;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    private HttpResponse<String> post(String route, Map<String, String> body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + route))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }

    private String emailedToken(String email) {
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(mailer, timeout(5000)).sendVerificationLink(eq(email), anyString(), link.capture(), anyLong());
        return link.getValue().substring(link.getValue().indexOf("token=") + 6);
    }

    @Test
    void emailVerificationFlowEndToEnd() throws Exception {
        String email = "verify-" + UUID.randomUUID() + "@example.invalid";
        var reg = post("/api/auth/register", Map.of(
                "name", "Verify User",
                "emailAddress", email,
                "password", "ValidPassword123!"
        ));
        assertEquals(201, reg.statusCode(), reg.body());
        String token = json.readTree(reg.body()).get("token").asText();

        // 1. Initial profile shows isEmailVerified == false
        var meReq = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/me"))
                .header("Authorization", "Bearer " + token).build();
        var meRes = client.send(meReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, meRes.statusCode());
        assertFalse(json.readTree(meRes.body()).get("isEmailVerified").asBoolean());

        // 2. Capture verification token sent to mailer upon registration
        String verificationToken = emailedToken(email);
        assertNotNull(verificationToken);

        // 3. Confirm email via POST /api/auth/verify-email
        var confirmRes = post("/api/auth/verify-email", Map.of("token", verificationToken));
        assertEquals(200, confirmRes.statusCode(), confirmRes.body());

        // 4. Subsequent profile check shows isEmailVerified == true
        var verifiedMeRes = client.send(meReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, verifiedMeRes.statusCode());
        assertTrue(json.readTree(verifiedMeRes.body()).get("isEmailVerified").asBoolean());

        // 5. Reusing the token fails with 400 Bad Request
        var reusedRes = post("/api/auth/verify-email", Map.of("token", verificationToken));
        assertEquals(400, reusedRes.statusCode());
        assertTrue(reusedRes.body().contains("INVALID_EMAIL_VERIFICATION_TOKEN"));
    }
}
