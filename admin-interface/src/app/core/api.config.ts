import { environment } from '../../environments/environment';

/**
 * Where the admin portal finds the backend.
 *
 * Deployed, everything goes through one api-gateway (environment.gatewayUrl,
 * set in src/environments/environment.production.ts), so the admin portal, the
 * web app and the mobile app all read the same data. Locally the gatewayUrl is
 * empty: identity calls go to the gateway on 8080 and bank calls straight to
 * bank-integration-service on 8090.
 */
const gateway = environment.gatewayUrl.replace(/\/+$/, '');
const localGateway = 'http://localhost:8080';

/**
 * bank-integration-service's admin API.
 *
 *   POST {API_BASE_URL}/admin/bank-integrations/{bankId}/test
 *   GET  {API_BASE_URL}/admin/bank-integrations
 *   PUT  {API_BASE_URL}/admin/bank-integrations/{bankId}
 *   GET  {API_BASE_URL}/admin/stats
 */
export const API_BASE_URL = gateway ? `${gateway}/api/v1` : 'http://localhost:8090/api/v1';

/**
 * identity-service's admin API, reached through api-gateway.
 *
 *   GET {IDENTITY_API_BASE_URL}/admin/users
 */
export const IDENTITY_API_BASE_URL = `${gateway || localGateway}/api/v1`;

/**
 * identity-service's auth endpoints sit directly under /api, not /api/v1.
 *
 *   POST {IDENTITY_AUTH_BASE_URL}/login
 */
export const IDENTITY_AUTH_BASE_URL = `${gateway || localGateway}/api/auth`;

/**
 * identity-service's organisations API, also under /api rather than /api/v1.
 *
 *   GET  {IDENTITY_ORGANIZATIONS_URL}
 *   POST {IDENTITY_ORGANIZATIONS_URL}
 *   GET  {IDENTITY_ORGANIZATIONS_URL}/{id}/members
 *   POST {IDENTITY_ORGANIZATIONS_URL}/{id}/members
 */
export const IDENTITY_ORGANIZATIONS_URL = `${gateway || localGateway}/api/organizations`;
