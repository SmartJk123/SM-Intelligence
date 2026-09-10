CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES accounts(id),
    
    -- Link to original transaction (for refunds/reversals)
    related_transaction_id UUID REFERENCES transactions(id),
    
    amount NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    transaction_type TEXT NOT NULL CHECK (transaction_type IN ('CREDIT', 'DEBIT')),
    
    -- Granular tracking
    counterparty TEXT,
    payment_method TEXT,
    category TEXT NOT NULL DEFAULT 'UNCATEGORIZED',
    
    -- Status updated to match the frontend spec requirements
    status TEXT NOT NULL DEFAULT 'PENDING' 
        CHECK (status IN ('PENDING', 'POSTED', 'FAILED', 'REVERSED', 'CANCELLED')),
        
    provider_reference TEXT UNIQUE,
    description TEXT,
    
    -- The temporal split
    transaction_date TIMESTAMPTZ NOT NULL,
    posting_date TIMESTAMPTZ,
    
    -- Infrastructure
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes for lightning-fast frontend dashboard queries
CREATE INDEX idx_transactions_account_date ON transactions(account_id, transaction_date DESC);
CREATE INDEX idx_transactions_status ON transactions(status);