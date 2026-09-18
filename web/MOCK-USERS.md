# Local development data

Run `npm run start:mock` from `web/` to use local authentication and in-memory financial APIs. The launcher starts with no seeded users or records. Register a fictional user, complete setup, and add records through the workspace. Restarting the process clears its users, sessions, and financial data.

The test utilities can explicitly call `startMockServer(port, webPort, { seed: true })` to load deterministic fictional scenarios. Those fixtures remain available to tests; they are not login accounts created by `npm run start:mock`. See `tools/mock-api.mjs` and `tools/realistic-records.mjs` for the fixtures.

For hosted authentication, use `npm start`; see [SAMPLE-AUTH.md](SAMPLE-AUTH.md). Both modes use local in-memory financial data and do not connect to banks or execute payments.
