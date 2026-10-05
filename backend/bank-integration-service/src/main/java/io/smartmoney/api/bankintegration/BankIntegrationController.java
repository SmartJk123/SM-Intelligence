package io.smartmoney.api.bankintegration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import io.smartmoney.api.bankintegration.equity.EquityProperties;
import io.smartmoney.api.bankintegration.kcb.KcbProperties;
import io.smartmoney.api.bankintegration.ncba.NcbaProperties;
import io.smartmoney.api.bankintegration.stanbic.StanbicProperties;
import io.smartmoney.api.bankintegration.stanbic.StanbicRegistrationService;
import io.smartmoney.api.bankintegration.stanbic.StanbicRegistrationService.RegistrationOutcome;
import io.smartmoney.api.config.PlatformProperties;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** API consumed by the Angular admin interface. */
@RestController
@RequestMapping("/api/v1/admin/bank-integrations")
public class BankIntegrationController {

    private final BankIntegrationHealthService healthService;
    private final BankIntegrationSettingsService settingsService;
    private final ConnectionTestRegistry tests;
    private final PlatformProperties platform;
    private final CallbackReachability reachability;
    private final StanbicProperties stanbic;
    private final KcbProperties kcb;
    private final NcbaProperties ncba;
    private final EquityProperties equity;
    private final StanbicRegistrationService stanbicRegistrations;
    private final ObjectMapper mapper;
    private final Map<String, BankConnector> connectors;
    private final DemoTransactionService demo;

    public BankIntegrationController(
            BankIntegrationHealthService healthService,
            BankIntegrationSettingsService settingsService,
            ConnectionTestRegistry tests,
            PlatformProperties platform,
            CallbackReachability reachability,
            StanbicProperties stanbic,
            KcbProperties kcb,
            NcbaProperties ncba,
            EquityProperties equity,
            StanbicRegistrationService stanbicRegistrations,
            ObjectMapper mapper,
            List<BankConnector> connectors,
            DemoTransactionService demo) {
        this.healthService = healthService;
        this.settingsService = settingsService;
        this.tests = tests;
        this.platform = platform;
        this.reachability = reachability;
        this.stanbic = stanbic;
        this.kcb = kcb;
        this.ncba = ncba;
        this.equity = equity;
        this.stanbicRegistrations = stanbicRegistrations;
        this.mapper = mapper;
        this.demo = demo;
        this.connectors = connectors.stream()
                .collect(Collectors.toMap(BankConnector::bankId, Function.identity()));
    }

    @GetMapping
    public List<IntegrationHealth> health() {
        return healthService.health();
    }

    @GetMapping("/{bankId}")
    public IntegrationHealth healthFor(@PathVariable String bankId) {
        return healthService.healthFor(bankId);
    }

    @GetMapping("/{bankId}/settings")
    public BankConnectionSettings settingsFor(@PathVariable String bankId) {
        return settingsService.settingsFor(bankId);
    }

