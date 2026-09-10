CREATE TABLE forecasts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    
    -- The future date being predicted
    target_date DATE NOT NULL,
    
    -- Projection variants for timeline charting
    scenario TEXT NOT NULL DEFAULT 'BASE'
        CONSTRAINT chk_forecast_scenario CHECK (scenario IN ('BASE', 'OPTIMISTIC', 'PESSIMISTIC', 'STRESS_TEST')),
        
    -- Core cash flow predictions
    predicted_inflow NUMERIC(19,4) NOT NULL DEFAULT 0.0000,
    predicted_outflow NUMERIC(19,4) NOT NULL DEFAULT 0.0000,
    projected_balance NUMERIC(19,4) NOT NULL,
    
    -- Algorithmic confidence scoring (0.00% to 100.00%)
    confidence_score NUMERIC(5,2) 
        CONSTRAINT chk_forecast_confidence CHECK (confidence_score >= 0 AND confidence_score <= 100),
        
    -- Flexible metadata for ML model inputs (e.g., seasonality factors, variable weights)
    model_metadata JSONB DEFAULT '{}'::jsonb,
    
    -- When this prediction was computed (crucial for model backtesting)
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    -- Prevent duplicate scenarios for the same user on the same future day
    CONSTRAINT uq_user_scenario_date UNIQUE (user_id, scenario, target_date)
);

-- Optimized index for rendering future projection charts on the frontend
CREATE INDEX idx_forecasts_user_target ON forecasts(user_id, target_date);