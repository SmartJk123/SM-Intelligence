package io.smartmoney.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smartmoney.api.bankintegration.kcb.KcbProperties;
import io.smartmoney.api.bankintegration.kcb.KcbSignatureVerifier;
import io.smartmoney.api.bankintegration.SignaturePolicy;
import io.smartmoney.api.bankintegration.stanbic.StanbicProperties;
import io.smartmoney.api.bankintegration.stanbic.StanbicSignatureVerifier;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SignatureVerifierTests {

    /** Stands in for the saved setting: the environment value is used until one exists. */
    private static final SignaturePolicy UNCONFIGURED = (bankId, whenUnset) -> whenUnset;

    @Test
    void stanbicVerificationFailsClosedWhenEnabledButUnconfigured() {
        StanbicProperties props = mock(StanbicProperties.class);
        when(props.signatureVerification()).thenReturn(true);
        when(props.signatureHeader()).thenReturn("");
        when(props.signatureSecret()).thenReturn("");

        Boolean result = new StanbicSignatureVerifier(props, new ObjectMapper(), UNCONFIGURED)
                .verify("{}", Map.of());

        assertThat(result).isFalse();
    }

    @Test
    void kcbVerificationFailsClosedWhenEnabledButUnconfigured() {
        KcbProperties props = mock(KcbProperties.class);
        when(props.signatureVerification()).thenReturn(true);
        when(props.publicKey()).thenReturn("");

        Boolean result = new KcbSignatureVerifier(props, UNCONFIGURED).verify("{}", Map.of());

        assertThat(result).isFalse();
    }
}
