package com.smi.identity_service.settings;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The admin portal's saved settings, one section at a time. Every section has
 * defaults here, so an unsaved section still reads sensibly, and only the keys
 * listed here are accepted, each with its type checked.
 *
 * What the settings do:
 *   security.sessionTimeoutMinutes  idle sign-out of the admin portal
 *   security.maxFailedAttempts / lockoutMinutes  sign in is refused after that many
 *       wrong passwords for an email address within lockoutMinutes (0 turns it off)
 *   security.adminEmailDomains      staff may only sign in from these domains (ADMIN_EMAILS always may)
 *   notifications.email*            which administrator notifications are also emailed
 *   notifications.recipients        where they are emailed (empty: ADMIN_EMAILS)
 *   system.maintenanceMode          customers cannot sign in or sign up; staff still can
 *   system.auditRetentionDays       audit entries older than this are deleted daily (0 keeps all)
 */
@Service
public class PlatformSettings {

    private static final Logger log = LoggerFactory.getLogger(PlatformSettings.class);

    public static final List<String> SECTIONS = List.of("general", "security", "notifications", "system");

    private static final Map<String, Map<String, Object>> DEFAULTS = Map.of(
            "general", ordered(
                    "platformName", "SmartMoney Intelligence",
                    "defaultCurrency", "KES",
                    "defaultLanguage", "English (Kenya)",
                    "timezone", "Africa/Nairobi",
                    "dateFormat", "DD/MM/YYYY",
                    "supportEmail", ""),
            "security", ordered(
                    "sessionTimeoutMinutes", 15,
                    "maxFailedAttempts", 5,
                    "lockoutMinutes", 15,
                    "adminEmailDomains", "",
                    "requireAdminMfa", false),
            "notifications", ordered(
                    "emailNewSignUps", true,
                    "emailFirstSignIns", false,
                    "emailAccountChanges", true,
                    "emailSecurity", true,
                    "emailServiceAlerts", true,
                    "recipients", ""),
            "system", ordered(
                    "maintenanceMode", false,
                    "auditRetentionDays", 365));

    /** Keys that hold numbers, with their allowed range. */
    private static final Map<String, int[]> NUMBER_RANGES = Map.of(
            "sessionTimeoutMinutes", new int[] {1, 1440},
            "maxFailedAttempts", new int[] {0, 100},
            "lockoutMinutes", new int[] {1, 1440},
            "auditRetentionDays", new int[] {0, 3650});

    public record Section(String section, Map<String, Object> values, OffsetDateTime updatedAt, String updatedBy) {
    }

    private final PlatformSettingRepository repository;
    private final ObjectMapper json;

    public PlatformSettings(PlatformSettingRepository repository, ObjectMapper json) {
        this.repository = repository;
        this.json = json;
    }

    public Section get(String section) {
        Map<String, Object> defaults = defaults(section);
        Map<String, Object> values = new LinkedHashMap<>(defaults);
        PlatformSetting stored = null;
        try {
            stored = repository.findById(section).orElse(null);
            if (stored != null) {
                Map<String, Object> saved = json.readValue(stored.getValue(), new TypeReference<>() { });
                saved.forEach((key, value) -> {
                    if (defaults.containsKey(key)) {
                        values.put(key, value);
                    }
                });
            }
        } catch (Exception error) {
            // An unreadable section falls back to its defaults rather than failing sign in.
            log.warn("Could not read the {} settings, using defaults: {}", section, error.getMessage());
        }
        return new Section(section, values, stored == null ? null : stored.getUpdatedAt(),
                stored == null ? null : stored.getUpdatedBy());
    }

    /** Saves the given keys of a section; keys left out keep their current value. */
    public Section save(String section, Map<String, Object> changes, String updatedBy) {
        Map<String, Object> defaults = defaults(section);
        Map<String, Object> values = new LinkedHashMap<>(get(section).values());
        for (Map.Entry<String, Object> change : changes.entrySet()) {
            String key = change.getKey();
            if (!defaults.containsKey(key)) {
                throw new IllegalArgumentException("Unknown setting: " + section + "." + key);
            }
            values.put(key, checked(key, defaults.get(key), change.getValue()));
        }
        PlatformSetting row = repository.findById(section).orElseGet(() -> new PlatformSetting(section));
        try {
            row.update(json.writeValueAsString(values), updatedBy);
        } catch (Exception error) {
            throw new IllegalArgumentException("These settings could not be stored");
        }
        repository.save(row);
        return get(section);
    }

    public boolean flag(String section, String key) {
        return Boolean.TRUE.equals(get(section).values().get(key));
    }

    public int number(String section, String key) {
        Object value = get(section).values().get(key);
        return value instanceof Number number ? number.intValue() : 0;
    }

    public String text(String section, String key) {
        Object value = get(section).values().get(key);
        return value == null ? "" : value.toString();
    }

    /** A comma separated setting as trimmed, lower-case entries. */
    public Set<String> list(String section, String key) {
        Set<String> entries = new java.util.LinkedHashSet<>();
        for (String part : text(section, key).split(",")) {
            String entry = part.trim().toLowerCase();
            if (!entry.isEmpty()) {
                entries.add(entry);
            }
        }
        return entries;
    }

    private static Object checked(String key, Object defaultValue, Object value) {
        if (defaultValue instanceof Boolean) {
            if (value instanceof Boolean) return value;
            throw new IllegalArgumentException(key + " must be true or false");
        }
        if (defaultValue instanceof Number) {
            int number;
            if (value instanceof Number given) number = given.intValue();
            else {
                try {
                    number = Integer.parseInt(String.valueOf(value).trim());
                } catch (NumberFormatException error) {
                    throw new IllegalArgumentException(key + " must be a whole number");
                }
            }
            int[] range = NUMBER_RANGES.getOrDefault(key, new int[] {Integer.MIN_VALUE, Integer.MAX_VALUE});
            if (number < range[0] || number > range[1]) {
                throw new IllegalArgumentException(key + " must be between " + range[0] + " and " + range[1]);
            }
            return number;
        }
        String text = value == null ? "" : String.valueOf(value).trim();
        if (text.length() > 500) {
            throw new IllegalArgumentException(key + " is too long");
        }
        return text;
    }

    private static Map<String, Object> defaults(String section) {
        Map<String, Object> defaults = DEFAULTS.get(section);
        if (defaults == null) {
            throw new IllegalArgumentException("Unknown settings section: " + section);
        }
        return defaults;
    }

    private static Map<String, Object> ordered(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return java.util.Collections.unmodifiableMap(map);
    }
}