    /**
     * Which credentials the backend has loaded, without ever returning a secret.
     * The portal issues the OAuth client key and secret; the ApiKey field of the
     * register request falls back to the client key when no separate value exists.
     */
    @GetMapping("/{bankId}/credentials")
    public ResponseEntity<IntegrationCredentials> credentialsFor(@PathVariable String bankId) {
        if ("kcb".equals(bankId)) {
            return ResponseEntity.ok(kcbCredentials());
        }
        if ("ncba".equals(bankId)) {
            return ResponseEntity.ok(ncbaCredentials());
        }
        if ("equity".equals(bankId)) {
            return ResponseEntity.ok(equityCredentials());
        }
        if (!"stanbic".equals(bankId)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(stanbicCredentials());
    }

    /**
     * KCB issues an OAuth client key and secret from BUNI. There is no separate
     * ApiKey field and no registration call to make: the notification address is
     * set on the application in the portal, so that part cannot be tested from
     * here and the response says so.
     */
    private IntegrationCredentials kcbCredentials() {
        return new IntegrationCredentials(
                "kcb",
                KcbProperties.isPresent(kcb.clientKey()),
                StanbicProperties.tail(kcb.clientKey()),
                KcbProperties.isPresent(kcb.clientSecret()),
                false,
                "",
                "not applicable",
                kcb.tokenUrl(),
                KcbProperties.isPresent(kcb.apiBaseUrl()) ? kcb.apiBaseUrl() : "",
                "",
                "",
                "",
                tokenReady("kcb"),
                false,
                kcbCredentialsAdvice(),
                "The signature check uses KCB_PUBLIC_KEY. While it holds the local rehearsal key, "
                        + "notifications signed by KCB report as invalid rather than unchecked. "
                        + "Clear it, or replace it with the KCB public key, before testing with the bank.");
    }

    private String kcbCredentialsAdvice() {
        if (!KcbProperties.isPresent(kcb.clientKey()) || !KcbProperties.isPresent(kcb.clientSecret())) {
            return "Generate a sandbox Key and Secret for the application in BUNI, then save them as "
                    + "KCB_CLIENT_KEY and KCB_CLIENT_SECRET and restart the service.";
        }
        if (!KcbProperties.isPresent(kcb.publicKey())) {
            return "Key and Secret are loaded. No public key is set, so incoming notifications are "
                    + "stored and reported as not checked rather than rejected. Ask KCB for their "
                    + "public key and set KCB_PUBLIC_KEY to verify signatures.";
        }
        return "Key, Secret and public key are loaded. Set the notification address on the "
                + "application in BUNI, then send a test notification.";
    }

    /**
     * Jenga issues a merchant code, API key and consumer secret. The IPN
     * callback is protected with Basic Auth credentials registered with it on
     * Jenga HQ. None of the values is ever returned.
     */
    private IntegrationCredentials equityCredentials() {
        boolean ipnReady = equity.ipnCredentialsConfigured();
        String advice;
        if (!equity.credentialsConfigured()) {
            advice = "Copy the merchant code, API key and consumer secret from Jenga HQ into EQUITY_MERCHANT_CODE, "
                    + "EQUITY_API_KEY and EQUITY_CONSUMER_SECRET, then restart the service.";
        } else if (!ipnReady) {
            advice = "Credentials are loaded. Register the webhook URL as the IPN callback on Jenga HQ with a "
                    + "username and password, and set the same values as EQUITY_IPN_USERNAME and EQUITY_IPN_PASSWORD.";
        } else {
            advice = "Credentials and IPN Basic Auth are loaded. Send a test payment to confirm delivery.";
        }
        return new IntegrationCredentials(
                "equity",
                EquityProperties.isPresent(equity.apiKey()),
                StanbicProperties.tail(equity.apiKey()),
                EquityProperties.isPresent(equity.consumerSecret()),
                EquityProperties.isPresent(equity.merchantCode()),
                StanbicProperties.tail(equity.merchantCode()),
                "merchant code",
                equity.tokenUrl(),
                "",
                "",
                "",
                equity.maskedAccountNumber() == null ? "" : equity.maskedAccountNumber(),
                tokenReady("equity"),
                ipnReady,
                advice,
                "Equity pushes Instant Payment Notifications for successful and failed payments; only "
                        + "successful ones are credited to a customer.");
    }

    /**
     * NCBA issues nothing over an API. The endpoint credentials are the values
     * this service gives the bank, so what matters to an operator is which of
     * them are loaded. None of the values is ever returned.
     */
    private IntegrationCredentials ncbaCredentials() {
        boolean credentialsReady = NcbaProperties.isPresent(ncba.secretKey())
                && NcbaProperties.isPresent(ncba.username())
                && NcbaProperties.isPresent(ncba.password());
        boolean addressPublic = reachability.state("ncba") == CallbackReachability.State.RESOLVABLE;

        return new IntegrationCredentials(
                "ncba",
                NcbaProperties.isPresent(ncba.secretKey()),
                NcbaProperties.tail(ncba.secretKey()),
                NcbaProperties.isPresent(ncba.password()),
                NcbaProperties.isPresent(ncba.username()),
                NcbaProperties.tail(ncba.username()),
                "username given to NCBA",
                "",
                "",
                "",
                "",
                NcbaProperties.isPresent(ncba.accountNumber()) ? ncba.maskedAccountNumber() : "",
                credentialsReady,
                addressPublic,
                ncbaCredentialsAdvice(credentialsReady, addressPublic),
                "NCBA has no token endpoint and no registration API. It pushes XML to this "
                        + "service, and the secret key, username and password on the request letter are "
                        + "the values every notification is checked against.");
    }

    private String ncbaCredentialsAdvice(boolean credentialsReady, boolean addressPublic) {
        if (!credentialsReady) {
            return "Set NCBA_SECRET_KEY, NCBA_USERNAME and NCBA_PASSWORD in .env.local, then "
                    + "restart. Those three values are what the request letter carries.";
        }
        if (!addressPublic) {
            return "The endpoint credentials are loaded. Set PUBLIC_BASE_URL to a public HTTPS "
                    + "address that stays the same, because NCBA configures this address on their "
                    + "side and a quick tunnel changes hostname every restart.";
        }
        return "The credentials and the address are ready. Send the request letter, then expect a "
                + "test notification during NCBA user acceptance testing.";
    }

    private IntegrationCredentials stanbicCredentials() {
        return new IntegrationCredentials(
                "stanbic",
                StanbicProperties.isPresent(stanbic.clientKey()),
                StanbicProperties.tail(stanbic.clientKey()),
                StanbicProperties.isPresent(stanbic.clientSecret()),
                StanbicProperties.isPresent(stanbic.apiKey()),
                StanbicProperties.tail(stanbic.effectiveApiKey()),
                stanbic.apiKeySource(),
                stanbic.tokenUrl(),
                stanbic.apiBaseUrl(),
                stanbic.registrationUrl(),
                stanbic.oauthScope(),
                stanbic.maskedAccountNumber(),
                tokenReady("stanbic"),
                stanbic.registrationConfigured(),
                apiKeyAdvice(stanbic),
                "Credentials are read from environment variables, so rotating a set on the portal "
                        + "means updating the value and restarting, with no code change.");
    }

    /** A configured credential pair is not the same thing as an issued token. */
    private boolean tokenReady(String bankId) {
        BankConnector connector = connectors.get(bankId);
        if (!(connector instanceof TokenStateProvider provider)) {
            return false;
        }
        TokenStatus status = provider.tokenSnapshot().status();
        return status == TokenStatus.VALID || status == TokenStatus.EXPIRING;
    }

    /**
     * The portal issues the OAuth pair and the token URL. It does not issue a
     * separate ApiKey for the register request, so say plainly where that value
     * would come from if the bank rejects the fallback.
     */
    private static String apiKeyAdvice(StanbicProperties props) {
        if (StanbicProperties.isPresent(props.apiKey())) {
            return "A separate ApiKey is configured and will be sent in the register request.";
        }
        if (StanbicProperties.isPresent(props.clientKey())) {
            return "The developer portal does not issue a separate ApiKey, so the Client Key is sent "
                    + "in that field. If Stanbic rejects the registration, ask the Integrations Team "
                    + "(kilele@stanbic.com) what value the registerurl request expects in ApiKey for "
                    + "this application, then set STANBIC_API_KEY.";
        }
        return "Set the Client Key and Client Secret first. The ApiKey field then falls back to the "
                + "Client Key unless a separate value is supplied.";
    }

    /** Credential status for the admin interface. No secret is ever included. */
    public record IntegrationCredentials(
            String bankId,
            boolean clientKeyConfigured,
            String clientKeyHint,
            boolean clientSecretConfigured,
            boolean apiKeyConfigured,
            String apiKeyHint,
            String apiKeySource,
            String tokenUrl,
            String apiBaseUrl,
            String registrationUrl,
            String oauthScope,
            String accountNumber,
            boolean tokenReady,
            boolean registrationReady,
            String apiKeyAdvice,
            String note) {
    }

    @GetMapping("/{bankId}/webhook-url")
    public Map<String, Object> webhookUrl(@PathVariable String bankId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("bankId", bankId);
        body.put("webhookUrl", platform.callbackUrl(bankId));
        body.put("publiclyReachable", reachability.state(bankId) == CallbackReachability.State.RESOLVABLE);
        body.put("accountNumber", switch (bankId) {
            case "stanbic" -> stanbic.maskedAccountNumber();
            case "equity" -> equity.maskedAccountNumber();
            case "ncba" -> NcbaProperties.isPresent(ncba.accountNumber())
                    ? ncba.maskedAccountNumber() : null;
            default -> null;
        });
        return body;
    }

    /**
     * Accepts an empty body as well as a JSON settings object, and does not care
     * about the content type, so a plain POST works from a terminal.
     */
    @PostMapping(value = "/{bankId}/test", consumes = MediaType.ALL_VALUE)
    public BankConnectionTest test(
            @PathVariable String bankId,
            @RequestBody(required = false) String rawBody) {

        BankConnectionSettings requested = parseSettings(rawBody);
        BankConnectionSettings settings = requested == null
                ? settingsService.settingsFor(bankId)
                : merge(bankId, rawBody);

        BankConnector connector = connectors.get(bankId);
        BankConnectionTest result = connector == null
                ? BankConnectionTest.of(List.of(ConnectionStep.fail(StepKey.TOKEN, "Connector",
                        "No connector is implemented for " + bankId + " yet.")), 0L)
                : connector.testConnection(settings);

        tests.record(bankId, result);
        return result;
    }

    @PutMapping(value = {"/{bankId}", "/{bankId}/settings"}, consumes = MediaType.ALL_VALUE)
    public BankConnectionSettings save(
            @PathVariable String bankId, @RequestBody(required = false) String rawBody) {
        if (parseSettings(rawBody) == null) {
            return settingsService.settingsFor(bankId);
        }
        return settingsService.save(bankId, merge(bankId, rawBody));
    }

    /**
     * Registers the SmartMoney callback URL with Stanbic so real-time credit and
     * debit alerts start arriving. With no notificationType, both are registered
     * so that money in and money out are both visible.
     */
    @PostMapping("/{bankId}/register-callback")
    public List<RegistrationOutcome> registerCallback(
            @PathVariable String bankId,
            @RequestParam(required = false) String notificationType) {

        if (!"stanbic".equals(bankId)) {
            return List.of();
        }
        if (notificationType != null && !notificationType.isBlank()) {
            return List.of(stanbicRegistrations.register(notificationType));
        }
        return List.of(
                stanbicRegistrations.register("CREDIT"),
                stanbicRegistrations.register("DEBIT"));
    }

    private BankConnectionSettings parseSettings(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(rawBody, BankConnectionSettings.class);
        } catch (Exception error) {
            return null;
        }
    }

