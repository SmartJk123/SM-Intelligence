/**
 * Settings for `npm start` (local development, as started by start-local.ps1).
 * An empty gatewayUrl means the services on this machine: the gateway on 8080
 * and the bank service on 8090. Set it to 'http://145.241.250.59' to use the
 * backend on the Kubernetes VM instead.
 */
export const environment = {
  gatewayUrl: '',
};
