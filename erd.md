# Service schema map

Runtime schema definitions live in each service's `src/main/resources/db/migration/` directory. Flyway loads these from the service classpath. The redundant SQL copies formerly in `database_schema/` have been removed; the service migrations are the single source of truth.

## Ownership

| Service | Tables | Runtime migration |
| --- | --- | --- |
| Identity | users, organizations, organization_members | [SQL](backend/identity-service/src/main/resources/db/migration/V1__initial_schema.sql) |
| Accounts | accounts | [SQL](backend/accounts-service/src/main/resources/db/migration/V1__initial_schema.sql) |
| Transactions | transactions | [SQL](backend/transactions-service/src/main/resources/db/migration/V1__initial_schema.sql) |
| Categories | categories | [SQL](backend/categories-service/src/main/resources/db/migration/V1__initial_schema.sql) |
| Budgets | budgets, budget_categories, budget_accounts, budget_audit_log | [SQL](backend/budgets-service/src/main/resources/db/migration/V1__initial_schema.sql) |
| Investments | investments, investment_valuations | [SQL](backend/investments-service/src/main/resources/db/migration/V1__initial_schema.sql) |
| Forecasts | forecasts | [SQL](backend/forecasts-service/src/main/resources/db/migration/V1__initial_schema.sql) |
| Notifications | notifications, notification_preferences, notification_outbox | [SQL](backend/notifications-service/src/main/resources/db/migration/V1__initial_schema.sql) |
| Audit | audit_logs | [SQL](backend/audit-service/src/main/resources/db/migration/V1__initial_schema.sql) |

## Relationships

Cross-service identifiers are soft references, not database foreign keys. For example, `accounts.user_id` identifies an Identity user, `transactions.account_id` identifies an Accounts record, and `budget_categories.category_id` identifies a Categories record. Services must validate cross-service relationships in their application logic.

Foreign keys within a service remain enforced: organization membership references users and organizations; transactions and categories can reference their own tables; budget junction/audit tables reference budgets; valuations reference investments; notification outbox entries reference notifications. Consult the linked migration for exact constraints and delete rules.

The budget allocation trigger records allocation changes. The schema does not enforce a fixed bank list; the frontend currently presents KCB, Equity, NCBA, and Stanbic.

## Migration policy

Local Compose provisions one database per service. Each service maintains its own Flyway history. Add subsequent schema changes as new versioned migrations in the owning service, retaining already-applied migrations and their checksums.
