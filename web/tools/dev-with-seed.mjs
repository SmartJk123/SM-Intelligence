import { spawn } from 'node:child_process';
import { startSeedServer } from './seed-server.mjs';
const server = startSeedServer();
server.on('error', error => { console.error(`Seed server could not start: ${error.message}`); process.exit(1); });
server.once('listening', () => {
  console.log('Local seed mode: http://localhost:4200');
  console.log('Test accounts: new.user@example.com / returning.user@example.com / business.user@example.com');
  console.log('Password for each: SeedPass123! — fictional data only; restarting resets all changes.');
  const app = spawn(process.execPath, ['node_modules/@angular/cli/bin/ng.js', 'serve', '--host', '127.0.0.1', '--port', '4200', '--proxy-config', 'proxy.seed.json'], { stdio: 'inherit' });
  app.on('exit', code => { server.close(); process.exit(code ?? 0); });
  for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => { app.kill(); server.close(); });
});
