package io.smartmoney.api.bankintegration.kcb;

import io.smartmoney.api.bankintegration.BankConnectionSettings;
import io.smartmoney.api.bankintegration.BankConnectionTest;
import io.smartmoney.api.bankintegration.BankConnector;
import io.smartmoney.api.bankintegration.CallbackReachability;
import io.smartmoney.api.bankintegration.ConnectionStep;
import io.smartmoney.api.bankintegration.StepKey;
import io.smartmoney.api.bankintegration.SignaturePolicy;
import io.smartmoney.api.config.PlatformProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * KCB connector.
 *
 * KCB's instant payment notification is push based: KCB posts to us. So the
 * connectivity proof is the OAuth token, and the readiness checks are the
 * callback address and the signature key.
 */
@Service
public class KcbConnector implements BankConnector {

    private static final String BANK_ID = "kcb";

    private final KcbProperties props;
    private final PlatformProperties platform;
    private final KcbTokenService tokens;
    private final CallbackReachability reachability;
    private final SignaturePolicy signaturePolicy;

    public KcbConnector(
            KcbProperties props,
            PlatformProperties platform,
            KcbTokenService tokens,
            CallbackReachability reachability,
            SignaturePolicy signaturePolicy) {
        this.props = props;
        this.platform = platform;
        this.tokens = tokens;
        this.reachability = reachability;
        this.signaturePolicy = signaturePolicy;
    }

    @Override
    public String bankId() {
        return BANK_ID;
    }

    @Override
    public String displayName() {
        return "KCB Bank Kenya";
    }

    @Override
    public BankConnectionTest testConnection(BankConnectionSettings settings) {
        long started = System.nanoTime();
        List<ConnectionStep> steps = new ArrayList<>();

        String environment = settings.environment() == null ? "Sandbox" : settings.environment().value();

        try {
            tokens.accessToken();
            steps.add(ConnectionStep.ok(StepKey.TOKEN, "Token request",
                    "Access token issued by BUNI for the " + environment
                            + " environment using the client credentials grant."));
        } catch (Exception error) {
            steps.add(ConnectionStep.fail(StepKey.TOKEN, "Token request", error.getMessage()));
        }

        steps.add(notificationChannelStep());
        steps.add(webhookStep());
        steps.add(signatureStep());

        return BankConnectionTest.of(steps, elapsedMs(started));
    }

    /**
     * KCB pushes notifications to us, so there is no balance or account call to
     * make here. The token is the proof that our credentials are accepted.
     */
    private ConnectionStep notificationChannelStep() {
        if (!props.credentialsConfigured()) {
            return ConnectionStep.fail(StepKey.ACCOUNT_PROBE, "Notification channel",
                    "Generate a sandbox Key and Secret for the application in BUNI, then set "
                            + "KCB_CLIENT_KEY and KCB_CLIENT_SECRET.");
        }
        return ConnectionStep.ok(StepKey.ACCOUNT_PROBE, "Notification channel",
                "KCB delivers instant payment notifications to this service, so no pull call is required. "
                        + "The token request above confirms the credentials.");
    }

    private ConnectionStep webhookStep() {
        String callback = platform.callbackUrl(BANK_ID);
        CallbackReachability.State state = reachability.state(BANK_ID);

        if (state == CallbackReachability.State.RESOLVABLE) {
            return ConnectionStep.ok(StepKey.WEBHOOK_REGISTRATION, "Webhook endpoint registration",
                    "Callback URL " + callback + " resolves. Subscribe this address to the instant payment "
                            + "notification API in BUNI and confirm that the subscription is active.");
        }
        if (state == CallbackReachability.State.UNRESOLVABLE) {
            return ConnectionStep.fail(StepKey.WEBHOOK_REGISTRATION, "Webhook endpoint registration",
                    "Callback URL " + callback + " does not resolve, so BUNI cannot deliver to it. A quick "
                            + "tunnel is issued a new hostname every time it starts and the previous address "
                            + "stops resolving. Start the tunnel again, put the new address in PUBLIC_BASE_URL "
                            + "and restart the service before subscribing anything.");
        }
        return ConnectionStep.warn(StepKey.WEBHOOK_REGISTRATION, "Webhook endpoint registration",
                "Callback URL is " + callback + ". BUNI cannot reach localhost, so start a tunnel and set "
                        + "PUBLIC_BASE_URL before subscribing this address.");
    }

    private ConnectionStep signatureStep() {
        if (!signaturePolicy.verifies(BANK_ID, props.signatureVerification())) {
            return ConnectionStep.warn(StepKey.SIGNATURE_VERIFICATION, "Signature verification",
                    "Signature verification is disabled, so a notification could be forged.");
        }
        if (!KcbProperties.isPresent(props.publicKey())) {
            return ConnectionStep.warn(StepKey.SIGNATURE_VERIFICATION, "Signature verification",
                    "Set KCB_PUBLIC_KEY to the KCB public key so the Signature header can be verified "
                            + "with SHA256withRSA. Until then notifications will be rejected while verification is enabled.");
        }
        return ConnectionStep.ok(StepKey.SIGNATURE_VERIFICATION, "Signature verification",
                "Notifications are verified with SHA256withRSA using the configured KCB public key.");
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
