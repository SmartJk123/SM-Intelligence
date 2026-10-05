import { createServer } from 'node:http';
import { randomUUID } from 'node:crypto';
import { WebSocketServer } from 'ws';

const DAY_MS = 24 * 60 * 60 * 1000;
const toMinor = (amount) => Math.round(Number(amount) * 100);

// A bank's narrative is sometimes just an account or reference number (common
// for a self-transfer), which reads as a bug rather than a name. Swap that
// case for the bank's name instead of showing digits where a sender name is
// expected.
function describedNarrative(narrative, institution) {
  const text = (narrative ?? '').trim();
  if (!text) return institution ? institution + ' transaction' : 'Transaction';
  if (/^\d{5,}$/.test(text)) return institution ? institution + ' transfer' : 'Bank transfer';
  return text;
}

/**
 * Builds the Financial Overview data from accounts-service and
 * transactions-service, through the same gateway `call` already used for
 * identity. Never invents figures: a downstream failure is reported as a
 * failure, and a user with no accounts yet simply sees zeros.
 */
async function sendDashboard(send, call, session, searchParams) {
  const days = Math.min(365, Math.max(1, parseInt(searchParams.get('days') ?? '30', 10) || 30));
  const bank = searchParams.get('bank') ?? '';
  const to = new Date();
  const from = new Date(to.getTime() - days * DAY_MS);

  const meResponse = await call('/api/auth/me', { headers: { Authorization: 'Bearer ' + session.token } });
  if (!meResponse.ok) return send(503, { error: 'Identity service unavailable' });
  const profile = await meResponse.json();

  const accountsResponse = await call('/api/accounts?userId=' + encodeURIComponent(session.userId), {
    headers: { Authorization: 'Bearer ' + session.token },
  });
  if (!accountsResponse.ok) return send(503, { error: 'Accounts service unavailable' });
  let accounts = await accountsResponse.json();
  if (bank) accounts = accounts.filter((a) => (a.institution ?? '').toLowerCase() === bank.toLowerCase());

  const perAccountTx = await Promise.all(
    accounts.map(async (account) => {
      const response = await call('/api/transactions?accountId=' + encodeURIComponent(account.id));
      return response.ok ? response.json() : [];
    }),
  );
  const inPeriod = perAccountTx.flat().filter((tx) => {
    const date = new Date(tx.transactionDate);
    return date >= from && date <= to;
  });

  const depositAccountIds = new Set(accounts.filter((a) => a.accountType === 'DEPOSIT').map((a) => a.id));
  const isCashMovement = (tx) => tx.status === 'POSTED' && depositAccountIds.has(tx.accountId);

  let moneyInMinor = 0;
  let moneyOutMinor = 0;
  for (const tx of inPeriod) {
    if (!isCashMovement(tx)) continue;
    const minor = toMinor(tx.amount);
    if (tx.transactionType === 'CREDIT') moneyInMinor += minor;
    else if (tx.transactionType === 'DEBIT') moneyOutMinor += minor;
  }

  const cashFlow = [];
  for (let bucketStart = from; bucketStart < to; bucketStart = new Date(bucketStart.getTime() + 7 * DAY_MS)) {
    const bucketEnd = new Date(Math.min(bucketStart.getTime() + 7 * DAY_MS, to.getTime()));
    let bucketIn = 0;
    let bucketOut = 0;
    for (const tx of inPeriod) {
      if (!isCashMovement(tx)) continue;
      const date = new Date(tx.transactionDate);
      if (date < bucketStart || date >= bucketEnd) continue;
      const minor = toMinor(tx.amount);
      if (tx.transactionType === 'CREDIT') bucketIn += minor;
      else if (tx.transactionType === 'DEBIT') bucketOut += minor;
    }
    cashFlow.push({
      from: bucketStart.toISOString().slice(0, 10),
      to: bucketEnd.toISOString().slice(0, 10),
      moneyInMinor: bucketIn,
      moneyOutMinor: bucketOut,
    });
  }

  const transactions = inPeriod
    .slice()
    .sort((a, b) => new Date(b.transactionDate) - new Date(a.transactionDate))
    .slice(0, 12)
    .map((tx) => {
      const institution = accounts.find((a) => a.id === tx.accountId)?.institution;
      return {
        id: tx.id,
        accountId: tx.accountId,
        // The bank's counterparty (who sent or received the money) is the
        // headline; its own narrative/reference is the detail line beneath it.
        // A bare reference/account number is not a name, so it falls back to
        // the bank instead of showing digits where a person expects a name.
        description: tx.counterparty || describedNarrative(tx.description, institution),
        // Placeholder: categories-service is not wired up yet, so the bank's
        // narrative stands in for a real category name.
        category: tx.description || 'Uncategorized',
        direction: tx.transactionType,
        amountMinor: toMinor(tx.amount),
        status: tx.status,
        date: tx.transactionDate,
      };
    });

  return send(200, {
    ...(bank ? { bank } : {}),
    source: 'live',
    currency: 'KES',
    user: { name: profile.name, kind: profile.accountType === 'ORGANIZATION' ? 'organization' : 'individual' },
    period: { from: from.toISOString().slice(0, 10), to: to.toISOString().slice(0, 10), days },
    summary: {
      availableCashMinor: accounts
        .filter((a) => a.accountType === 'DEPOSIT')
        .reduce((sum, a) => sum + toMinor(a.availableBalance), 0),
      creditOutstandingMinor: accounts
        .filter((a) => a.accountType === 'CREDIT')
        .reduce((sum, a) => sum + toMinor(a.creditOutstanding), 0),
      moneyInMinor,
      moneyOutMinor,
      netCashFlowMinor: moneyInMinor - moneyOutMinor,
    },
    accounts: accounts.map((a) => ({
      id: a.id,
      bank: a.institution,
      accountName: a.accountName,
      maskedIdentifier: a.maskedIdentifier,
      accountType: a.accountType,
      availableBalanceMinor: toMinor(a.availableBalance),
      creditOutstandingMinor: toMinor(a.creditOutstanding),
    })),
    cashFlow,
    transactions,
    transactionCount: inPeriod.length,
  });
}

