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
