package io.smartmoney.api.bankintegration.ncba;

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
 * NCBA connector.

 * NCBA is push only and there is no API of theirs to call: the endpoint
 * address, the secret key, the username, the password and the account number
 * are handed to the bank in writing. So the connection test cannot prove the
 * integration by asking NCBA anything. It reports what this service has been
 * given, whether the address it will publish can actually be reached, and
 * whether a notification that arrives will be verified rather than refused.
 */
@Service
public class NcbaConnector implements BankConnector {

    private static final String BANK_ID = "ncba";

    private final NcbaProperties props;
    private final PlatformProperties platform;
    private final CallbackReachability reachability;
    private final SignaturePolicy signaturePolicy;

    public NcbaConnector(
            NcbaProperties props,
            PlatformProperties platform,
            CallbackReachability reachability,
            SignaturePolicy signaturePolicy) {
        this.props = props;
        this.platform = platform;
        this.reachability = reachability;
        this.signaturePolicy = signaturePolicy;
    }

    @Override
    public String bankId() {
        return BANK_ID;
    }

    @Override
    public String displayName() {
        return "NCBA Bank Kenya";
    }

    @Override
    public BankConnectionTest testConnection(BankConnectionSettings settings) {
        long started = System.nanoTime();
        List<ConnectionStep> steps = new ArrayList<>();

        steps.add(credentialsStep());
        steps.add(notificationChannelStep());
        steps.add(webhookStep());
        steps.add(signatureStep());

        return BankConnectionTest.of(steps, elapsedMs(started));
    }

    /**
     * There is no token to fetch, so the proof of configuration is that the
     * three values NCBA was given are loaded. None of them is ever printed.
     */
    private ConnectionStep credentialsStep() {
        List<String> missing = new ArrayList<>();
        if (!NcbaProperties.isPresent(props.secretKey())) {
            missing.add("NCBA_SECRET_KEY");
        }
        if (!NcbaProperties.isPresent(props.username())) {
            missing.add("NCBA_USERNAME");
        }
        if (!NcbaProperties.isPresent(props.password())) {
            missing.add("NCBA_PASSWORD");
        }
        if (!missing.isEmpty()) {
            return ConnectionStep.fail(StepKey.TOKEN, "Endpoint credentials",
                    "NCBA has no token endpoint, so the endpoint credentials are what we give them. "
                            + "Set " + String.join(", ", missing)
                            + " in .env.local and restart the service.");
        }
        return ConnectionStep.ok(StepKey.TOKEN, "Endpoint credentials",
                "Secret key, username and password are loaded. These are the values to send to NCBA "
                        + "on the request letter, and the values a notification is checked against.");
    }

    private ConnectionStep notificationChannelStep() {
        if (!NcbaProperties.isPresent(props.accountNumber())) {
            return ConnectionStep.warn(StepKey.ACCOUNT_PROBE, "Notification channel",
                    "Set NCBA_ACCOUNT_NUMBER to the account NCBA will watch. NCBA pushes "
                            + "notifications to this service, so there is no pull call to make.");
        }
        return ConnectionStep.ok(StepKey.ACCOUNT_PROBE, "Notification channel",
                "Watching account " + props.maskedAccountNumber()
                        + ". NCBA pushes an XML notification to this service on every credit and "
                        + "debit, so no pull call is required.");
    }

    private ConnectionStep webhookStep() {
        String callback = platform.callbackUrl(BANK_ID);
        CallbackReachability.State state = reachability.state(BANK_ID);

        if (state == CallbackReachability.State.RESOLVABLE) {
            return ConnectionStep.ok(StepKey.WEBHOOK_REGISTRATION, "Notification address",
                    "Callback URL " + callback + " resolves. Give this address to NCBA on the "
                            + "request letter, and keep it stable because they configure it on their side.");
        }
        if (state == CallbackReachability.State.UNRESOLVABLE) {
            return ConnectionStep.fail(StepKey.WEBHOOK_REGISTRATION, "Notification address",
                    "Callback URL " + callback + " does not resolve, so NCBA cannot deliver to it. "
                            + "A quick tunnel is issued a new hostname every time it starts and the "
                            + "previous address stops resolving, which would leave NCBA posting into "
                            + "nothing. Start the tunnel again, put the new address in PUBLIC_BASE_URL "
                            + "and restart before sending the letter.");
        }
        return ConnectionStep.warn(StepKey.WEBHOOK_REGISTRATION, "Notification address",
                "Callback URL is " + callback + ". NCBA cannot reach localhost, so use a public "
                        + "HTTPS address that stays the same before telling NCBA this address.");
    }

    private ConnectionStep signatureStep() {
        if (!signaturePolicy.verifies(BANK_ID, props.signatureVerification())) {
            return ConnectionStep.warn(StepKey.SIGNATURE_VERIFICATION, "Signature verification",
                    "Signature verification is switched off, so a notification could be forged. "
                            + "Turn it back on before production.");
        }
        if (!NcbaProperties.isPresent(props.secretKey())) {
            return ConnectionStep.warn(StepKey.SIGNATURE_VERIFICATION, "Signature verification",
                    "Set NCBA_SECRET_KEY to the secret key given to NCBA. While verification is "
                            + "enabled and no secret key is loaded, every notification is refused.");
        }
        return ConnectionStep.ok(StepKey.SIGNATURE_VERIFICATION, "Signature verification",
                "The HashVal on each notification is recomputed from the secret key and compared, "
                        + "and the User and Password elements are checked against the values NCBA was given.");
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
