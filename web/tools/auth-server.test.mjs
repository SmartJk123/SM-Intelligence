import test from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { startAuthServer } from './auth-server.mjs';
const userId = '12345678-1234-4234-8234-123456789abc';
const profile = { id: userId, name: 'Team Member', emailAddress: 'member@example.invalid', accountType: 'ORGANIZATION' };
const jwt = (subject = userId, expires = Date.now() / 1000 + 60) => 'header.' + Buffer.from(JSON.stringify({ sub: subject, exp: expires })).toString('base64url') + '.signature';
async function fixture(fetchAuth) {
  const server = startAuthServer(0, 4200, { fetchAuth, identityUrl: 'http://localhost:8080' });
  await once(server, 'listening');
  const base = 'http://127.0.0.1:' + server.address().port;
  return { server, async call(route, body, cookie, origin) {
    const response = await fetch(base + route, { method: body ? 'POST' : 'GET', headers: { 'Content-Type': 'application/json', ...(cookie ? { Cookie: cookie } : {}), ...(origin ? { Origin: origin } : {}) }, ...(body ? { body: JSON.stringify(body) } : {}) });
    return { status: response.status, body: await response.json(), cookie: response.headers.get('set-cookie') };
  }, async close() { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); } };
}
const input = { name: ' Team Member ', email: 'MEMBER@example.invalid', phone: ' +254700000000 ', password: 'ValidPassword123!', kind: 'organization' };

test('maps the real identity contract, validates /me, restores profile and never creates financial records', async () => {
  const calls = [];
  const fx = await fixture(async (url, init) => {
    calls.push({ path: url.pathname, init });
    if (url.pathname.endsWith('/me')) { assert.equal(init.headers.Authorization, 'Bearer ' + jwtToken); return Response.json(profile); }
    const body = JSON.parse(init.body);
    if (body.password === 'wrong') return Response.json({}, { status: 401 });
    assert.equal(body.emailAddress, profile.emailAddress);
    assert.equal(body.email, undefined);
    return Response.json({ token: jwtToken, userId, expiresIn: 60000 }, { status: url.pathname.endsWith('/register') ? 201 : 200 });
  });
  const jwtToken = jwt();
  try {
    assert.equal((await fx.call('/api/auth/session')).status, 401);
    const result = await fx.call('/api/auth/register', input);
    assert.equal(result.status, 201);
    assert.deepEqual(JSON.parse(calls[0].init.body), { emailAddress: profile.emailAddress, password: input.password, name: 'Team Member', phoneNumber: '+254700000000', accountType: 'ORGANIZATION' });
    assert.equal(result.body.user.id, userId);
    assert.equal(result.body.user.kind, 'organization');
    assert.equal(result.body.user.email, profile.emailAddress);
    assert.equal(result.body.token, undefined);
    assert.ok(result.cookie.includes('HttpOnly'));
    const cookie = result.cookie.split(';')[0];
    assert.equal((await fx.call('/api/auth/session', null, cookie)).body.user.name, profile.name);
    assert.equal((await fx.call('/api/accounts', { accountName: 'No temporary account' }, cookie)).status, 501);
    assert.equal((await fx.call('/api/workspace', null, cookie)).status, 501);
    assert.equal((await fx.call('/api/auth/login', { email: input.email, password: 'wrong' })).status, 401);
    assert.equal((await fx.call('/api/auth/logout', {}, cookie, 'https://untrusted.example')).status, 403);
    await fx.call('/api/auth/logout', {}, cookie);
    assert.equal((await fx.call('/api/auth/session', null, cookie)).status, 401);
    const login = await fx.call('/api/auth/login', input);
    assert.equal(login.body.user.id, userId);
  } finally { await fx.close(); }
});

