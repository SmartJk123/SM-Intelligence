-- Migration: 001_create_users.sql
-- Description: Create public.users table synchronized with auth.users

-- 1. Create updated_at trigger helper function if it doesn't already exist
CREATE OR REPLACE FUNCTION public.set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 2. Create public.users table
CREATE TABLE IF NOT EXISTS public.users (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    email_address TEXT NOT NULL,
    phone_number TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT check_email_not_empty CHECK (char_length(trim(email_address)) > 0),
    CONSTRAINT check_name_not_empty CHECK (char_length(trim(name)) > 0)
);

-- Index for fast lookup by email
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email_address ON public.users (LOWER(email_address));

-- Attach updated_at trigger
DROP TRIGGER IF EXISTS trg_users_updated_at ON public.users;
CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON public.users
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();

-- 3. Trigger function to automatically mirror auth.users signups into public.users
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
DECLARE
    user_name TEXT;
BEGIN
    user_name := COALESCE(
        NEW.raw_user_meta_data->>'name',
        NEW.raw_user_meta_data->>'full_name',
        split_part(NEW.email, '@', 1)
    );

    INSERT INTO public.users (id, name, email_address, phone_number, created_at, updated_at)
    VALUES (
        NEW.id,
        user_name,
        NEW.email,
        NEW.phone,
        NOW(),
        NOW()
    )
    ON CONFLICT (id) DO UPDATE SET
        email_address = EXCLUDED.email_address,
        name = CASE
            WHEN EXCLUDED.name IS NOT NULL AND trim(EXCLUDED.name) <> '' THEN EXCLUDED.name
            ELSE public.users.name
        END,
        phone_number = COALESCE(EXCLUDED.phone_number, public.users.phone_number),
        updated_at = NOW();

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- Attach trigger to auth.users
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT OR UPDATE ON auth.users
    FOR EACH ROW
    EXECUTE FUNCTION public.handle_new_user();
