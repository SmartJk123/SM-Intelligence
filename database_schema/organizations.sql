CREATE TABLE organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    name TEXT NOT NULL,
    slug TEXT NOT NULL CONSTRAINT uq_orgs_slug UNIQUE,
    
    status TEXT NOT NULL DEFAULT 'ACTIVE' 
        CONSTRAINT chk_orgs_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
        
    -- JPA Optimistic Locking
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE organization_members (
    -- Strict financial compliance: Soft Deletes only (No CASCADE)
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