test('rejects invalid, expired and unverified sessions instead of trusting decoded JWT claims', async () => {
  for (const token of [jwt('wrong-id'), jwt(userId, 1), 'invalid']) {
    const fx = await fixture(async () => Response.json({ token, userId }));
    try { assert.equal((await fx.call('/api/auth/login', input)).status, 502); } finally { await fx.close(); }
  }
  const fx = await fixture(async url => url.pathname.endsWith('/me') ? Response.json({}, {status:401}) : Response.json({token:jwt(), userId}));
  try { assert.equal((await fx.call('/api/auth/login', input)).status, 502); } finally { await fx.close(); }
});

test('preserves successful registration when session verification fails and exposes backend outages', async () => {
  const fx = await fixture(async url => { if (url.pathname.endsWith('/me')) throw new Error('offline'); return Response.json({token:jwt(),userId}, {status:201}); });
  try { assert.deepEqual((await fx.call('/api/auth/register', input)).body, {registered:true}); } finally { await fx.close(); }
  const down = await fixture(async () => { throw new Error('offline'); });
  try { assert.equal((await down.call('/api/auth/login', input)).status, 503); } finally { await down.close(); }
});

test('session refresh rejects a revoked backend token', async () => {
  let valid = true;
  const fx = await fixture(async url => url.pathname.endsWith('/me') ? Response.json(valid ? profile : {}, {status: valid ? 200 : 401}) : Response.json({token:jwt(),userId}));
  try {
    const login = await fx.call('/api/auth/login', input);
    valid = false;
    const result = await fx.call('/api/auth/session', null, login.cookie.split(';')[0]);
    assert.equal(result.status,401);
    assert.ok(result.cookie.includes('Max-Age=0'));
  } finally { await fx.close(); }
});

const accountId = '87654321-4321-4321-8321-abcdef123456';

test('builds the Financial Overview from accounts-service and transactions-service', async () => {
  const fx = await fixture(async (url) => {
    if (url.pathname.endsWith('/me')) return Response.json(profile);
    if (url.pathname === '/api/auth/login') return Response.json({ token: jwt(), userId });
    if (url.pathname === '/api/accounts') {
      assert.equal(url.searchParams.get('userId'), userId);
      return Response.json([{
        id: accountId, institution: 'KCB', accountName: 'Everyday', maskedIdentifier: '••1234',
        accountType: 'DEPOSIT', availableBalance: '1000.00', creditOutstanding: '0.00',
      }]);
    }
    if (url.pathname === '/api/transactions') {
      assert.equal(url.searchParams.get('accountId'), accountId);
      return Response.json([{
        id: 'tx-1', accountId, amount: '250.00', transactionType: 'CREDIT', status: 'POSTED',
        description: 'Salary', counterparty: 'Employer', transactionDate: new Date().toISOString(),
      }]);
    }
    throw new Error('unexpected call to ' + url.pathname);
  });
  try {
    const login = await fx.call('/api/auth/login', input);
    const cookie = login.cookie.split(';')[0];
    const result = await fx.call('/api/dashboard?days=30', null, cookie);
    assert.equal(result.status, 200);
    assert.equal(result.body.source, 'live');
    assert.equal(result.body.accounts.length, 1);
    assert.equal(result.body.accounts[0].availableBalanceMinor, 100000);
    assert.equal(result.body.summary.moneyInMinor, 25000);
    assert.equal(result.body.summary.moneyOutMinor, 0);
    assert.equal(result.body.transactions[0].description, 'Salary');
    assert.equal(result.body.transactionCount, 1);
  } finally { await fx.close(); }
});

test('dashboard requires a session and never invents data when downstream is unreachable', async () => {
  const anon = await fixture(async () => { throw new Error('should not be called'); });
  try { assert.equal((await anon.call('/api/dashboard')).status, 401); } finally { await anon.close(); }

  const fx = await fixture(async (url) => {
    if (url.pathname.endsWith('/me')) return Response.json(profile);
    if (url.pathname === '/api/auth/login') return Response.json({ token: jwt(), userId });
    throw new Error('offline');
  });
  try {
    const login = await fx.call('/api/auth/login', input);
    const cookie = login.cookie.split(';')[0];
    assert.equal((await fx.call('/api/dashboard', null, cookie)).status, 503);
  } finally { await fx.close(); }
});
