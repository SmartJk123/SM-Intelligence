import { createWorkspaceStore } from './workspace-model.mjs';
import { dashboardData, sampleRecords } from './dashboard-model.mjs';
import { createServer } from 'node:http';
import { randomUUID } from 'node:crypto';

export function startMockServer(port = 4301, webPort = 4200, options = {}) {
  const hosted = options.hostedAuth === true;
  const fetchAuth = options.fetchAuth || fetch;
  const cookieName = hosted ? "sm_dev_session" : "sm_mock_session";
  const users = new Map([
    [
      'new@example.com',
      {
        id: 'seed-new',
        name: 'Amina Njeri',
        email: 'new@example.com',
        password: 'SamplePass123!',
        kind: 'individual',
        setupCompleted: false,
      },
    ],
    [
      'individual@example.com',
      {
        id: 'seed-returning',
        name: 'David Otieno',
        email: 'individual@example.com',
        password: 'SamplePass123!',
        kind: 'individual',
        setupCompleted: true,
      },
    ],
    [
      'business@example.com',
      {
        id: 'seed-business',
        name: 'Savanna Retail',
        email: 'business@example.com',
        password: 'SamplePass123!',
        kind: 'organization',
        setupCompleted: true,
      },
    ],
  ]);
  for (const [email, id, name] of [
    ['empty@example.com', 'seed-empty', 'Taylor Wanjiku'],
    ['slow@example.com', 'seed-slow', 'Sam Kariuki'],
    ['error@example.com', 'seed-error', 'Alex Mwangi'],
  ])
    users.set(email, {
      id,
      name,
      email,
      password: 'SamplePass123!',
      kind: 'individual',
      setupCompleted: true,
    });
  for (const [email,id,name,kind] of [
    ['realistic@example.com','realistic-personal','Nia Kamau','individual'],
    ['retail@example.com','realistic-retail','Acacia Retail Demo','organization'],
  ]) users.set(email,{id,name,email,kind,password:'SamplePass123!',setupCompleted:true});
  if (options.seed !== true || hosted) users.clear();
  const records = [...users.values()].map((user) => sampleRecords(user));
  const accounts = records.flatMap((r) => r.accounts);
  const transactions = records.flatMap((r) => r.transactions);
  const workspace = createWorkspaceStore(accounts, transactions);
  const failedOnce = new Set();
  const sessions = new Map();
  const expiries = new Map();
  const publicUser = ({ id, name, kind, setupCompleted }) => ({ id, name, kind, setupCompleted });
  const server = createServer(async (req, res) => {
    res.setHeader('Cache-Control', 'no-store');
    res.setHeader('Content-Type', 'application/json');
    const send = (status, data) => {
      res.writeHead(status);
      if (hosted && data?.source === "sample") data = { ...data, source: "local" };
      res.end(JSON.stringify(data));
    };
    // Development only. Never listen on a public interface or enable CORS.
    if (
      req.headers.origin &&
      !new RegExp(`^http://(localhost|127\\.0\\.0\\.1):${webPort}$`).test(req.headers.origin)
    )
      return send(403, { error: 'Local development only' });
    const cookie = req.headers.cookie
      ?.split(';')
      .map((v) => v.trim())
      .find((v) => v.startsWith(cookieName + '='))
      ?.slice(cookieName.length + 1);
    if (hosted && (expiries.get(cookie) || 0) <= Date.now()) { sessions.delete(cookie); expiries.delete(cookie); }
    const email = sessions.get(cookie);
    const user = email && users.get(email);
    const path = req.url?.split('?')[0];
    if (path === '/api/workspace' && req.method === 'GET')
      return user ? send(200, workspace.snapshot(user)) : send(401, { error: 'Sign in required' });
    if (path?.startsWith('/api/workspace/') && req.method === 'DELETE') {
      if (!user) return send(401, { error: 'Sign in required' });
      const parts = path.split('/');
      try {
        return send(200, workspace.mutate(user, parts[3], {}, decodeURIComponent(parts[4] || '')));
      } catch (e) {
        return send(e.status || 400, { error: e.message });
      }
    }
    if (req.method === 'GET' && path === '/api/auth/session')
      return user
        ? send(200, { user: publicUser(user) })
        : send(401, { error: 'Sign in required' });
    if (req.method === 'GET' && path === '/api/dashboard') {
      if (!user) return send(401, { error: 'Sign in required' });
      if (user.id === 'seed-error' && !failedOnce.has(user.id)) {
        failedOnce.add(user.id);
        return send(503, { error: 'Simulated temporary failure' });
      }
      if (user.id === 'seed-slow') await new Promise((resolve) => setTimeout(resolve, 3000));
      const days = Number(new URL(req.url, 'http://localhost').searchParams.get('days') ?? 30);
      if (![30, 90].includes(days)) return send(400, { error: 'Unsupported period' });
      const bank = new URL(req.url, 'http://localhost').searchParams.get('bank') || '';
      if (bank && !['KCB','Equity','Stanbic','NCBA'].includes(bank)) return send(400, {error:'Invalid bank'});
      return send(200, dashboardData(user, accounts, transactions, days, new Date(), bank));
    }
    if (req.method === 'POST' && path === '/api/auth/logout') {
      sessions.delete(cookie);
      expiries.delete(cookie);
      res.setHeader('Set-Cookie', cookieName + '=; HttpOnly; SameSite=Lax; Path=/api; Max-Age=0');
      return send(200, {});
    }
    if (req.method === 'GET' && path === '/api/accounts')
      return user
        ? send(200, {
            accounts: accounts.filter((a) => a.userId === user.id).map(({ userId, ...a }) => a),
          })
        : send(401, { error: 'Sign in required' });
    if (req.method !== 'POST') return send(404, { error: 'Not found' });
    let text = '';
    try {
      for await (const chunk of req) {
        text += chunk;
        if (text.length > 16384) {
          send(413, { error: 'Request too large' });
          return;
        }
      }
      const body = JSON.parse(text || '{}');
      if (path?.startsWith('/api/workspace/')) {
        if (!user) return send(401, { error: 'Sign in required' });
        try {
          return send(200, workspace.mutate(user, path.split('/')[3], body));
        } catch (e) {
          return send(e.status || 400, { error: e.message });
        }
      }
      if (hosted && (path === '/api/auth/login' || path === '/api/auth/register')) {
        const email = typeof body.email === 'string' ? body.email.trim() : '';
        if (!email || typeof body.password !== 'string' || !body.password) return send(400, {error:'Email and password are required'});
        const registering = path.endsWith('/register');
        if (registering && (!body.name?.trim() || !['individual','organization'].includes(body.kind) || body.password.length < 12))
          return send(400, {error:'Invalid registration'});
        let response;
        try {
          response = await fetchAuth('https://sm-backend-dev.onrender.com' + path, {
            method:'POST', headers:{'Content-Type':'application/json'}, signal:AbortSignal.timeout(90000),
            body:JSON.stringify(registering ? {name:body.name.trim(),email,password:body.password,phoneNumber:body.phone?.trim() || ''} : {email,password:body.password}),
          });
        } catch { return send(503, {error:'Authentication service is unavailable. Please retry.'}); }
        if (!response.ok) return send(response.status, {error:'Authentication request failed'});
        // Never forward registration entities: the hosted sample includes password hashes.
        if (registering) {
          await response.arrayBuffer();
          users.set(email,{id:randomUUID(),name:body.name.trim(),email,kind:body.kind,setupCompleted:false});
          return send(201,{registered:true});
        }
        let claims;
        try {
          const result=await response.json();
          const token=result.accessToken ?? result.token;
          claims=JSON.parse(Buffer.from(token.split('.')[1],'base64url').toString('utf8'));
          if (claims.sub !== email || !Number.isFinite(claims.exp) || claims.exp*1000 <= Date.now()) throw new Error();
        } catch { return send(502,{error:'Invalid session from authentication service'}); }
        // Trust only a token received directly from successful HTTPS credential verification.
        // The browser never supplies a token to this adapter.
        let target=users.get(email);
        if (!target) {
          target={id:randomUUID(),name:email.split('@')[0],email,kind:'individual',setupCompleted:false};
          users.set(email,target);
        }
        const session=randomUUID();
        sessions.set(session,email);expiries.set(session,claims.exp*1000);
        res.setHeader('Set-Cookie', cookieName+'='+session+'; HttpOnly; SameSite=Lax; Path=/api');
        return send(200,{user:publicUser(target)});
      }
      if (path === '/api/auth/login' || path === '/api/auth/register') {
        const normalized = typeof body.email === 'string' ? body.email.trim().toLowerCase() : '';
        let target = users.get(normalized);
        if (path.endsWith('/register')) {
          if (target) return send(409, { error: 'Email already registered' });
          if (
            !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(normalized) ||
            typeof body.password !== 'string' ||
            body.password.length < 12 ||
            !body.name?.trim() ||
            !['individual', 'organization'].includes(body.kind)
          )
            return send(400, { error: 'Invalid registration' });
          target = {
            id: randomUUID(),
            name: body.name.trim(),
            email: normalized,
            password: body.password,
            kind: body.kind,
            setupCompleted: false,
          };
          users.set(normalized, target);
        } else if (!target || target.password !== body.password)
          return send(401, { error: 'Invalid credentials' });
        const token = randomUUID();
        sessions.set(token, normalized);
        res.setHeader('Set-Cookie', `sm_mock_session=${token}; HttpOnly; SameSite=Lax; Path=/api`);
        return send(200, { user: publicUser(target) });
      }
      if (path === '/api/accounts') {
        if (!user) return send(401, { error: 'Sign in required' });
        if (
          !['KCB', 'Equity', 'NCBA', 'Stanbic'].includes(body.bank) ||
          !body.accountName?.trim() ||
          !/^\d{4,34}$/.test(body.accountNumber) ||
          !['debit', 'credit'].includes(body.cardType) ||
          body.currency !== 'KES' ||
          typeof body.balance !== 'number' ||
          !Number.isFinite(body.balance) ||
          body.balance < 0 ||
          Math.abs(body.balance * 100 - Math.round(body.balance * 100)) > 1e-6 ||
          !/^\d{4}-\d{2}-\d{2}$/.test(body.balanceDate) ||
          body.balanceDate > new Date().toLocaleDateString('en-CA')
        )
          return send(400, { error: 'Invalid account details' });
        const account = {
          id: randomUUID(),
          userId: user.id,
          bank: body.bank,
          accountName: body.accountName.trim(),
          maskedIdentifier: '•••• ' + body.accountNumber.slice(-4),
          accountType: body.cardType === 'credit' ? 'CREDIT' : 'DEPOSIT',
          availableBalanceMinor: body.cardType === 'debit' ? Math.round(body.balance * 100) : 0,
          creditOutstandingMinor: body.cardType === 'credit' ? Math.round(body.balance * 100) : 0,
        };
        accounts.push(account);
        user.setupCompleted = true;
        return send(201, { id: account.id });
      }
      send(404, { error: 'Not found' });
    } catch {
      if (!res.headersSent) send(400, { error: 'Invalid request' });
    }
  });
  server.listen(port, '127.0.0.1');
  return server;
}
