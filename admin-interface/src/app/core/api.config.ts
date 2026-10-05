/**
 * Base URL of the Spring Boot API.
 *
 * Development default is the local Spring Boot port. Change this to the
 * deployed API host when you release, for example
 * `https://api.smartmoney.io/api/v1`.
 *
 *   POST {API_BASE_URL}/admin/bank-integrations/{bankId}/test
 *   GET  {API_BASE_URL}/admin/bank-integrations
 *   PUT  {API_BASE_URL}/admin/bank-integrations/{bankId}
 *   GET  {API_BASE_URL}/admin/stats
 */
export const API_BASE_URL = 'http://localhost:8090/api/v1';

/**
 * Base URL of identity-service, reached through api-gateway (port 8080), not
 * the bank-integration service above. Separate from API_BASE_URL because they
 * are two different Spring Boot services on two different ports.
 *
 *   GET {IDENTITY_API_BASE_URL}/admin/users
 */
export const IDENTITY_API_BASE_URL = 'http://localhost:8080/api/v1';

/**
 * identity-service's auth endpoints sit directly under /api, not /api/v1 like
 * its admin routes above, so login needs its own base URL.
 *
 *   POST {IDENTITY_AUTH_BASE_URL}/login
 */
export const IDENTITY_AUTH_BASE_URL = 'http://localhost:8080/api/auth';

/**
 * identity-service's organisations API, also under /api rather than /api/v1.
 *
 *   GET  {IDENTITY_ORGANIZATIONS_URL}
 *   POST {IDENTITY_ORGANIZATIONS_URL}
 *   GET  {IDENTITY_ORGANIZATIONS_URL}/{id}/members
 *   POST {IDENTITY_ORGANIZATIONS_URL}/{id}/members
 */
export const IDENTITY_ORGANIZATIONS_URL = 'http://localhost:8080/api/organizations';
