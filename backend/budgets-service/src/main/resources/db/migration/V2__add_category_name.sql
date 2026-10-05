-- The junction tables reference a categories-service that does not exist yet
-- (categories are still just free text on a transaction's narrative, set by
-- bank-integration-service). Until that service is real, a single-category
-- budget names the category directly instead of joining to a category id.
ALTER TABLE budgets ADD COLUMN category_name TEXT;
