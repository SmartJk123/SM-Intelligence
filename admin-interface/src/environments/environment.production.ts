/**
 * Settings for `npm run build` (the deployed admin portal).
 *
 * gatewayUrl is the public address of the api-gateway on Render, for example
 * 'https://api-gateway-xxxx.onrender.com' (no trailing slash). Every call,
 * including the bank screens, then goes through that one gateway, so the admin
 * portal reads the same data as the web and mobile apps. Left empty, the build
 * talks to services on localhost.
 */
export const environment = {
  gatewayUrl: '',
};
