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
  return { server, base, async call(route, body, cookie, origin) {
    const response = await fetch(base + route, { method: body ? 'POST' : 'GET', headers: { 'Content-Type': 'application/json', ...(cookie ? { Cookie: cookie } : {}), ...(origin ? { Origin: origin } : {}) }, ...(body ? { body: JSON.stringify(body) } : {}) });
    return { status: response.status, body: await response.json(), cookie: response.headers.get('set-cookie') };
  }, async close() { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); } };
}
const input = { name: ' Team Member ', email: 'MEMBER@example.invalid', phone: ' +254700000000 ', password: 'ValidPassword123!', kind: 'organization' };

test('accounts proxy uses the verified session and returns persisted, masked records', async () => {
  let saved = []; const token = jwt();
  const fx = await fixture(async (url, init) => {
    if (url.pathname.endsWith('/me')) return Response.json(profile);
    if (url.pathname.endsWith('/login')) return Response.json({ token, userId });
    assert.equal(init.headers.Authorization, 'Bearer ' + token);
    assert.equal(url.search, '');
    if (init.method === 'POST') {
      assert.equal(url.pathname, '/api/accounts/manual');
      const value = JSON.parse(init.body);
      assert.equal(value.accountNumber, '12345678');
      saved = [{id:userId, userId, providerAccountId:'private-fingerprint', accountName:value.accountName,
        institution:'KCB', maskedIdentifier:'•••• 5678', accountType:'DEPOSIT', currency:'KES',
        availableBalance:10, creditOutstanding:0, accountStatus:'ACTIVE'}];
      return Response.json(saved[0], {status:201});
    }
    return Response.json(saved);
  });
  try {
    assert.equal((await fx.call('/api/accounts')).status, 401);
    const login = await fx.call('/api/auth/login', input); const cookie = login.cookie.split(';')[0];
    assert.deepEqual((await fx.call('/api/accounts', null, cookie)).body, []);
    const result = await fx.call('/api/accounts', {accountName:'Savings', accountNumber:'12345678'}, cookie);
    assert.equal(result.status, 201);
    assert.equal(result.body.providerAccountId, undefined);
    assert.equal(result.body.userId, undefined);
    assert.equal((await fx.call('/api/accounts?userId=someone-else', null, cookie)).body[0].id, userId);
  } finally { await fx.close(); }
});

