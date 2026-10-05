package io.smartmoney.api.bankintegration.stanbic;

import io.smartmoney.api.bankintegration.BankEnvironment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** STANBIC_ENV picks the endpoints, and explicit URLs still win. */
class StanbicEndpointsTests {

    private static StanbicProperties props(BankEnvironment environment, String tokenUrl, String apiBaseUrl) {
        return new StanbicProperties(environment, null, tokenUrl, apiBaseUrl, "key", "secret", "0100013306316",
                null, null, null, "APGW", "SmartMoney Intelligence", "payments", 30, 3, 5,
                "/api/v1/webhooks/stanbic", true, "X-Stanbic-Signature", "");
    }

    @Test
    void productionUsesTheProdRegisterUrl() {
        StanbicProperties production = props(BankEnvironment.PRODUCTION, "", "");
        assertThat(production.registrationUrl())
                .isEqualTo("https://api.connect.stanbicbank.co.ke/api/prod/registerurl/");
        assertThat(production.tokenUrl())
                .isEqualTo("https://api.connect.stanbicbank.co.ke/api/prod/auth/oauth2/token");
    }

    @Test
    void sandboxIsTheDefault() {
        StanbicProperties sandbox = props(BankEnvironment.SANDBOX, null, null);
        assertThat(sandbox.registrationUrl())
                .isEqualTo("https://api.connect.stanbicbank.co.ke/api/sandbox/registerurl/");
        assertThat(sandbox.tokenUrl())
                .isEqualTo("https://api.connect.stanbicbank.co.ke/api/sandbox/auth/oauth2/token");
    }

    @Test
    void explicitUrlsOverrideTheEnvironment() {
        StanbicProperties custom = props(BankEnvironment.PRODUCTION, "https://example.test/token", "https://example.test/api");
        assertThat(custom.tokenUrl()).isEqualTo("https://example.test/token");
        assertThat(custom.registrationUrl()).isEqualTo("https://example.test/api/registerurl/");
    }
}
