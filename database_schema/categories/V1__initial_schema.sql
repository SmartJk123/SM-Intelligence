CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================================================
-- CATEGORIES
-- ============================================================
CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Soft reference to Identity service (no FK constraint)
    user_id UUID NOT NULL,

    name TEXT NOT NULL,
    category_type TEXT NOT NULL
        CONSTRAINT chk_category_type CHECK (category_type IN ('INCOME', 'EXPENSE', 'TRANSFER')),

    -- Self-referencing FK (same service) for nested sub-categories
    parent_id UUID REFERENCES categories(id),

    -- UI metadata for the frontend to render custom badges
    icon_name TEXT,
    color_hex CHAR(7)
        CONSTRAINT chk_category_color CHECK (color_hex ~ '^#[0-9A-Fa-f]{6}$'),

    -- JPA Optimistic Locking
    version INTEGER NOT NULL DEFAULT 0,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Prevent duplicate category names at the same hierarchy level for a user
    CONSTRAINT uq_user_category_name UNIQUE NULLS NOT DISTINCT (user_id, parent_id, name)
);

CREATE INDEX idx_categories_user ON categories(user_id);
CREATE INDEX idx_categories_parent ON categories(parent_id);
