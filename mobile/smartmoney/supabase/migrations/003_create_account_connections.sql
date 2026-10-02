-- Migration: 003_create_account_connections.sql
-- Description: Create public.account_connections table to track sync state with external institutions/providers

CREATE TABLE IF NOT EXISTS public.account_connections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES public.accounts(id) ON DELETE CASCADE,
    provider TEXT NOT NULL,
    connection_status TEXT NOT NULL DEFAULT 'connected',
    external_identifier TEXT,
    last_sync_at TIMESTAMPTZ,
    error_message TEXT,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_account_provider UNIQUE (account_id, provider)
);

-- Indexes for connection lookup
CREATE INDEX IF NOT EXISTS idx_account_connections_account_id ON public.account_connections(account_id);
CREATE INDEX IF NOT EXISTS idx_account_connections_provider ON public.account_connections(provider);

-- Attach updated_at trigger
DROP TRIGGER IF EXISTS trg_account_connections_updated_at ON public.account_connections;
CREATE TRIGGER trg_account_connections_updated_at
    BEFORE UPDATE ON public.account_connections
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();
