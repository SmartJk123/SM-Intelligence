CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    user_id UUID NOT NULL,
    
    -- Renamed to clarify this is the bank's external ID, not the internal PK
    provider_account_id TEXT NOT NULL,
    
    account_name TEXT NOT NULL 
        CHECK (char_length(trim(account_name)) BETWEEN 1 AND 150),
        
    institution TEXT NOT NULL 
        CHECK (institution IN ('KCB', 'NCBA', 'STANBIC', 'EQUITY')),
        
    account_type TEXT NOT NULL 
        CHECK (account_type IN ('DEPOSIT', 'CREDIT')),
        
    masked_identifier TEXT NOT NULL,
    
    currency CHAR(3) NOT NULL,
    
    ledger_balance NUMERIC(19,4) NOT NULL DEFAULT 0,
    available_balance NUMERIC(19,4) NOT NULL DEFAULT 0,
    credit_outstanding NUMERIC(19,4) NOT NULL DEFAULT 0,
    credit_limit NUMERIC(19,4) NOT NULL DEFAULT 0,
    
    account_status TEXT NOT NULL DEFAULT 'ACTIVE' 
        CHECK (account_status IN ('ACTIVE', 'CLOSED', 'RESTRICTED')),
        
    connection_status TEXT NOT NULL DEFAULT 'CONNECTED' 
        CHECK (connection_status IN ('CONNECTED', 'SYNCING', 'DISCONNECTED', 'ACTION_REQUIRED')),
        
    last_updated TIMESTAMPTZ,
    
    data_source TEXT NOT NULL 
        CHECK (data_source IN ('BANK_API', 'MANUAL')),
        
    -- JPA Optimistic Locking
    version INTEGER NOT NULL DEFAULT 0,
    
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT accounts_user_fk FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT accounts_currency_check CHECK (currency ~ '^[A-Z]{3}$'),
    
    -- Removed ledger_balance >= 0 check, as deposit accounts can go into overdraft
    CONSTRAINT accounts_credit_outstanding_check CHECK (credit_outstanding >= 0),
    CONSTRAINT accounts_credit_limit_check CHECK (credit_limit >= 0),
    
    -- Prevent the same bank account from being added multiple times
    CONSTRAINT unique_provider_account UNIQUE (institution, provider_account_id)
);