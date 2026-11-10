-- Annual interest rate in percent (e.g. 12.5 for 12.5% a year), used for the
-- customer's maturity forecast. Null when the customer has not entered one.
ALTER TABLE investments ADD COLUMN annual_interest_rate NUMERIC(7,4)
    CONSTRAINT chk_inv_interest_rate CHECK (annual_interest_rate IS NULL OR (annual_interest_rate >= 0 AND annual_interest_rate <= 100));