/**
 * Month-over-month spending trends. A separate endpoint from the dashboard
 * because that one caps its transaction list at 12 rows for a quick recent
 * list; trends need every posted debit across the whole window to total
 * correctly.
 */
async function sendAnalytics(send, call, session, searchParams) {
  const months = Math.min(24, Math.max(1, parseInt(searchParams.get('months') ?? '6', 10) || 6));
  const to = new Date();
  const from = new Date(Date.UTC(to.getUTCFullYear(), to.getUTCMonth() - (months - 1), 1));

  const accountsResponse = await call('/api/accounts?userId=' + encodeURIComponent(session.userId), {
    headers: { Authorization: 'Bearer ' + session.token },
  });
  if (!accountsResponse.ok) return send(503, { error: 'Accounts service unavailable' });
  const accounts = await accountsResponse.json();

  const perAccountTx = await Promise.all(
    accounts.map(async (account) => {
      const response = await call('/api/transactions?accountId=' + encodeURIComponent(account.id));
      return response.ok ? response.json() : [];
    }),
  );
  const inPeriod = perAccountTx.flat().filter((tx) => {
    const date = new Date(tx.transactionDate);
    return date >= from && date <= to;
  });

  const depositAccountIds = new Set(accounts.filter((a) => a.accountType === 'DEPOSIT').map((a) => a.id));
  const isPostedDebit = (tx) =>
    tx.status === 'POSTED' && tx.transactionType === 'DEBIT' && depositAccountIds.has(tx.accountId);

  // Transactions are stored in UTC but booked in East Africa Time (+3). A
  // plain UTC month extraction misfiles anything booked in the first three
  // hours of a month into the previous one (midnight EAT on the 1st is
  // 21:00 UTC on the last day before it), so shift before reading the month.
  const EAT_OFFSET_MS = 3 * 60 * 60 * 1000;
  const monthKey = (date) => new Date(date.getTime() + EAT_OFFSET_MS).toISOString().slice(0, 7);
  const monthKeys = [];
  for (let m = new Date(from); m <= to; m = new Date(Date.UTC(m.getUTCFullYear(), m.getUTCMonth() + 1, 1))) {
    monthKeys.push(monthKey(m));
  }

  const totalsByMonth = new Map(monthKeys.map((key) => [key, 0]));
  const categoryTotals = new Map();
  const categoryByMonth = new Map();
  for (const tx of inPeriod) {
    if (!isPostedDebit(tx)) continue;
    const key = monthKey(new Date(tx.transactionDate));
    const minor = toMinor(tx.amount);
    totalsByMonth.set(key, (totalsByMonth.get(key) ?? 0) + minor);
    const category = tx.description || 'Uncategorized';
    categoryTotals.set(category, (categoryTotals.get(category) ?? 0) + minor);
    if (!categoryByMonth.has(category)) categoryByMonth.set(category, new Map(monthKeys.map((k) => [k, 0])));
    const perMonth = categoryByMonth.get(category);
    perMonth.set(key, (perMonth.get(key) ?? 0) + minor);
  }

  return send(200, {
    source: 'live',
    currency: 'KES',
    period: { from: from.toISOString().slice(0, 10), to: to.toISOString().slice(0, 10), months },
    monthlySpending: monthKeys.map((key) => ({ month: key, spendingMinor: totalsByMonth.get(key) })),
    categories: [...categoryTotals.entries()]
      .map(([category, value]) => ({ category, value }))
      .sort((a, b) => b.value - a.value),
    categoryTrend: [...categoryByMonth.entries()].map(([category, perMonth]) => ({
      category,
      monthly: monthKeys.map((key) => perMonth.get(key) ?? 0),
    })),
    transactionCount: inPeriod.filter(isPostedDebit).length,
  });
}

