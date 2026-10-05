package io.smartmoney.api.bankintegration.equity;

import io.smartmoney.api.bankintegration.BankConnectionSettings;
import io.smartmoney.api.bankintegration.BankConnectionTest;
import io.smartmoney.api.bankintegration.BankConnector;
import io.smartmoney.api.bankintegration.CallbackReachability;
import io.smartmoney.api.bankintegration.ConnectionStep;
import io.smartmoney.api.bankintegration.SignaturePolicy;
import io.smartmoney.api.bankintegration.StepKey;
import io.smartmoney.api.bankintegration.TokenSnapshot;
import io.smartmoney.api.bankintegration.TokenStateProvider;
import io.smartmoney.api.config.PlatformProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Equity Bank connector, over the Jenga API.
 *
 * Payments reach us as Instant Payment Notifications pushed to our callback,
 * so the connectivity proof is the merchant token, and the readiness checks
 * are the callback address and the Basic Auth credentials registered with it.
 */
@Service
public class EquityConnector implements BankConnector, TokenStateProvider {

    private static final String BANK_ID = "equity";

    private final EquityProperties props;
    private final PlatformProperties platform;
    private final EquityTokenService tokens;
    private final CallbackReachability reachability;
    private final SignaturePolicy signaturePolicy;

    public EquityConnector(EquityProperties props, PlatformProperties platform, EquityTokenService tokens,
                           CallbackReachability reachability, SignaturePolicy signaturePolicy) {
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
        return "Equity Bank Kenya";
    }

    @Override
    public TokenSnapshot tokenSnapshot() {
        return tokens.tokenSnapshot();
    }

    @Override
    public BankConnectionTest testConnection(BankConnectionSettings settings) {
        long started = System.nanoTime();
        List<ConnectionStep> steps = new ArrayList<>();
        String environment = settings.environment() == null ? "Sandbox" : settings.environment().value();

        try {
            tokens.accessToken();
            steps.add(ConnectionStep.ok(StepKey.TOKEN, "Token request",
                    "Jenga issued a merchant access token for the " + environment + " environment."));
        } catch (Exception error) {
            steps.add(ConnectionStep.fail(StepKey.TOKEN, "Token request", error.getMessage()));
        }

        steps.add(props.credentialsConfigured()
                ? ConnectionStep.ok(StepKey.ACCOUNT_PROBE, "Notification channel",
                        "Equity pushes Instant Payment Notifications to this service, so no pull call is "
                                + "required. The token request above confirms the credentials.")
                : ConnectionStep.fail(StepKey.ACCOUNT_PROBE, "Notification channel",
                        "Copy the merchant code, API key and consumer secret from Jenga HQ into "
                                + "EQUITY_MERCHANT_CODE, EQUITY_API_KEY and EQUITY_CONSUMER_SECRET."));
        steps.add(webhookStep());
        steps.add(authenticationStep());

        return BankConnectionTest.of(steps, (System.nanoTime() - started) / 1_000_000);
    }

    private ConnectionStep webhookStep() {
        String callback = platform.callbackUrl(BANK_ID);
        return switch (reachability.state(BANK_ID)) {
            case RESOLVABLE -> ConnectionStep.ok(StepKey.WEBHOOK_REGISTRATION, "Webhook endpoint registration",
                    "Callback URL " + callback + " resolves. Register it as the IPN callback on Jenga HQ, "
                            + "with the username and password in EQUITY_IPN_USERNAME and EQUITY_IPN_PASSWORD.");
            case UNRESOLVABLE -> ConnectionStep.fail(StepKey.WEBHOOK_REGISTRATION, "Webhook endpoint registration",
                    "Callback URL " + callback + " does not resolve, so Jenga cannot deliver to it. Set the "
                            + "webhook base address to the public address first.");
            default -> ConnectionStep.warn(StepKey.WEBHOOK_REGISTRATION, "Webhook endpoint registration",
                    "Callback URL is " + callback + ". Jenga cannot reach localhost, so set the webhook base "
                            + "address to the public address before registering it.");
        };
    }

    private ConnectionStep authenticationStep() {
        if (!signaturePolicy.verifies(BANK_ID, props.signatureVerification())) {
            return ConnectionStep.warn(StepKey.SIGNATURE_VERIFICATION, "Callback authentication",
                    "Basic Auth checking is off, so anyone could post a payment notification.");
        }
        if (!props.ipnCredentialsConfigured()) {
            return ConnectionStep.warn(StepKey.SIGNATURE_VERIFICATION, "Callback authentication",
                    "Set EQUITY_IPN_USERNAME and EQUITY_IPN_PASSWORD to the Basic Auth credentials registered "
                            + "with the callback on Jenga HQ. Until then every notification is refused.");
        }
        return ConnectionStep.ok(StepKey.SIGNATURE_VERIFICATION, "Callback authentication",
                "Notifications must carry the Basic Auth credentials registered with the callback.");
    }
}
