# Normal workflow with hosted authentication
In VS Code, stop the previous server with Ctrl+C and run npm start from web.
npm run start:sample is an alias for the same workflow.
Register → automatic sign-in → dashboard. Returning users: sign in → dashboard. Add accounts from the workspace when ready.
Existing registered users can sign in. Backend identities are not deleted.

Authentication is checked by the hosted Render backend over HTTPS.
A loopback-only development adapter supplies per-user financial APIs with empty
in-memory collections. No seeded users or financial records are loaded.
Refresh and logout preserve financial records while the server runs. Restarting
the server clears all local financial records and sessions and returns users to an empty dashboard after signing in.
Registration metadata (name and account type) is also temporary; returning users
after a restart default to individual until edited in settings.
The adapter is development-only and does not claim to persist data on Render.
Old prototype browser data and tokens are cleared once when the new app loads.
The existing backend has no deletion endpoint; its login accounts remain available.

Checks: npm test -- --watch=false; npm run build; npm run test:workflow.