test('invoice proxy requires a session and forwards the verified token, multipart body and document response', async () => {
  const token = jwt(); let uploadBody;
  const fx = await fixture(async (url, init) => {
    if (url.pathname.endsWith('/me')) return Response.json(profile);
    if (url.pathname.endsWith('/login')) return Response.json({ token, userId });
    assert.equal(init.headers.Authorization, 'Bearer ' + token);
    if (init.method === 'DELETE') {
      assert.equal(url.pathname, '/api/invoices/' + userId);
      return new Response(null, {status: 204});
    }
    if (init.method === 'POST') { uploadBody = init.body; return Response.json({ status: 'PENDING' }, { status: 201 }); }
    if (url.pathname.endsWith('/document')) return new Response('%PDF-test', { headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': 'attachment; filename="invoice.pdf"' } });
    return Response.json([]);
  });
  try {
    assert.equal((await fx.call('/api/invoices')).status, 401);
    const login = await fx.call('/api/auth/login', input);
    const cookie = login.cookie.split(';')[0];
    assert.deepEqual((await fx.call('/api/invoices', null, cookie)).body, []);
    const form = new FormData(); form.append('file', new Blob(['%PDF-test'], { type: 'application/pdf' }), 'invoice.pdf');
    const base = 'http://127.0.0.1:' + fx.server.address().port;
    assert.equal((await fetch(base + '/api/invoices/' + userId, { method: 'DELETE' })).status, 401);
    assert.equal((await fetch(base + '/api/invoices/' + userId, { method: 'DELETE', headers: { Cookie: cookie, Origin: 'https://untrusted.example' } })).status, 403);
    const deletion = await fetch(base + '/api/invoices/' + userId, {method: 'DELETE', headers: {Cookie: cookie}});
    assert.equal(deletion.status, 204);
    assert.equal(await deletion.text(), '');
    const response = await fetch(base + '/api/invoices', { method: 'POST', headers: { Cookie: cookie }, body: form });
    assert.equal(response.status, 201); assert.ok(uploadBody.includes(Buffer.from('%PDF-test')));
    const doc = await fetch(base + '/api/invoices/' + userId + '/document', { headers: { Cookie: cookie } });
    assert.equal(doc.headers.get('content-type'), 'application/pdf'); assert.equal(await doc.text(), '%PDF-test');
  } finally { await fx.close(); }
});

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
        // A fixed point safely in the past: sendDashboard captures `to = new Date()`
        // before this mock ever runs, so a transactionDate of "right now" can
        // flakily land a few ms after `to` and get excluded from the period.
        description: 'Salary', counterparty: 'Employer', transactionDate: new Date(Date.now() - 60000).toISOString(),
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
    assert.equal(result.body.transactions[0].description, 'Employer');
    assert.equal(result.body.transactions[0].category, 'Salary');
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

test('forgot and reset password pass through without a session and hide whether an account exists', async () => {
  const seen = [];
  const fx = await fixture(async (url, init) => {
    seen.push([url.pathname, JSON.parse(init.body)]);
    if (url.pathname.endsWith('/forgot-password')) return Response.json({ message: 'queued' }, { status: 202 });
    const { token } = JSON.parse(init.body);
    if (token === 'good-token') return new Response(null, { status: 204 });
    return Response.json({ code: 'INVALID_RESET_TOKEN' }, { status: 400 });
  });
  try {
    const requested = await fx.call('/api/auth/forgot-password', { email: ' Member@Example.invalid ' });
    assert.equal(requested.status, 202);
    assert.deepEqual(requested.body, { requested: true });
    assert.deepEqual(seen[0], ['/api/auth/forgot-password', { emailAddress: 'member@example.invalid' }]);
    assert.equal((await fx.call('/api/auth/forgot-password', { email: '' })).status, 400);
    const reset = await fx.call('/api/auth/reset-password', { token: 'good-token', password: 'BrandNewPassword2!' });
    assert.equal(reset.status, 200);
    const refused = await fx.call('/api/auth/reset-password', { token: 'used-token', password: 'BrandNewPassword2!' });
    assert.equal(refused.status, 400);
    assert.equal(refused.body.error, 'invalid-token');
  } finally { await fx.close(); }
});

test('removing an account unlinks it at the bank service first, then deletes it', async () => {
  const token = jwt(); const calls = []; let bankUp = true;
  const accountId = 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee';
  const fx = await fixture(async (url, init) => {
    if (url.pathname.endsWith('/me')) return Response.json(profile);
    if (url.pathname.endsWith('/login')) return Response.json({ token, userId });
    calls.push([init.method, url.pathname + url.search, init.headers?.Authorization]);
    if (url.pathname.startsWith('/api/v1/admin/account-links/by-account/'))
      return bankUp ? Response.json({ removed: 1 }) : new Response('down', { status: 502 });
    if (url.pathname === '/api/accounts/' + accountId) return Response.json({ id: accountId });
    return new Response(null, { status: 404 });
  });
  try {
    const cookie = (await fx.call('/api/auth/login', input)).cookie.split(';')[0];
    const remove = () => fetch(fx.base + '/api/accounts/' + accountId, { method: 'DELETE', headers: { Cookie: cookie } });

    // The bank service cannot confirm the unlink: the account is kept.
    bankUp = false;
    assert.equal((await remove()).status, 503);
    assert.equal(calls.filter(([m, p]) => m === 'DELETE' && p.startsWith('/api/accounts/')).length, 0);

    // Normal removal: unlink first, then delete permanently, both with the customer's token.
    bankUp = true; calls.length = 0;
    assert.equal((await remove()).status, 204);
    assert.deepEqual(calls.map(([m, p]) => m + ' ' + p), [
      'DELETE /api/v1/admin/account-links/by-account/' + accountId,
      'DELETE /api/accounts/' + accountId + '?permanent=true',
    ]);
    assert.ok(calls.every(([, , auth]) => auth === 'Bearer ' + token));

    // Without a session nothing is removed.
    assert.equal((await fetch(fx.base + '/api/accounts/' + accountId, { method: 'DELETE' })).status, 401);
  } finally { await fx.close(); }
});
