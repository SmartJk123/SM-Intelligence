# Schema locations

Runtime SQL is maintained in each service's `src/main/resources/db/migration/` directory. See the [service schema map](../erd.md) for direct links.

The nine SQL files formerly in this directory were byte-for-byte copies of the service migrations and were not loaded by the application. They have been removed so there is one authoritative copy per service.

Add subsequent runtime changes as new versioned migrations in the owning service. Keep already-applied migrations unchanged to preserve Flyway checksums.
