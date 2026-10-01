-- Seed: 001_users.sql
-- Note: auth.users is managed by Supabase Auth.
-- In production, inserting into auth.users automatically triggers public.users creation.
-- This seed file provides placeholder inserts for public.users in local development/testing.

-- Example test user IDs (UUID format)
-- User A: 00000000-0000-0000-0000-000000000001
-- User B: 00000000-0000-0000-0000-000000000002

-- In an environment with auth.users already populated:
-- INSERT INTO public.users (id, name, email_address, phone_number)
-- VALUES
--     ('00000000-0000-0000-0000-000000000001', 'Demo User', 'demo@example.com', '+254700000001'),
--     ('00000000-0000-0000-0000-000000000002', 'Second User', 'user2@example.com', '+254700000002')
-- ON CONFLICT (id) DO NOTHING;
