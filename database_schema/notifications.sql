CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    
    -- Categorization
    event_type TEXT NOT NULL,
    severity TEXT NOT NULL 
        CONSTRAINT chk_notif_severity CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL', 'SUCCESS')),
        
    -- Content
    title TEXT NOT NULL,
    message TEXT NOT NULL,
    
    -- Polymorphic linking (e.g., links to a specific transaction or budget ID)
    reference_id UUID,
    reference_type TEXT, 
    
    -- Actionable State
    read_at TIMESTAMPTZ,       -- When the user saw it
    resolved_at TIMESTAMPTZ,   -- When the user took action (if required)
    
    -- Flexible metadata payload (e.g., custom icon URLs, dynamic frontend routing paths)
    metadata JSONB DEFAULT '{}'::jsonb,
    
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);


CREATE TABLE notification_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    
    -- Specific event (e.g., 'BUDGET_EXCEEDED', 'SECURITY_ALERT') or 'ALL' for defaults
    event_type TEXT NOT NULL,
    
    in_app_enabled BOOLEAN NOT NULL DEFAULT true,
    email_enabled BOOLEAN NOT NULL DEFAULT true,
    push_enabled BOOLEAN NOT NULL DEFAULT false,
    
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    -- A user can only have one set of rules per event type
    CONSTRAINT uq_user_event_pref UNIQUE (user_id, event_type)
);

CREATE INDEX idx_notif_prefs_user ON notification_preferences(user_id);


-- Optimized for fetching a user's unread inbox quickly
CREATE INDEX idx_notifications_inbox ON notifications(user_id, read_at) WHERE read_at IS NULL;


CREATE TABLE notification_outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Links back to the core notification
    notification_id UUID NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
    
    -- Delivery medium (e.g., 'EMAIL', 'PUSH_FCM', 'SMS')
    delivery_channel TEXT NOT NULL,
    
    -- The actual payload to be sent to the external provider
    payload JSONB NOT NULL,
    
    status TEXT NOT NULL DEFAULT 'PENDING' 
        CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'PROCESSING', 'DELIVERED', 'FAILED')),
        
    -- Tracking for retries and dead-letter queues
    retry_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ
);

-- Optimized for background workers looking for pending jobs
CREATE INDEX idx_outbox_pending ON notification_outbox(status, created_at) WHERE status = 'PENDING';