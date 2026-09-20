import { createServer } from 'node:http';
import { randomUUID } from 'node:crypto';

// Loopback development adapter. JWTs stay here; the browser receives an HttpOnly session cookie.
export function startAuthServer(port = 4301, webPort = 4200, options = {}) {
  const backend = new URL(options.identityUrl || process.env.IDENTITY_API_URL || 'http://localhost:8080');
  if (backend.protocol !== 'https:' && !(backend.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(backend.hostname)))
    throw new Error('Identity API requires HTTPS, except on loopback.');
  const requestBackend = options.fetchAuth || fetch;
  const sessions = new Map();
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
    if (req.headers.origin && !['http://localhost:' + webPort, 'http://127.0.0.1:' + webPort].includes(req.headers.origin))
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
      // Financial integration is a later team milestone. Never create temporary records.
      return send(501, { error: 'This feature is not connected yet' });
    } catch {
      return send(503, { error: 'Identity service unavailable. Please retry.' });
    }
  });
  return server.listen(port, '127.0.0.1');
}
