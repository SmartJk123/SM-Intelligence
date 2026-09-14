CREATE TABLE budgets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES users(id),
    
    name TEXT NOT NULL,
    currency CHAR(3) NOT NULL 
        CONSTRAINT chk_budget_currency CHECK (currency ~ '^[A-Z]{3}$'),
    
    -- Approved spending limit (0 allowed for "No allocation" state)
    allocated_amount NUMERIC(19,4) NOT NULL 
        CONSTRAINT chk_budget_amount_positive CHECK (allocated_amount >= 0),
    
    -- Temporal constraints
    start_date DATE NOT NULL,
    end_date DATE, 
    
    -- Ensures the time period is chronologically valid
    CONSTRAINT chk_budget_dates CHECK (end_date IS NULL OR end_date >= start_date),
    
    recurrence TEXT NOT NULL DEFAULT 'NONE' 
        CONSTRAINT chk_budget_recurrence CHECK (recurrence IN ('NONE', 'DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY')),
        
    -- Threshold for frontend alerts
    alert_threshold_percentage NUMERIC(5,2) DEFAULT 85.00 
        CONSTRAINT chk_budget_alert_pct CHECK (alert_threshold_percentage >= 0 AND alert_threshold_percentage <= 100),
        
    status TEXT NOT NULL DEFAULT 'ACTIVE' 
        CONSTRAINT chk_budget_status CHECK (status IN ('ACTIVE', 'EXHAUSTED', 'CLOSED')),
        
    -- JPA Optimistic Locking & Audit
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_budgets_owner ON budgets(owner_id);

CREATE TABLE budget_categories (
    budget_id UUID NOT NULL REFERENCES budgets(id) ON DELETE CASCADE,
    category TEXT NOT NULL,
    PRIMARY KEY (budget_id, category)
);

CREATE TABLE budget_accounts (
    budget_id UUID NOT NULL REFERENCES budgets(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    PRIMARY KEY (budget_id, account_id)
);

CREATE TABLE budget_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- This preserves financial history permanently
    budget_id UUID NOT NULL REFERENCES budgets(id),
    
    -- Track exactly what changed
    old_allocated_amount NUMERIC(19,4),
    new_allocated_amount NUMERIC(19,4),
    
    changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_budget_audit_budget_id ON budget_audit_log(budget_id);

-- This function automatically intercepts any UPDATE to the budgets table.
-- If the allocated_amount changes, it inserts a record into the audit log.
CREATE OR REPLACE FUNCTION log_budget_allocation_change()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.allocated_amount IS DISTINCT FROM NEW.allocated_amount THEN
        INSERT INTO budget_audit_log (
            budget_id, 
            old_allocated_amount, 
            new_allocated_amount
        ) VALUES (
            NEW.id, 
            OLD.allocated_amount, 
            NEW.allocated_amount
        );
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Attach the trigger to the budgets table
CREATE TRIGGER trigger_budget_allocation_audit
AFTER UPDATE ON budgets
FOR EACH ROW
EXECUTE FUNCTION log_budget_allocation_change();