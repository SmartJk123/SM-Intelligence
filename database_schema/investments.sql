CREATE TABLE investments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES users(id),
    
    name TEXT NOT NULL,
    institution TEXT NOT NULL, 
    currency CHAR(3) NOT NULL 
        CONSTRAINT chk_inv_currency CHECK (currency ~ '^[A-Z]{3}$'),
        
    product_type TEXT NOT NULL 
        CONSTRAINT chk_inv_product_type CHECK (product_type IN ('FIXED_DEPOSIT', 'TREASURY', 'FUND', 'EQUITY', 'OTHER')),
        
    -- Base Capital Movements
    contributions NUMERIC(19,4) NOT NULL DEFAULT 0.0000,
    principal_withdrawn NUMERIC(19,4) NOT NULL DEFAULT 0.0000,
    
    -- Temporal Dates
    start_date DATE NOT NULL,
    maturity_date DATE,
    CONSTRAINT chk_inv_dates CHECK (maturity_date IS NULL OR maturity_date >= start_date),
        
    status TEXT NOT NULL DEFAULT 'ACTIVE' 
        CONSTRAINT chk_inv_status CHECK (status IN ('ACTIVE', 'MATURED', 'REDEEMED', 'CLOSED')),
        
    -- JSONB for polymorphic product traits (e.g., {"units_held": 150.55, "yield": 12.5})
    product_metadata JSONB DEFAULT '{}'::jsonb,
        
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_investments_owner ON investments(owner_id);
CREATE INDEX idx_investments_metadata ON investments USING GIN (product_metadata);


CREATE TABLE investment_valuations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    investment_id UUID NOT NULL REFERENCES investments(id),
    
    -- The snapshot in time
    valuation_date DATE NOT NULL,
    current_value NUMERIC(19,4) NOT NULL,
    accrued_income NUMERIC(19,4) NOT NULL DEFAULT 0.0000,
    realized_return NUMERIC(19,4) NOT NULL DEFAULT 0.0000,
    unrealized_return NUMERIC(19,4) NOT NULL DEFAULT 0.0000,
    fees_and_taxes NUMERIC(19,4) NOT NULL DEFAULT 0.0000,
    
    valuation_source TEXT NOT NULL 
        CONSTRAINT chk_inv_val_source CHECK (valuation_source IN ('PROVIDER', 'MARKET_FEED', 'CALCULATION', 'MANUAL')),
        
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    -- Prevent multiple valuations for the same exact day
    UNIQUE (investment_id, valuation_date)
);

CREATE INDEX idx_investment_valuations_date ON investment_valuations(investment_id, valuation_date DESC);