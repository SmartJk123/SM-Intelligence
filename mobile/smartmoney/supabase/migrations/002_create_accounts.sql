-- Migration: 002_create_accounts.sql
-- Description: Create public.accounts table for managing financial and energy accounts

CREATE TABLE IF NOT EXISTS public.accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    account_id TEXT NOT NULL,
    account_name TEXT NOT NULL,
    institution TEXT NOT NULL,
    account_type TEXT NOT NULL,
    masked_identifier TEXT,
    currency CHAR(3) NOT NULL DEFAULT 'KES',
    ledger_balance NUMERIC(19,4) NOT NULL DEFAULT 0,
    available_balance NUMERIC(19,4) NOT NULL DEFAULT 0,
    credit_outstanding NUMERIC(19,4) NOT NULL DEFAULT 0,
    credit_limit NUMERIC(19,4),
    available_credit NUMERIC(19,4),
    account_status TEXT NOT NULL DEFAULT 'active',
    connection_status TEXT NOT NULL DEFAULT 'pending',
    last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    data_source TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_institution_account UNIQUE (user_id, institution, account_id),
    CONSTRAINT chk_account_currency CHECK (char_length(currency) = 3)
);

-- Indexes for efficient queries
CREATE INDEX IF NOT EXISTS idx_accounts_user_id ON public.accounts(user_id);
CREATE INDEX IF NOT EXISTS idx_accounts_status ON public.accounts(account_status);
CREATE INDEX IF NOT EXISTS idx_accounts_institution ON public.accounts(institution);

-- Attach updated_at trigger
DROP TRIGGER IF EXISTS trg_accounts_updated_at ON public.accounts;
CREATE TRIGGER trg_accounts_updated_at
    BEFORE UPDATE ON public.accounts
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();
