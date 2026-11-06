package com.smi.identity_service.staff;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a staff role can open in the admin portal: one permission per area. The
 * admin portal hides the areas a role lacks, and identity-service refuses its own
 * admin endpoints for them (see StaffAccessInterceptor).
 */
public final class StaffPermissions {

    public static final String SETTINGS = "settings";

    /** key to label, in sidebar order. */
    public static final Map<String, String> ALL = new LinkedHashMap<>();

    static {
        ALL.put("dashboard", "Dashboard");
        ALL.put("customers", "Customers");
        ALL.put("organisations", "Organisations");
        ALL.put("bank-integrations", "Bank integrations");
        ALL.put("bank-accounts", "Bank accounts");
        ALL.put("transactions", "Transactions");
        ALL.put("reconciliation", "Reconciliation");
        ALL.put("notifications", "Notifications");
        ALL.put("audit-logs", "Audit logs");
        ALL.put("reports", "Reports");
        ALL.put(SETTINGS, "Settings, staff and roles");
    }

    public static List<String> keys() {
        return List.copyOf(ALL.keySet());
    }

    private StaffPermissions() {
    }
}
