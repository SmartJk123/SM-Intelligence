-- Invoice-backed pending activity has no bank account until it is reconciled.
CREATE TABLE invoices (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    vendor VARCHAR(200) NOT NULL,
    invoice_number VARCHAR(100) NOT NULL,
    amount NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    invoice_date DATE NOT NULL,
    due_date DATE,
    filename VARCHAR(200) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    document BYTEA NOT NULL,
    document_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT invoices_owner_document UNIQUE (owner_id, document_hash)
);
CREATE INDEX invoices_owner_created ON invoices(owner_id, created_at DESC);
