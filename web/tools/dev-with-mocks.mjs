import { spawn } from 'node:child_process';
import { createServer } from 'node:net';
import { startMockServer } from './mock-api.mjs';
const port = Number(process.env.MOCK_WEB_PORT || 4200);
const apiPort = Number(process.env.MOCK_API_PORT || 4301);
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
    `\nPort ${e.port || '4200 / 4301'} is already in use. Stop the previous Angular/mock server with Ctrl+C in its terminal, then run npm run start:mock again.\nPowerShell: Get-NetTCPConnection -State Listen -LocalPort 4200,4301 | Select LocalAddress,LocalPort,OwningProcess\nInspect the listed process before stopping it; do not stop unrelated Node processes.`,
  );
  process.exit(1);
}
const server = startMockServer(apiPort, port);
server.on('error', (e) => {
  console.error('Mock API could not start:', e.message);
  process.exitCode = 1;
});
server.once('listening', () => {
  console.log(
    `Mock dashboard: http://localhost:${port}/login\nSample users: individual@example.com, business@example.com, new@example.com, empty@example.com\nPassword: SamplePass123!\nRecords reset on restart. Use fictional details only.`,
  );
  const app = spawn(
    process.execPath,
    [
      'node_modules/@angular/cli/bin/ng.js',
      'serve',
      '--host',
      'localhost',
      '--port',
      String(port),
      '--proxy-config',
      'proxy.mock.cjs',
    ],
    { stdio: 'inherit', env: { ...process.env, MOCK_API_PORT: String(apiPort) } },
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

