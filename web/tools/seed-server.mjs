import { createServer } from 'node:http';
import { randomUUID } from 'node:crypto';

export function startSeedServer(port = 4301) {
  const users = new Map([
    ['new.user@example.com', { id: 'seed-new', name: 'Amina Njeri', email: 'new.user@example.com', password: 'SeedPass123!', kind: 'individual', setupCompleted: false }],
    ['returning.user@example.com', { id: 'seed-returning', name: 'David Otieno', email: 'returning.user@example.com', password: 'SeedPass123!', kind: 'individual', setupCompleted: true }],
    ['business.user@example.com', { id: 'seed-business', name: 'Savanna Retail', email: 'business.user@example.com', password: 'SeedPass123!', kind: 'organization', setupCompleted: false }],
  ]);
  const accounts = [{ id: 'seed-account', userId: 'seed-returning', bank: 'KCB', accountName: 'Personal savings', lastFour: '4321', cardType: 'debit', balance: 45000, balanceDate: new Date().toLocaleDateString('en-CA'), currency: 'KES' }];
  const sessions = new Map();
  const publicUser = ({ id, name, kind, setupCompleted }) => ({ id, name, kind, setupCompleted });
  const server = createServer(async (req, res) => {
    res.setHeader('Cache-Control', 'no-store');
    res.setHeader('Content-Type', 'application/json');
    const send = (status, data) => { res.writeHead(status); res.end(JSON.stringify(data)); };
    // Development only. Never listen on a public interface or enable CORS.
    if (req.headers.origin && !/^http:\/\/(localhost|127\.0\.0\.1):4200$/.test(req.headers.origin)) return send(403, { error: 'Local development only' });
    const cookie = req.headers.cookie?.split(';').map(v => v.trim()).find(v => v.startsWith('sm_seed_session='))?.slice('sm_seed_session='.length);
    const email = sessions.get(cookie); const user = email && users.get(email);
    const path = req.url?.split('?')[0];
    if (req.method === 'GET' && path === '/api/auth/session') return user ? send(200, { user: publicUser(user) }) : send(401, { error: 'Sign in required' });
    if (req.method === 'GET' && path === '/api/accounts') return user ? send(200, { accounts: accounts.filter(a => a.userId === user.id).map(({userId, ...a}) => a) }) : send(401, { error: 'Sign in required' });
    if (req.method !== 'POST') return send(404, { error: 'Not found' });
    let text = '';
    try {
      for await (const chunk of req) { text += chunk; if (text.length > 16384) { send(413, { error: 'Request too large' }); return; } }
      const body = JSON.parse(text || '{}');
      if (path === '/api/auth/login' || path === '/api/auth/register') {
        const normalized = typeof body.email === 'string' ? body.email.trim().toLowerCase() : '';
        let target = users.get(normalized);
        if (path.endsWith('/register')) {
          if (target) return send(409, { error: 'Email already registered' });
          if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(normalized) || typeof body.password !== 'string' || body.password.length < 12 || !body.name?.trim() || !['individual','organization'].includes(body.kind)) return send(400, { error: 'Invalid registration' });
          target = { id: randomUUID(), name: body.name.trim(), email: normalized, password: body.password, kind: body.kind, setupCompleted: false }; users.set(normalized, target);
        } else if (!target || target.password !== body.password) return send(401, { error: 'Invalid credentials' });
        const token = randomUUID(); sessions.set(token, normalized);
        res.setHeader('Set-Cookie', `sm_seed_session=${token}; HttpOnly; SameSite=Lax; Path=/api`);
        return send(200, { user: publicUser(target) });
      }
      if (path === '/api/accounts') {
        if (!user) return send(401, { error: 'Sign in required' });
        if (!['KCB','Equity','NCBA','Stanbic'].includes(body.bank) || !body.accountName?.trim() || !/^\d{4,34}$/.test(body.accountNumber) || !['debit','credit'].includes(body.cardType) || body.currency !== 'KES' || typeof body.balance !== 'number' || !Number.isFinite(body.balance) || body.balance < 0 || Math.abs(body.balance*100-Math.round(body.balance*100))>1e-6 || !/^\d{4}-\d{2}-\d{2}$/.test(body.balanceDate) || body.balanceDate > new Date().toLocaleDateString('en-CA')) return send(400, { error: 'Invalid account details' });
        const account = { id: randomUUID(), userId: user.id, bank: body.bank, accountName: body.accountName.trim(), lastFour: body.accountNumber.slice(-4), cardType: body.cardType, balance: body.balance, balanceDate: body.balanceDate, currency: 'KES' };
        accounts.push(account); user.setupCompleted = true; return send(201, { id: account.id });
      }
      send(404, { error: 'Not found' });
    } catch { if (!res.headersSent) send(400, { error: 'Invalid request' }); }
  });
  server.listen(port, '127.0.0.1');
  return server;
}