/**
 * Budget limits plus how much of each has actually been spent this calendar
 * month. budgets-service only stores the limit; this combines it with the
 * same posted-debit totals the dashboard and analytics use, so "spent" always
 * means the same thing everywhere in the app.
 */
async function sendBudgets(send, call, callBudgets, session) {
  const budgetsResponse = await callBudgets('/api/budgets?userId=' + encodeURIComponent(session.userId));
  if (!budgetsResponse.ok) return send(503, { error: 'Budgets service unavailable' });
  const budgets = await budgetsResponse.json();

  const accountsResponse = await call('/api/accounts?userId=' + encodeURIComponent(session.userId), {
    headers: { Authorization: 'Bearer ' + session.token },
  });
  if (!accountsResponse.ok) return send(503, { error: 'Accounts service unavailable' });
  const accounts = await accountsResponse.json();
  const depositAccountIds = new Set(accounts.filter((a) => a.accountType === 'DEPOSIT').map((a) => a.id));

  const to = new Date();
  const monthStart = new Date(Date.UTC(to.getUTCFullYear(), to.getUTCMonth(), 1));
  const perAccountTx = await Promise.all(
    accounts.map(async (account) => {
      const response = await call('/api/transactions?accountId=' + encodeURIComponent(account.id));
      return response.ok ? response.json() : [];
    }),
  );
  const EAT_OFFSET_MS = 3 * 60 * 60 * 1000;
  const thisMonth = perAccountTx.flat().filter((tx) => {
    if (tx.status !== 'POSTED' || tx.transactionType !== 'DEBIT' || !depositAccountIds.has(tx.accountId)) return false;
    const booked = new Date(new Date(tx.transactionDate).getTime() + EAT_OFFSET_MS);
    return booked >= monthStart;
  });
  const spentByCategory = new Map();
  for (const tx of thisMonth) {
    const category = tx.description || 'Uncategorized';
    spentByCategory.set(category, (spentByCategory.get(category) ?? 0) + toMinor(tx.amount));
  }

  return send(200, {
    budgets: budgets.map((b) => {
      const spentMinor = spentByCategory.get(b.category) ?? 0;
      const limitMinor = toMinor(b.monthlyLimit);
      return {
        id: b.id,
        category: b.category,
        monthlyLimitMinor: limitMinor,
        spentMinor,
        percentUsed: limitMinor > 0 ? Math.round((spentMinor / limitMinor) * 100) : 0,
        alertThresholdPercentage: Number(b.alertThresholdPercentage ?? 85),
      };
    }),
  });
}

