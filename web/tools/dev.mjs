import { spawn } from 'node:child_process';
import { createServer } from 'node:net';
import { startAuthServer } from './auth-server.mjs';
const port = Number(process.env.WEB_PORT || 4200);
const apiPort = Number(process.env.AUTH_ADAPTER_PORT || 4301);
const webHost = process.env.WEB_HOST || 'localhost';
const https = process.env.WEB_HTTPS === 'true';
const tlsArgs = https ? ['--ssl'] : [];
if (https) {
  if (!!process.env.WEB_SSL_CERT !== !!process.env.WEB_SSL_KEY) {
    throw new Error('Set both WEB_SSL_CERT and WEB_SSL_KEY for your trusted development certificate.');
  }
  if (process.env.WEB_SSL_CERT) tlsArgs.push('--ssl-cert', process.env.WEB_SSL_CERT, '--ssl-key', process.env.WEB_SSL_KEY);
  process.env.WEB_ORIGIN ||= 'https://localhost:' + port;
}
async function check(port, host) {
  await new Promise((resolve, reject) => {
    const probe = createServer();
    probe.once('error', reject);
    probe.listen(port, host, () => probe.close(resolve));
  });
}
try {
  await check(apiPort, '127.0.0.1');
  // Check both loopback names: separate IPv4/IPv6 servers cause confusing login results.
  await check(port, '127.0.0.1');
  try {
    await check(port, '::1');
  } catch (e) {
    if (e.code !== 'EADDRNOTAVAIL') throw e;
  }
} catch (e) {
  console.error(
    `\nPort ${e.port || '4200 / 4301'} is already in use. Stop the previous Angular/authentication server with Ctrl+C in its terminal, then run npm start again.\nPowerShell: Get-NetTCPConnection -State Listen -LocalPort 4200,4301 | Select LocalAddress,LocalPort,OwningProcess\nInspect the listed process before stopping it; do not stop unrelated Node processes.`,
  );
  process.exit(1);
}
const server = startAuthServer(apiPort, port);
server.on('error', (e) => {
  console.error('Authentication adapter could not start:', e.message);
  process.exitCode = 1;
});
server.once('listening', () => {
  console.log('Workspace: ' + (process.env.WEB_ORIGIN || 'http://localhost:' + port) + '/login\nIdentity API: ' + (process.env.IDENTITY_API_URL || 'http://localhost:8080'));
  const app = spawn(
    process.execPath,
    [
      'node_modules/@angular/cli/bin/ng.js',
      'serve',
      '--configuration',
      'development',
      '--host',
      webHost,
      '--port',
      String(port),
      '--proxy-config',
      'proxy.auth.cjs',
      ...tlsArgs,
    ],
    { stdio: 'inherit', env: { ...process.env, AUTH_ADAPTER_PORT: String(apiPort) } },
  );
  let stopping = false;
  const stop = () => {
    if (stopping) return;
    stopping = true;
    app.kill();
    server.close();
  };
  app.on('error', (e) => {
    console.error(e.message);
    stop();
    process.exitCode = 1;
  });
  app.on('exit', (code) => {
    server.close();
    process.exitCode = code ?? 0;
  });
  for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, stop);
});
