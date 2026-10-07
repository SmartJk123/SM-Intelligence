package io.smartmoney.api.bankintegration.kcb;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KcbTokenServiceTests {

    private KcbTokenService tokenService;

    @BeforeEach
    void setUp() {
        KcbProperties props = new KcbProperties(
                null, null, "https://accounts.buni.kcbgroup.com/oauth2/token",
                null, null, "test-key", "test-secret", "basic",
                "/api/v1/webhooks/kcb", true, "Signature", null, 30
        );
        tokenService = new KcbTokenService(props, new ObjectMapper());
    }

    @Test
    void parsesValidTokenWithExpiresIn() {
        String json = "{\"access_token\": \"kcb-jwt-token-123\", \"token_type\": \"Bearer\", \"expires_in\": 3599}";
        var response = tokenService.parse(json);

        assertThat(response.accessToken()).isEqualTo("kcb-jwt-token-123");
        assertThat(response.expiresIn()).isEqualTo(3599L);
    }

    @Test
    void parsesValidTokenWithoutExpiresIn() {
        String json = "{\"access_token\": \"kcb-jwt-token-456\"}";
        var response = tokenService.parse(json);

        assertThat(response.accessToken()).isEqualTo("kcb-jwt-token-456");
        assertThat(response.expiresIn()).isNull();
    }

    @Test
    void throwsOnMissingAccessToken() {
        String json = "{\"error\": \"invalid_client\", \"error_description\": \"Client authentication failed\"}";
        assertThatThrownBy(() -> tokenService.parse(json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("did not return an access_token");
    }

    @Test
    void throwsOnEmptyResponse() {
        assertThatThrownBy(() -> tokenService.parse(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty response");
    }

    @Test
    void throwsHelpfulHintOnHtmlGatewayError() {
        String html = "<!DOCTYPE html><html><body>WAF Blocked</body></html>";
        assertThatThrownBy(() -> tokenService.parse(html))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("returned an HTML page instead of a token");
    }

    @Test
    void throwsOnMalformedJson() {
        String malformed = "{unquoted_key: 123}";
        assertThatThrownBy(() -> tokenService.parse(malformed))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("response that is not JSON");
    }
}
