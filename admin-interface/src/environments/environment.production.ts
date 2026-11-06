/**
 * Settings for `npm run build` (the deployed admin portal).
 *
 * gatewayUrl is the public address of the api-gateway (no trailing slash): the
 * Kubernetes VM's ingress. Every call, including the bank screens, then goes
 * through that one gateway, so the admin portal reads the same data as the web
 * and mobile apps. Left empty, the build talks to services on localhost.
 *
 * The VM serves plain HTTP only, so a portal hosted on an https:// address
 * cannot call it (browsers block mixed content). Switch this to the gateway's
 * https:// address once the VM has a domain and a trusted certificate.
 */
export const environment = {
  gatewayUrl: 'http://145.241.250.59',
};
