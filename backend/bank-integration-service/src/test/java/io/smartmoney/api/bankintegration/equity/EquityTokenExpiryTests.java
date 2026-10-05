package io.smartmoney.api.bankintegration.equity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Jenga names expiresIn without fixing its format, so each plausible form is read. */
class EquityTokenExpiryTests {

    private final ObjectMapper mapper = new ObjectMapper();

    private Instant expiry(String json) throws Exception {
        return EquityTokenService.expiry(mapper.readTree("{\"expiresIn\":" + json + "}").path("expiresIn"));
    }

    @Test
    void readsSecondsEpochAndIsoForms() throws Exception {
        Instant now = Instant.now();
        assertThat(Duration.between(now, expiry("3599"))).isBetween(Duration.ofSeconds(3590), Duration.ofSeconds(3600));
        assertThat(expiry("1790000000")).isEqualTo(Instant.ofEpochSecond(1790000000L));
        assertThat(expiry("1790000000000")).isEqualTo(Instant.ofEpochMilli(1790000000000L));
        assertThat(expiry("\"2026-10-05T12:00:00Z\"")).isEqualTo(Instant.parse("2026-10-05T12:00:00Z"));
        assertThat(Duration.between(now, expiry("null"))).isBetween(Duration.ofMinutes(59), Duration.ofMinutes(61));
    }
}
