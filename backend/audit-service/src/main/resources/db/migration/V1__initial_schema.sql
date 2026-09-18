CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================================================
-- AUDIT LOGS (centralized, receives events from all services)
-- ============================================================
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- The source service that emitted the event
    source_service TEXT NOT NULL,

    -- The entity being modified
    table_name TEXT NOT NULL,
    record_id UUID NOT NULL,

    -- The mutation type
    action TEXT NOT NULL
        CONSTRAINT chk_audit_action CHECK (action IN ('INSERT', 'UPDATE', 'DELETE')),

    -- JSONB snapshots for exact point-in-time state comparison
    old_data JSONB,
    new_data JSONB,

    -- Soft reference to Identity service (the user or system that triggered the mutation)
    changed_by UUID,

    changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Optimized for investigating the history of a specific row in any table
CREATE INDEX idx_audit_logs_target ON audit_logs(table_name, record_id);

-- Optimized for chronological security reviews
CREATE INDEX idx_audit_logs_date ON audit_logs(changed_at DESC);

-- Optimized for filtering events by originating service
CREATE INDEX idx_audit_logs_service ON audit_logs(source_service);
