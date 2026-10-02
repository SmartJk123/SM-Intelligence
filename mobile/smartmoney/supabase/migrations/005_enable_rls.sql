-- Migration: 005_enable_rls.sql
-- Description: Enable Row Level Security (RLS) and define strict tenant isolation policies

-- 1. Enable RLS on all application tables
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.account_connections ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.transactions ENABLE ROW LEVEL SECURITY;

-- 2. RLS Policies for public.users
DROP POLICY IF EXISTS "Users can view own profile" ON public.users;
CREATE POLICY "Users can view own profile"
    ON public.users
    FOR SELECT
    USING (auth.uid() = id);

DROP POLICY IF EXISTS "Users can insert own profile" ON public.users;
CREATE POLICY "Users can insert own profile"
    ON public.users
    FOR INSERT
    WITH CHECK (auth.uid() = id);

DROP POLICY IF EXISTS "Users can update own profile" ON public.users;
CREATE POLICY "Users can update own profile"
    ON public.users
    FOR UPDATE
    USING (auth.uid() = id)
    WITH CHECK (auth.uid() = id);

-- 3. RLS Policies for public.accounts
DROP POLICY IF EXISTS "Users can view own accounts" ON public.accounts;
CREATE POLICY "Users can view own accounts"
    ON public.accounts
    FOR SELECT
    USING (user_id = auth.uid());

DROP POLICY IF EXISTS "Users can insert own accounts" ON public.accounts;
CREATE POLICY "Users can insert own accounts"
    ON public.accounts
    FOR INSERT
    WITH CHECK (user_id = auth.uid());

DROP POLICY IF EXISTS "Users can update own accounts" ON public.accounts;
CREATE POLICY "Users can update own accounts"
    ON public.accounts
    FOR UPDATE
    USING (user_id = auth.uid())
    WITH CHECK (user_id = auth.uid());

DROP POLICY IF EXISTS "Users can delete own accounts" ON public.accounts;
CREATE POLICY "Users can delete own accounts"
    ON public.accounts
    FOR DELETE
    USING (user_id = auth.uid());

-- 4. RLS Policies for public.account_connections
DROP POLICY IF EXISTS "Users can view own account connections" ON public.account_connections;
CREATE POLICY "Users can view own account connections"
    ON public.account_connections
    FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM public.accounts
            WHERE accounts.id = account_connections.account_id
            AND accounts.user_id = auth.uid()
        )
    );

DROP POLICY IF EXISTS "Users can insert own account connections" ON public.account_connections;
CREATE POLICY "Users can insert own account connections"
    ON public.account_connections
    FOR INSERT
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.accounts
            WHERE accounts.id = account_connections.account_id
            AND accounts.user_id = auth.uid()
        )
    );

DROP POLICY IF EXISTS "Users can update own account connections" ON public.account_connections;
CREATE POLICY "Users can update own account connections"
    ON public.account_connections
    FOR UPDATE
    USING (
        EXISTS (
            SELECT 1 FROM public.accounts
            WHERE accounts.id = account_connections.account_id
            AND accounts.user_id = auth.uid()
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.accounts
            WHERE accounts.id = account_connections.account_id
            AND accounts.user_id = auth.uid()
        )
    );

DROP POLICY IF EXISTS "Users can delete own account connections" ON public.account_connections;
CREATE POLICY "Users can delete own account connections"
    ON public.account_connections
    FOR DELETE
    USING (
        EXISTS (
            SELECT 1 FROM public.accounts
            WHERE accounts.id = account_connections.account_id
            AND accounts.user_id = auth.uid()
        )
    );

-- 5. RLS Policies for public.transactions
DROP POLICY IF EXISTS "Users can view own transactions" ON public.transactions;
CREATE POLICY "Users can view own transactions"
    ON public.transactions
    FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM public.accounts
            WHERE accounts.id = transactions.account_id
            AND accounts.user_id = auth.uid()
        )
    );

DROP POLICY IF EXISTS "Users can insert own transactions" ON public.transactions;
CREATE POLICY "Users can insert own transactions"
    ON public.transactions
    FOR INSERT
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.accounts
            WHERE accounts.id = transactions.account_id
            AND accounts.user_id = auth.uid()
        )
    );

DROP POLICY IF EXISTS "Users can update own transactions" ON public.transactions;
CREATE POLICY "Users can update own transactions"
    ON public.transactions
    FOR UPDATE
    USING (
        EXISTS (
            SELECT 1 FROM public.accounts
            WHERE accounts.id = transactions.account_id
            AND accounts.user_id = auth.uid()
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.accounts
            WHERE accounts.id = transactions.account_id
            AND accounts.user_id = auth.uid()
        )
    );

DROP POLICY IF EXISTS "Users can delete own transactions" ON public.transactions;
CREATE POLICY "Users can delete own transactions"
    ON public.transactions
    FOR DELETE
    USING (
        EXISTS (
            SELECT 1 FROM public.accounts
            WHERE accounts.id = transactions.account_id
            AND accounts.user_id = auth.uid()
        )
    );
