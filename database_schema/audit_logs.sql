CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- The entity being modified
    table_name TEXT NOT NULL,
    record_id UUID NOT NULL,
    
    -- The mutation type
    action TEXT NOT NULL 
        CONSTRAINT chk_audit_action CHECK (action IN ('INSERT', 'UPDATE', 'DELETE')),
        
    -- JSONB snapshots for exact point-in-time state comparison
    old_data JSONB,
    new_data JSONB,
    
    -- The user or system service that triggered the mutation
    changed_by UUID REFERENCES users(id) ON DELETE SET NULL, 
    
    changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Optimized for investigating the history of a specific row in any table
CREATE INDEX idx_audit_logs_target ON audit_logs(table_name, record_id);

-- Optimized for chronological security reviews
CREATE INDEX idx_audit_logs_date ON audit_logs(changed_at DESC);