    /**
     * Fills anything the caller left out from the stored settings.
     *
     * The booleans are why this reads the JSON tree as well as the record. An
     * omitted primitive arrives as false and an omitted number arrives as zero,
     * so a partial request that only changed the environment used to switch
     * signature verification off and reset the retry counts without saying so.
     */
    private BankConnectionSettings merge(String bankId, String rawBody) {
        BankConnectionSettings fallback = settingsService.settingsFor(bankId);
        BankConnectionSettings body = parseSettings(rawBody);
        if (body == null) {
            return fallback;
        }
        JsonNode fields = readTree(rawBody);
        return new BankConnectionSettings(
                bankId,
                body.environment() != null ? body.environment() : fallback.environment(),
                supplied(fields, "apiTimeoutSeconds") ? body.apiTimeoutSeconds() : fallback.apiTimeoutSeconds(),
                supplied(fields, "retryAttempts") ? body.retryAttempts() : fallback.retryAttempts(),
                supplied(fields, "retryDelaySeconds") ? body.retryDelaySeconds() : fallback.retryDelaySeconds(),
                supplied(fields, "signatureVerification") ? body.signatureVerification() : fallback.signatureVerification(),
                supplied(fields, "automaticRetry") ? body.automaticRetry() : fallback.automaticRetry());
    }

    private JsonNode readTree(String rawBody) {
        try {
            return mapper.readTree(rawBody);
        } catch (Exception error) {
            return null;
        }
    }

    /** True only when the request carried the field, so an explicit false is honoured. */
    private static boolean supplied(JsonNode fields, String name) {
        return fields != null && fields.has(name) && !fields.get(name).isNull();
    }

    /**
     * Development helper. Sends a sample payment notification through exactly
     * the same path the bank will use, so the pipeline can be tested before the
     * callback is registered.
     */
    @PostMapping(value = "/{bankId}/simulate-notification",
            consumes = MediaType.ALL_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> simulateNotification(@PathVariable String bankId) {
        DemoTransactionService.DemoRecorded recorded =
                demo.record(bankId, "Credit", null, "Simulated tenant payment", null);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "simulated");
        body.put("eventId", recorded.eventId());
        body.put("reference", recorded.movement().reference());
        body.put("bankId", recorded.movement().bankId());
        body.put("direction", recorded.movement().direction());
        body.put("amount", recorded.movement().amount());
        return body;
    }
}
