-- Account management from the admin interface.
--
-- status:        SUSPENDED users cannot sign in, and an existing session stops
--                working on its next request, because /api/auth/me refuses them.
-- role:          PLATFORM_ADMIN may use /api/v1/admin/** and /api/organizations/**.
--                Granted at start up to the addresses in ADMIN_EMAILS.
-- last_login_at: set on every successful sign in, shown on the admin Users page.

ALTER TABLE users ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED'));

ALTER TABLE users ADD COLUMN role TEXT NOT NULL DEFAULT 'USER'
    CONSTRAINT chk_users_role CHECK (role IN ('USER', 'PLATFORM_ADMIN'));

ALTER TABLE users ADD COLUMN last_login_at TIMESTAMPTZ;
