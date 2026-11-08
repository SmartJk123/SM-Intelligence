-- Admin activity: audit trail, in-app notifications for administrators,
-- a record of every email sent (the outbox) and editable email templates.

CREATE TABLE audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actor_id UUID,
    actor_email TEXT,
    -- CUSTOMER, STAFF or SYSTEM
    actor_role TEXT NOT NULL,
    action TEXT NOT NULL,
    target_type TEXT,
    target_id TEXT,
    target_label TEXT,
    detail TEXT,
    ip_address TEXT,
    -- SUCCESS or FAILURE
    outcome TEXT NOT NULL
);

CREATE INDEX idx_audit_events_occurred ON audit_events(occurred_at DESC);

CREATE TABLE admin_notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    category TEXT NOT NULL,
    -- INFO, WARNING or CRITICAL
    severity TEXT NOT NULL,
    title TEXT NOT NULL,
    message TEXT NOT NULL,
    read_at TIMESTAMPTZ
);

CREATE INDEX idx_admin_notifications_created ON admin_notifications(created_at DESC);

CREATE TABLE email_outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    recipient TEXT NOT NULL,
    subject TEXT NOT NULL,
    -- Links and temporary passwords are replaced with [hidden] before storing.
    body TEXT NOT NULL,
    template_key TEXT,
    -- SENT, FAILED or NOT_CONFIGURED (no SMTP settings)
    status TEXT NOT NULL,
    error TEXT
);

CREATE INDEX idx_email_outbox_created ON email_outbox(created_at DESC);

-- Only templates an administrator has edited are stored; the rest use the built-in text.
CREATE TABLE email_templates (
    template_key TEXT PRIMARY KEY,
    subject TEXT NOT NULL,
    body TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by TEXT
);
