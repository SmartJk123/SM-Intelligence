-- Migration: 004_create_transactions.sql
-- Description: Create public.transactions table using NUMERIC(19,4) for financial precision

CREATE TABLE IF NOT EXISTS public.transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES public.accounts(id) ON DELETE CASCADE,
    amount NUMERIC(19,4) NOT NULL,
    transaction_type TEXT NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    description TEXT,
    provider_transaction_id TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_transaction_type CHECK (transaction_type IN ('CREDIT', 'DEBIT', 'TRANSFER', 'FEE', 'PAYMENT', 'ADJUSTMENT')),
    CONSTRAINT uq_account_provider_tx UNIQUE (account_id, provider_transaction_id)
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_transactions_account_id ON public.transactions(account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_timestamp ON public.transactions(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_account_timestamp ON public.transactions(account_id, timestamp DESC);

-- Attach updated_at trigger
DROP TRIGGER IF EXISTS trg_transactions_updated_at ON public.transactions;
CREATE TRIGGER trg_transactions_updated_at
    BEFORE UPDATE ON public.transactions
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();