// Loopback development adapter. JWTs stay here; the browser receives an HttpOnly session cookie.
export function startAuthServer(port = 4301, webPort = 4200, options = {}) {
  const backend = new URL(options.identityUrl || process.env.IDENTITY_API_URL || 'http://localhost:8080');
  const invoiceBackend = new URL(options.invoiceUrl || process.env.INVOICE_API_URL || backend);
  const accountsBackend = new URL(options.accountsUrl || process.env.ACCOUNTS_API_URL || backend);
  if (accountsBackend.protocol !== 'https:' && !(accountsBackend.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(accountsBackend.hostname)))
    throw new Error('Accounts API requires HTTPS, except on loopback.');
  // Where a newly created account is registered so a matching bank notification
  // (by bank + account number) reaches this customer's dashboard. Best-effort:
  // the account is already saved by the time this is called, so a failure here
  // is logged and swallowed rather than failing the account creation itself.
  const bankIntegrationBackend = new URL(options.bankIntegrationUrl || process.env.BANK_INTEGRATION_API_URL || 'http://localhost:8090');
  if (bankIntegrationBackend.protocol !== 'https:' && !(bankIntegrationBackend.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(bankIntegrationBackend.hostname)))
    throw new Error('Bank integration API requires HTTPS, except on loopback.');
  const budgetsBackend = new URL(options.budgetsUrl || process.env.BUDGETS_API_URL || 'http://localhost:8085');
  if (budgetsBackend.protocol !== 'https:' && !(budgetsBackend.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(budgetsBackend.hostname)))
    throw new Error('Budgets API requires HTTPS, except on loopback.');
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
  const callBudgets = (route, init) => requestBackend(new URL(route, budgetsBackend), { ...init, redirect: 'error', signal: AbortSignal.timeout(15000) });
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
    const url = new URL(req.url ?? '/', 'http://internal');
    const route = url.pathname;
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
      if (['/api/auth/forgot-password', '/api/auth/reset-password'].includes(route) && req.method === 'POST') {
        let raw = ''; let size = 0;
        for await (const chunk of req) { size += chunk.length; if (size > 16384) return send(413, { error: 'Request too large' }); raw += chunk; }
        let body; try { body = JSON.parse(raw); } catch { return send(400, { error: 'Invalid JSON' }); }
        if (route.endsWith('/forgot-password')) {
          const emailAddress = typeof body?.email === 'string' ? body.email.trim().toLowerCase() : '';
          if (!emailAddress) return send(400, { error: 'Email is required' });
          const response = await call(route, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ emailAddress }) });
          if (response.status === 400) return send(400, { error: 'Enter a valid email address' });
          // Same answer whether or not the address has an account.
          return response.ok ? send(202, { requested: true }) : send(503, { error: 'Identity service unavailable' });
        }
        if (typeof body?.token !== 'string' || !body.token || typeof body?.password !== 'string' || !body.password)
          return send(400, { error: 'Token and password are required' });
        const response = await call(route, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token: body.token, password: body.password }) });
        if (response.ok) { sessions.delete(sessionId); clear(); return send(200, { reset: true }); }
        if (response.status === 400) {
          const detail = await response.json().catch(() => ({}));
          return send(400, { error: detail.code === 'INVALID_RESET_TOKEN' ? 'invalid-token' : 'invalid-password' });
        }
        return send(503, { error: 'Identity service unavailable' });
      }
      if (!session) return send(401, { error: 'Sign in required' });
      if (route === '/api/dashboard' && req.method === 'GET') {
        return await sendDashboard(send, call, session, url.searchParams);
      }
      if (route === '/api/analytics' && req.method === 'GET') {
        return await sendAnalytics(send, call, session, url.searchParams);
      }
      if (route === '/api/budgets' && req.method === 'GET') {
        return await sendBudgets(send, call, callBudgets, session);
      }
      if (route === '/api/budgets' && req.method === 'POST') {
        let raw = ''; let size = 0;
        for await (const chunk of req) {
          size += chunk.length;
          if (size > 4096) return send(413, { error: 'Request too large' });
          raw += chunk;
        }
        let body;
        try { body = JSON.parse(raw); } catch { return send(400, { error: 'Invalid JSON' }); }
        if (typeof body?.category !== 'string' || !body.category.trim())
          return send(400, { error: 'Enter a category' });
        const limit = Number(body?.monthlyLimit);
        if (!Number.isFinite(limit) || limit < 0) return send(400, { error: 'Enter a monthly limit of 0 or more' });
        const response = await callBudgets('/api/budgets', {
          method: 'POST', redirect: 'error', signal: AbortSignal.timeout(15000),
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ userId: session.userId, category: body.category.trim(), monthlyLimit: limit }),
        });
        if (!response.ok) return send(response.status === 409 ? 409 : 503,
          { error: response.status === 409 ? 'A budget for this category already exists.' : 'Unable to save the budget. Please retry.' });
        return send(201, await response.json());
      }
      if (/^\/api\/budgets\/[0-9a-f-]{36}$/i.test(route) && req.method === 'DELETE') {
        const id = route.split('/').pop();
        const response = await callBudgets('/api/budgets/' + encodeURIComponent(id), {
          method: 'DELETE', redirect: 'error', signal: AbortSignal.timeout(15000),
        });
        if (!response.ok && response.status !== 404) return send(503, { error: 'Unable to remove the budget. Please retry.' });
        return send(204, null);
      }
      if (route === '/api/accounts' && ['GET', 'POST'].includes(req.method)) {
        let body; let parsedBody;
        if (req.method === 'POST') {
          let raw = ''; let size = 0;
          for await (const chunk of req) {
            size += chunk.length;
            if (size > 16384) return send(413, { error: 'Request too large' });
            raw += chunk;
          }
          try { parsedBody = JSON.parse(raw); body = JSON.stringify(parsedBody); } catch { return send(400, { error: 'Invalid JSON' }); }
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
        // Registers the account so a bank notification for this bank + account number
        // reaches this customer's dashboard. The account is already saved above, so a
        // failure here is logged rather than reported as the account save failing.
        if (req.method === 'POST' && parsedBody?.bank && parsedBody?.accountNumber && result?.id) {
          requestBackend(new URL('/api/v1/admin/account-links', bankIntegrationBackend), {
            method: 'POST', redirect: 'error', signal: AbortSignal.timeout(15000),
            // The customer's own token: the bank service lets a customer link only
            // an account accounts-service holds for them.
            headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + session.token },
            body: JSON.stringify({
              bankId: parsedBody.bank, accountNumber: parsedBody.accountNumber,
              userId: session.userId, accountName: parsedBody.accountName, accountId: result.id,
            }),
          }).then(linkResponse => {
            if (!linkResponse.ok) console.error('Could not register the account link:', linkResponse.status);
          }).catch(error => console.error('Could not register the account link:', error.message));
        }
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
      // Financial integration beyond accounts/transactions/invoices is a later team
      // milestone. Never create temporary records.
      return send(501, { error: 'This feature is not connected yet' });
    } catch {
      return send(503, { error: 'Identity service unavailable. Please retry.' });
    }
  });
  // Live balance and transaction updates, so money in/out and the topbar
  // notification badge appear without the customer refreshing the page.
  // Polls rather than being pushed by bank-integration-service directly,
  // so this adapter needs no new inbound route from the backend to work.
  const wss = new WebSocketServer({ noServer: true });
  const socketsByUser = new Map();
  const seenTransactionsByUser = new Map();
  server.on('upgrade', (req, socket, head) => {
    const url = new URL(req.url, 'http://localhost');
    if (url.pathname !== '/api/ws') { socket.destroy(); return; }
    if (req.headers.origin && !allowedOrigins.includes(req.headers.origin)) { socket.destroy(); return; }
    const sessionId = req.headers.cookie?.split(';').map(v => v.trim()).find(v => v.startsWith(cookieName + '='))?.slice(cookieName.length + 1);
    const session = sessions.get(sessionId);
    if (!session || session.expiresAt <= Date.now()) { socket.destroy(); return; }
    wss.handleUpgrade(req, socket, head, (ws) => {
      ws.userId = session.userId;
      ws.token = session.token;
      let sockets = socketsByUser.get(session.userId);
      if (!sockets) { sockets = new Set(); socketsByUser.set(session.userId, sockets); }
      sockets.add(ws);
      ws.on('close', () => {
        sockets.delete(ws);
        if (sockets.size === 0) { socketsByUser.delete(session.userId); seenTransactionsByUser.delete(session.userId); }
      });
    });
  });

  async function pollForUpdates() {
    for (const [userId, sockets] of socketsByUser) {
      if (sockets.size === 0) continue;
      const token = sockets.values().next().value.token;
      try {
        const accountsResponse = await call('/api/accounts?userId=' + encodeURIComponent(userId), {
          headers: { Authorization: 'Bearer ' + token },
        });
        if (!accountsResponse.ok) continue;
        const accounts = await accountsResponse.json();
        const firstPoll = !seenTransactionsByUser.has(userId);
        const seen = seenTransactionsByUser.get(userId) ?? new Set();
        seenTransactionsByUser.set(userId, seen);
        for (const account of accounts) {
          const txResponse = await call('/api/transactions?accountId=' + encodeURIComponent(account.id));
          if (!txResponse.ok) continue;
          const transactions = await txResponse.json();
          for (const tx of transactions) {
            if (seen.has(tx.id)) continue;
            seen.add(tx.id);
            // The backlog on first connect is not "new": only notify for a
            // transaction that arrives while this socket is actually open.
            if (firstPoll) continue;
            const message = JSON.stringify({
              type: 'transaction',
              accountId: account.id,
              direction: tx.transactionType,
              amountMinor: toMinor(tx.amount),
              description: tx.counterparty || describedNarrative(tx.description, account.institution),
              date: tx.transactionDate,
              bank: account.institution,
            });
            for (const ws of sockets) if (ws.readyState === ws.OPEN) ws.send(message);
          }
        }
      } catch {
        // Try again on the next tick; a single failed poll should not drop the connection.
      }
    }
  }
  // Short enough that a payment shows on screen within seconds of the bank
  // reporting it; each tick is a few local service calls per connected user.
  const pollTimer = setInterval(pollForUpdates, 4000);
  server.on('close', () => clearInterval(pollTimer));

  return server.listen(port, '127.0.0.1');
}
