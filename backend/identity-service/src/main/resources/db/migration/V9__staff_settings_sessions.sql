-- Staff roles for administrators, saved admin portal settings, and the device
-- behind each signed-in session.

CREATE TABLE staff_roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL UNIQUE,
    description TEXT,
    -- Comma separated permission keys, see StaffPermissions.
    permissions TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- The role of a PLATFORM_ADMIN. Null means full access (ADMIN_EMAILS administrators).
ALTER TABLE users ADD COLUMN staff_role_id UUID REFERENCES staff_roles(id) ON DELETE SET NULL;

-- One row per settings section (general, security, notifications, system), as JSON.
CREATE TABLE platform_settings (
    setting_key TEXT PRIMARY KEY,
    setting_value TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by TEXT
);

-- A refresh token is one signed-in session; these say which device it is.
ALTER TABLE refresh_tokens ADD COLUMN user_agent TEXT;
ALTER TABLE refresh_tokens ADD COLUMN ip_address TEXT;
