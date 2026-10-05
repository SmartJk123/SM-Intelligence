package com.smi.identity_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smi.identity_service.service.PasswordResetMailer;
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

// Forgot password end to end over real HTTP, with the email captured instead of sent.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PasswordResetIntegrationTest {
    @LocalServerPort int port;
    @MockitoBean PasswordResetMailer mailer;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    private HttpResponse<String> post(String route, Map<String, String> body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + route))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }

    private String emailedToken(String email) {
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(mailer, timeout(5000)).sendResetLink(eq(email), anyString(), link.capture(), anyLong());
        return link.getValue().substring(link.getValue().indexOf("token=") + 6);
    }

    @Test void resetsThePasswordOnceWithTheEmailedLink() throws Exception {
        String email = "reset-" + UUID.randomUUID() + "@example.invalid";
        assertEquals(201, post("/api/auth/register", Map.of("name", "Reset User", "emailAddress", email, "password", "OriginalPassword1!")).statusCode());

        var requested = post("/api/auth/forgot-password", Map.of("emailAddress", email.toUpperCase()));
        assertEquals(202, requested.statusCode(), requested.body());
        String token = emailedToken(email);

        assertEquals(400, post("/api/auth/reset-password", Map.of("token", token, "password", "short")).statusCode());
        assertEquals(204, post("/api/auth/reset-password", Map.of("token", token, "password", "BrandNewPassword2!")).statusCode());

        assertEquals(401, post("/api/auth/login", Map.of("emailAddress", email, "password", "OriginalPassword1!")).statusCode());
        assertEquals(200, post("/api/auth/login", Map.of("emailAddress", email, "password", "BrandNewPassword2!")).statusCode());

        var reused = post("/api/auth/reset-password", Map.of("token", token, "password", "AnotherPassword3!"));
        assertEquals(400, reused.statusCode());
        assertTrue(reused.body().contains("INVALID_RESET_TOKEN"), reused.body());
    }

    @Test void answersTheSameForAnUnknownAddressAndSendsNothing() throws Exception {
        var requested = post("/api/auth/forgot-password", Map.of("emailAddress", "nobody-" + UUID.randomUUID() + "@example.invalid"));
        assertEquals(202, requested.statusCode());
        assertTrue(requested.body().contains("If an account exists"));
        verify(mailer, after(300).never()).sendResetLink(anyString(), anyString(), anyString(), anyLong());
        assertEquals(400, post("/api/auth/reset-password", Map.of("token", "made-up-token", "password", "BrandNewPassword2!")).statusCode());
    }
}
