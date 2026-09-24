import { createServer } from 'node:http';
import { randomUUID } from 'node:crypto';

// Loopback development adapter. JWTs stay here; the browser receives an HttpOnly session cookie.
export function startAuthServer(port = 4301, webPort = 4200, options = {}) {
  const backend = new URL(options.identityUrl || process.env.IDENTITY_API_URL || 'http://localhost:8080');
  const invoiceBackend = new URL(options.invoiceUrl || process.env.INVOICE_API_URL || backend);
  const accountsBackend = new URL(options.accountsUrl || process.env.ACCOUNTS_API_URL || backend);
  if (accountsBackend.protocol !== 'https:' && !(accountsBackend.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(accountsBackend.hostname)))
    throw new Error('Accounts API requires HTTPS, except on loopback.');
  if (invoiceBackend.protocol !== 'https:' && !(invoiceBackend.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(invoiceBackend.hostname)))
    throw new Error('Invoice API requires HTTPS, except on loopback.');
  if (backend.protocol !== 'https:' && !(backend.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(backend.hostname)))
    throw new Error('Identity API requires HTTPS, except on loopback.');
  const requestBackend = options.fetchAuth || fetch;
  const sessions = new Map();
  const allowedOrigins = ['http://localhost:' + webPort, 'http://127.0.0.1:' + webPort];
  if (process.env.WEB_ORIGIN) allowedOrigins.push(new URL(process.env.WEB_ORIGIN).origin);
  const cookieName = 'sm_identity_session';
  const cookie = value => cookieName + '=' + value + '; HttpOnly; SameSite=Lax; Path=/api';
  const call = (route, init) => requestBackend(new URL(route, backend), { ...init, redirect: 'error', signal: AbortSignal.timeout(15000) });
  const publicUser = profile => ({ id: profile.id, name: profile.name, email: profile.emailAddress,
    kind: profile.accountType === 'ORGANIZATION' ? 'organization' : 'individual', setupCompleted: false });
  const validProfile = profile => profile && typeof profile.id === 'string' && /^[0-9a-f-]{36}$/i.test(profile.id)
    && typeof profile.name === 'string' && typeof profile.emailAddress === 'string'
    && ['INDIVIDUAL', 'ORGANIZATION'].includes(profile.accountType);
  const server = createServer(async (req, res) => {
    res.setHeader('Cache-Control', 'no-store');
    res.setHeader('Content-Type', 'application/json');
    const send = (status, data) => { res.writeHead(status); res.end(JSON.stringify(data)); };
    const clear = () => res.setHeader('Set-Cookie', cookie('') + '; Max-Age=0');
    if (req.headers.origin && !allowedOrigins.includes(req.headers.origin))
      return send(403, { error: 'Cross-origin requests are not allowed' });
    if (req.headers['sec-fetch-site'] === 'cross-site') return send(403, { error: 'Cross-site requests are not allowed' });
    for (const [id, value] of sessions) if (value.expiresAt <= Date.now()) sessions.delete(id);
    const sessionId = req.headers.cookie?.split(';').map(v => v.trim()).find(v => v.startsWith(cookieName + '='))?.slice(cookieName.length + 1);
    const session = sessions.get(sessionId);
    const route = req.url?.split('?')[0];
    try {
      if (route === '/api/auth/logout' && req.method === 'POST') {
        sessions.delete(sessionId); clear(); return send(200, { signedOut: true });
      }
      if (route === '/api/auth/session' && req.method === 'GET') {
        if (!session) { clear(); return send(401, { error: 'Sign in required' }); }
        const response = await call('/api/auth/me', { headers: { Authorization: 'Bearer ' + session.token } });
        if (response.status === 401 || response.status === 403) { sessions.delete(sessionId); clear(); return send(401, { error: 'Session expired' }); }
        if (!response.ok) return send(503, { error: 'Identity service unavailable' });
        const profile = await response.json();
        if (!validProfile(profile) || profile.id !== session.userId) return send(502, { error: 'Invalid identity response' });
        return send(200, { user: publicUser(profile) });
      }
      if (['/api/auth/login', '/api/auth/register'].includes(route) && req.method === 'POST') {
        let raw = ''; let size = 0;
        for await (const chunk of req) { size += chunk.length; if (size > 16384) return send(413, { error: 'Request too large' }); raw += chunk; }
        let body; try { body = JSON.parse(raw); } catch { return send(400, { error: 'Invalid JSON' }); }
        const registering = route.endsWith('/register');
        const emailAddress = typeof body?.email === 'string' ? body.email.trim().toLowerCase() : '';
        if (!emailAddress || typeof body?.password !== 'string' || !body.password) return send(400, { error: 'Email and password are required' });
        if (registering && (typeof body.name !== 'string' || body.name.trim().length < 2 || !['individual', 'organization'].includes(body.kind)))
          return send(400, { error: 'Name and account type are required' });
        const payload = { emailAddress, password: body.password };
        if (registering) Object.assign(payload, { name: body.name.trim(), phoneNumber: typeof body.phone === 'string' ? body.phone.trim() || null : null,
          accountType: body.kind === 'organization' ? 'ORGANIZATION' : 'INDIVIDUAL' });
        const response = await call(route, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
        if (!response.ok) return send([400, 401, 409, 429].includes(response.status) ? response.status : 503, { error: 'Authentication request failed' });
        // Registration has committed at this point. If the response/session cannot be established,
        // let the UI offer sign-in without submitting the registration again.
        try {
          const result = await response.json();
          const claims = JSON.parse(Buffer.from(result.token.split('.')[1], 'base64url').toString('utf8'));
          if (claims.sub !== result.userId || !Number.isFinite(claims.exp) || claims.exp * 1000 <= Date.now()) throw new Error('Invalid session');
          const verified = await call('/api/auth/me', { headers: { Authorization: 'Bearer ' + result.token } });
          if (!verified.ok) throw new Error('Session verification failed');
          const profile = await verified.json();
          if (!validProfile(profile) || profile.id !== result.userId || profile.emailAddress.toLowerCase() !== emailAddress) throw new Error('Identity mismatch');
          sessions.delete(sessionId);
          const id = randomUUID();
          sessions.set(id, { token: result.token, userId: profile.id, expiresAt: claims.exp * 1000 });
          res.setHeader('Set-Cookie', cookie(id));
          return send(registering ? 201 : 200, { user: publicUser(profile) });
        } catch {
          return registering ? send(201, { registered: true }) : send(502, { error: 'Invalid session from identity service' });
        }
      }
      if (!session) return send(401, { error: 'Sign in required' });
      if (route === '/api/accounts' && ['GET', 'POST'].includes(req.method)) {
        let body;
        if (req.method === 'POST') {
          let raw = ''; let size = 0;
          for await (const chunk of req) {
            size += chunk.length;
            if (size > 16384) return send(413, { error: 'Request too large' });
            raw += chunk;
          }
          try { body = JSON.stringify(JSON.parse(raw)); } catch { return send(400, { error: 'Invalid JSON' }); }
        }
        const response = await requestBackend(new URL(req.method === 'POST' ? '/api/accounts/manual' : '/api/accounts', accountsBackend), {
          method: req.method, redirect: 'error', signal: AbortSignal.timeout(15000),
          headers: { Authorization: 'Bearer ' + session.token, 'Content-Type': 'application/json' },
          ...(body ? { body } : {})
        });
        if (!response.ok) return send([400, 401, 403, 409].includes(response.status) ? response.status : 503,
          { error: response.status === 409 ? 'This account has already been added.' : 'Unable to load or save accounts. Please retry.' });
        const publicAccount = ({ id, accountName, institution, maskedIdentifier, accountType, currency, availableBalance, creditOutstanding, accountStatus }) =>
          ({ id, accountName, institution, maskedIdentifier, accountType, currency, availableBalance, creditOutstanding, accountStatus });
        const result = await response.json();
        return send(response.status, Array.isArray(result) ? result.map(publicAccount) : publicAccount(result));
      }
      if ((route === '/api/invoices' && ['GET', 'POST'].includes(req.method)) ||
          (/^\/api\/invoices\/[0-9a-f-]{36}$/i.test(route) && req.method === 'DELETE') ||
          (/^\/api\/invoices\/[0-9a-f-]{36}\/document$/i.test(route) && req.method === 'GET')) {
        const chunks = []; let size = 0;
        if (req.method === 'POST') {
          if (!req.headers['content-type']?.startsWith('multipart/form-data;')) return send(415, { error: 'Upload a multipart document' });
          for await (const chunk of req) {
            size += chunk.length;
            if (size > 11 * 1024 * 1024) return send(413, { error: 'Choose a file up to 10 MB' });
            chunks.push(chunk);
          }
        }
        const response = await requestBackend(new URL(req.url, invoiceBackend), { method: req.method, redirect: 'error', signal: AbortSignal.timeout(20000),
          headers: { Authorization: 'Bearer ' + session.token,
            ...(req.method === 'POST' ? { 'Content-Type': req.headers['content-type'] } : {}) },
          ...(req.method === 'POST' ? { body: Buffer.concat(chunks) } : {}) });
        if (!response.ok) {
          const messages = { 400: 'Check the invoice details and file format.', 401: 'Your session expired. Sign in again.',
            403: 'You cannot access this invoice.', 404: 'Invoice not found.', 409: 'This document has already been saved.',
            413: 'Choose a file up to 10 MB.' };
          return send(messages[response.status] ? response.status : 503,
            { error: messages[response.status] || 'Invoice service unavailable. Start the transactions service and retry.' });
        }
        res.setHeader('Content-Type', response.headers.get('content-type') || 'application/json');
        res.setHeader('X-Content-Type-Options', 'nosniff');
        if (response.headers.has('content-disposition')) res.setHeader('Content-Disposition', response.headers.get('content-disposition'));
        res.writeHead(response.status); res.end(Buffer.from(await response.arrayBuffer())); return;
      }
      // Financial integration is a later team milestone. Never create temporary records.
      return send(501, { error: 'This feature is not connected yet' });
    } catch {
      return send(503, { error: 'Identity service unavailable. Please retry.' });
    }
  });
  return server.listen(port, '127.0.0.1');
}
