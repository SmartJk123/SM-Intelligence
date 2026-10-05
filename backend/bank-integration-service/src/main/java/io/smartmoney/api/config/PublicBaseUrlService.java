package io.smartmoney.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;

/**
 * The public address every bank's webhook URL is built from.
 *
 * PUBLIC_BASE_URL sets it until an administrator saves another one from the
 * admin interface. A saved address is stored and applied again at every start,
 * so production keeps its address across restarts without editing .env.local.
 */
@Service
public class PublicBaseUrlService {

    private static final Logger log = LoggerFactory.getLogger(PublicBaseUrlService.class);
    static final String KEY = "public-base-url";

    private final PlatformProperties platform;
    private final PlatformSettingRepository settings;

    public PublicBaseUrlService(PlatformProperties platform, PlatformSettingRepository settings) {
        this.platform = platform;
        this.settings = settings;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void applySaved() {
        settings.findById(KEY).ifPresent(saved -> {
            platform.setPublicBaseUrl(saved.getValue());
            log.info("Webhook base address is {} (saved from the admin interface)", saved.getValue());
        });
    }

    public String current() {
        return platform.publicBaseUrl();
    }

    /** Validates, stores and applies a new address. @throws IllegalArgumentException when it is not usable. */
    @Transactional
    public String update(String candidate) {
        String value = normalise(candidate);
        PlatformSettingEntity setting = settings.findById(KEY).orElseGet(() -> new PlatformSettingEntity(KEY, value));
        setting.setValue(value);
        settings.save(setting);
        platform.setPublicBaseUrl(value);
        log.info("Webhook base address changed to {}", value);
        return value;
    }

    /** An absolute http(s) origin with no path, query or trailing slash, such as https://sm-intelligence.globalsmartspaces.com */
    static String normalise(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            throw new IllegalArgumentException("Enter the public address, for example https://sm-intelligence.globalsmartspaces.com");
        }
        String trimmed = candidate.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        // Pasting a full webhook URL is a natural mistake; keep only the base.
        int webhooks = trimmed.indexOf("/api/v1/webhooks");
        if (webhooks > 0) {
            trimmed = trimmed.substring(0, webhooks);
        }
        URI parsed;
        try {
            parsed = URI.create(trimmed);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("That is not a valid address");
        }
        String scheme = parsed.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) || parsed.getHost() == null) {
            throw new IllegalArgumentException("The address must start with https:// (or http:// for local testing)");
        }
        if (parsed.getQuery() != null || parsed.getFragment() != null) {
            throw new IllegalArgumentException("The address must not contain ? or #");
        }
        if (trimmed.length() > 500) {
            throw new IllegalArgumentException("The address is too long");
        }
        return trimmed;
    }
}
