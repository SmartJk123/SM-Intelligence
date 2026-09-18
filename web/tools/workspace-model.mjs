import { randomUUID } from 'node:crypto';
const today = () => new Date().toISOString().slice(0, 10);
const date = (v) =>
  typeof v === 'string' &&
  /^\d{4}-\d{2}-\d{2}$/.test(v) &&
  !Number.isNaN(Date.parse(v)) &&
  new Date(v).toISOString().slice(0, 10) === v;
const money = (v) => Number.isSafeInteger(v) && v >= 0 && v <= 1000000000000;
const text = (v) => typeof v === 'string' && v.trim().length > 0 && v.length <= 200;
export function createWorkspaceStore(accounts, transactions) {
  const states = new Map();
  const state = (user) => {
    if (!states.has(user.id)) {
      const populated = user.id.startsWith('realistic-') || ['seed-returning', 'seed-business', 'seed-slow', 'seed-error'].includes(
        user.id,
      );
      const factor = user.kind === 'organization' ? 8 : 1;
      const end = today(),
        start = new Date(Date.now() - 29 * 86400000).toISOString().slice(0, 10);
      const realistic = user.id.startsWith('realistic-');
      states.set(user.id, {
        budgets: populated
          ? [
              {
                id: randomUUID(),
                category: 'Shopping',
                allocatedMinor: 1000000 * factor,
                start,
                end,
                accountId: realistic ? user.id+'-bank-0' : '',
                threshold: 85,
              },
              {
                id: randomUUID(),
                category: 'Rent',
                allocatedMinor: 4000000 * factor,
                start,
                end,
                accountId: '',
                threshold: 85,
              },
            ]
          : [],
        investments: populated
          ? [
              {
                id: randomUUID(),
                name: 'Money market savings',
                type: 'Money Market',
                principalMinor: 10000000 * factor,
                currentValueMinor: 10450000 * factor,
                valuationDate: end,
                maturityDate: '',
              },
              {
                id: randomUUID(),
                name: 'Fixed deposit',
                type: 'Fixed Deposit',
                principalMinor: 5000000 * factor,
                currentValueMinor: 5150000 * factor,
                valuationDate: end,
                maturityDate: new Date(Date.now() + (realistic ? 3 : 45) * 86400000).toISOString().slice(0, 10),
              },
            ]
          : [],
        profile: {
          organization: user.kind === 'organization' ? user.name : '',
          lowBalanceMinor: 10000000,
          budgetAlerts: true,
          balanceAlerts: true,
          maturityAlerts: true,
        },
        read: realistic ? ['transaction:'+user.id+'-activity-0'] : [],
        audit: [],
      });
    }
    return states.get(user.id);
  };
  const snapshot = (user) => {
    const s = state(user);
    const owned = accounts.filter((a) => a.userId === user.id);
    const ids = new Set(owned.map((a) => a.id));
    return {
      source: 'sample',
      profile: { name: user.name, email: user.email, kind: user.kind, ...s.profile },
      accounts: owned.map(({ userId, ...a }) => a),
      transactions: transactions.filter((t) => ids.has(t.accountId)),
      budgets: s.budgets,
      investments: s.investments,
      read: s.read,
      audit: s.audit.slice(-50).reverse(),
    };
  };
  function mutate(user, collection, body, removeId) {
    const s = state(user);
    const owned = (id) => accounts.some((a) => a.userId === user.id && a.id === id);
    const fail = (message) => {
      const error = new Error(message);
      error.status = 400;
      throw error;
    };
    let action = '';
    if (removeId) {
      if (!['budgets', 'investments'].includes(collection)) fail('This record cannot be deleted.');
      const index = s[collection].findIndex((r) => r.id === removeId);
      if (index < 0) fail('Record not found.');
      s[collection].splice(index, 1);
      action = 'Deleted ' + collection + ' record';
    } else if (collection === 'read') {
      if (
        !Array.isArray(body.ids) ||
        body.ids.some((id) => typeof id !== 'string' || id.length > 150)
      )
        fail('Invalid notification identifiers.');
      s.read = [...new Set([...s.read, ...body.ids])].slice(-500);
    } else if (collection === 'profile') {
      if (
        !text(body.name) ||
        typeof body.organization !== 'string' ||
        body.organization.length > 200 ||
        !money(body.lowBalanceMinor) ||
        ['budgetAlerts', 'balanceAlerts', 'maturityAlerts'].some(
          (k) => typeof body[k] !== 'boolean',
        )
      )
        fail('Enter a name and valid alert preferences.');
      user.name = body.name.trim();
      s.profile = {
        organization: body.organization.trim(),
        lowBalanceMinor: body.lowBalanceMinor,
        budgetAlerts: body.budgetAlerts,
        balanceAlerts: body.balanceAlerts,
        maturityAlerts: body.maturityAlerts,
      };
      action = 'Updated profile and alert preferences';
    } else {
      let value;
      let target;
      if (collection === 'transactions') {
        if (
          !owned(body.accountId) ||
          !text(body.description) ||
          !text(body.category) ||
          !date(body.date) ||
          body.date > today() ||
          !['CREDIT', 'DEBIT'].includes(body.direction) ||
          !['POSTED', 'PENDING', 'FAILED', 'REVERSED', 'CANCELLED'].includes(body.status) ||
          !money(body.amountMinor) ||
          body.amountMinor === 0
        )
          fail('Enter a valid account, date, description and positive amount.');
        target = transactions;
        value = {
          accountId: body.accountId,
          date: body.date,
          description: body.description.trim(),
          category: body.category.trim(),
          direction: body.direction,
          status: body.status,
          amountMinor: body.amountMinor,
        };
        if (body.id && !transactions.some((t) => t.id === body.id && owned(t.accountId)))
          fail('Transaction not found.');
      } else if (collection === 'budgets') {
        if (
          !text(body.category) ||
          !money(body.allocatedMinor) ||
          body.allocatedMinor === 0 ||
          !date(body.start) ||
          !date(body.end) ||
          body.start > body.end ||
          !Number.isFinite(Number(body.threshold)) ||
          Number(body.threshold) < 1 ||
          Number(body.threshold) > 100 ||
          (body.accountId && !owned(body.accountId))
        )
          fail('Enter a positive allocation, valid dates, and a warning threshold from 1 to 100.');
        target = s.budgets;
        value = {
          category: body.category.trim(),
          allocatedMinor: body.allocatedMinor,
          start: body.start,
          end: body.end,
          threshold: Number(body.threshold),
          accountId: body.accountId || '',
        };
      } else if (collection === 'investments') {
        if (
          !text(body.name) ||
          !['Money Market', 'Fixed Deposit', 'Treasury Bill', 'Other'].includes(body.type) ||
          !money(body.principalMinor) ||
          (body.currentValueMinor !== null && !money(body.currentValueMinor)) ||
          !date(body.valuationDate) ||
          body.valuationDate > today() ||
          (body.maturityDate && !date(body.maturityDate))
        )
          fail('Enter valid investment values and dates.');
        target = s.investments;
        value = {
          name: body.name.trim(),
          type: body.type,
          principalMinor: body.principalMinor,
          currentValueMinor: body.currentValueMinor,
          valuationDate: body.valuationDate,
          maturityDate: body.maturityDate || '',
        };
      } else fail('Unknown collection.');
      const index = body.id ? target.findIndex((r) => r.id === body.id) : -1;
      if (body.id && index < 0) fail('Record not found.');
      const record = { id: body.id || randomUUID(), ...value };
      if (index < 0) target.push(record);
      else target[index] = record;
      action = (index < 0 ? 'Added ' : 'Updated ') + collection + ' record';
    }
    if (action) s.audit.push({ at: new Date().toISOString(), action });
    return { ok: true };
  }
  return { snapshot, mutate };
}
