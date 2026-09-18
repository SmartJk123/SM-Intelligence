CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================================================
-- USERS
-- ============================================================
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    name TEXT NOT NULL
        CHECK (char_length(trim(name)) BETWEEN 2 AND 100),

    email_address TEXT NOT NULL
        CHECK (char_length(email_address) <= 254),

    password_hash TEXT NOT NULL
        CHECK (char_length(password_hash) BETWEEN 20 AND 500),

    phone_number TEXT
        CHECK (
            phone_number IS NULL
            OR char_length(phone_number) BETWEEN 7 AND 20
        ),

    account_type TEXT NOT NULL DEFAULT 'INDIVIDUAL'
        CHECK (account_type IN ('INDIVIDUAL', 'ORGANIZATION')),

    -- Organization-specific fields (nullable for individuals)
    organization_name TEXT
        CHECK (organization_name IS NULL OR char_length(trim(organization_name)) BETWEEN 2 AND 150),
    business_type TEXT,
    industry TEXT,

    -- Preferences & Security
    reporting_currency TEXT NOT NULL DEFAULT 'KES'
        CHECK (char_length(reporting_currency) = 3),
    timezone TEXT NOT NULL DEFAULT 'Africa/Nairobi',
    locale TEXT NOT NULL DEFAULT 'en-KE'
        CHECK (char_length(locale) BETWEEN 2 AND 10),

    is_email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,

    -- Compliance
    terms_accepted_version TEXT NOT NULL
        CHECK (char_length(terms_accepted_version) > 0),
    terms_accepted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- JPA Optimistic Locking
    version INTEGER NOT NULL DEFAULT 0,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Soft-delete: other services use RESTRICT-equivalent app logic
    deleted_at TIMESTAMPTZ
);

-- Email must be unique among active (non-deleted) users
CREATE UNIQUE INDEX users_email_active_unique
    ON users(email_address) WHERE deleted_at IS NULL;


-- ============================================================
-- ORGANIZATIONS
-- ============================================================
CREATE TABLE organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    name TEXT NOT NULL,
    slug TEXT NOT NULL CONSTRAINT uq_orgs_slug UNIQUE,

    status TEXT NOT NULL DEFAULT 'ACTIVE'
        CONSTRAINT chk_orgs_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),

    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);


-- ============================================================
-- ORGANIZATION MEMBERS
-- ============================================================
CREATE TABLE organization_members (
    organization_id UUID NOT NULL REFERENCES organizations(id),
    user_id UUID NOT NULL REFERENCES users(id),

    -- Role-Based Access Control
    role TEXT NOT NULL
        CONSTRAINT chk_org_members_role CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER', 'VIEWER')),

    -- Soft-delete mechanism instead of destroying historical records
    member_status TEXT NOT NULL DEFAULT 'ACTIVE'
        CONSTRAINT chk_org_members_status CHECK (member_status IN ('ACTIVE', 'INACTIVE')),

    invited_by UUID REFERENCES users(id),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- A user can only have one role per organization
    PRIMARY KEY (organization_id, user_id)
);

CREATE INDEX idx_org_members_user ON organization_members(user_id);
