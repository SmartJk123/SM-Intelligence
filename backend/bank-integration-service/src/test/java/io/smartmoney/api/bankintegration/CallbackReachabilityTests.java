package io.smartmoney.api.bankintegration;

import io.smartmoney.api.config.PlatformProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The webhook step used to report a green result for any HTTPS address that was
 * not localhost, including a tunnel hostname that had stopped resolving. These
 * tests pin the three states so a string comparison cannot creep back in.
 *
 * The resolvable state is not covered here because it needs a live resolver.
 * The .invalid top level domain is reserved by RFC 6761 and never resolves, so
 * it is safe to assert on.
 */
class CallbackReachabilityTests {

    private static CallbackReachability reachabilityFor(String baseUrl) {
        return new CallbackReachability(
                new PlatformProperties(baseUrl, new PlatformProperties.Security(true)));
    }

    @Test
    void aLocalhostAddressIsReportedAsLocal() {
        assertThat(reachabilityFor("http://localhost:8080").state("kcb"))
                .isEqualTo(CallbackReachability.State.LOCAL);
    }

    @Test
    void anAddressThatNoLongerResolvesIsReportedAsUnresolvable() {
        assertThat(reachabilityFor("https://tunnel-that-stopped.invalid").state("stanbic"))
                .isEqualTo(CallbackReachability.State.UNRESOLVABLE);
    }

    @Test
    void anAddressWithoutAHostIsReportedAsUnresolvable() {
        assertThat(reachabilityFor("https://").state("kcb"))
                .isEqualTo(CallbackReachability.State.UNRESOLVABLE);
    }
}