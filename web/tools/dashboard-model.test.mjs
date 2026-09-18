import { test } from 'node:test';import assert from 'node:assert/strict';
import { dashboardData,sampleRecords } from './dashboard-model.mjs';
const now=new Date('2026-09-14T12:00:00Z');const user={id:'seed-returning',name:'Test',kind:'individual'};
test('cash excludes credit debt and flow excludes pending/cancelled/credit purchases',()=>{const {accounts,transactions}=sampleRecords(user,now);const d=dashboardData(user,accounts,transactions,30,now);assert.equal(d.summary.availableCashMinor,28000000);assert.equal(d.summary.creditOutstandingMinor,1850000);assert.equal(d.summary.moneyInMinor,15425000);assert.equal(d.summary.moneyOutMinor,5100000);assert.equal(d.summary.netCashFlowMinor,10325000);assert.equal(d.cashFlow.reduce((s,p)=>s+p.moneyInMinor,0),d.summary.moneyInMinor);assert.equal(d.cashFlow.reduce((s,p)=>s+p.moneyOutMinor,0),d.summary.moneyOutMinor);});
test('90 days adds older transactions',()=>{const r=sampleRecords(user,now);const d=dashboardData(user,r.accounts,r.transactions,90,now);assert.equal(d.summary.moneyInMinor,17225000);assert.equal(d.summary.moneyOutMinor,5450000);});
test('empty user has zeros and cannot see another user records',()=>{const r=sampleRecords(user,now);const d=dashboardData({id:'other',name:'Empty',kind:'individual'},r.accounts,r.transactions,30,now);assert.equal(d.accounts.length,0);assert.equal(d.transactions.length,0);assert.equal(d.summary.availableCashMinor,0);});
test('out of range and failed rows excluded',()=>{const r=sampleRecords(user,now);r.transactions.push({...r.transactions[0],id:'future',date:'2026-10-01',amountMinor:99999999},{...r.transactions[0],id:'failed',status:'FAILED',amountMinor:99999999});const d=dashboardData(user,r.accounts,r.transactions,30,now);assert.equal(d.summary.moneyInMinor,15425000);});

test('bank filter scopes balances, flows and recent transactions before truncation',()=>{
 const r=sampleRecords(user,now);
 const equity=dashboardData(user,r.accounts,r.transactions,30,now,'Equity');
 assert.equal(equity.summary.availableCashMinor,9500000);
 assert.equal(equity.summary.moneyInMinor,125000);
 assert.equal(equity.summary.moneyOutMinor,0);
 assert.equal(equity.transactionCount,1);
 const credit=dashboardData(user,r.accounts,r.transactions,30,now,'NCBA');
 assert.equal(credit.summary.availableCashMinor,0);
 assert.equal(credit.summary.creditOutstandingMinor,1850000);
 assert.equal(credit.summary.moneyOutMinor,0);
 assert.equal(credit.transactionCount,1);
 const missing=dashboardData(user,r.accounts,r.transactions,90,now,'Stanbic');
 assert.equal(missing.accounts.length,0);assert.equal(missing.transactionCount,0);
 for(let i=0;i<20;i++)r.transactions.push({...r.transactions[0],id:'extra'+i});
 assert.equal(dashboardData(user,r.accounts,r.transactions,30,now,'Equity').transactionCount,1);
 assert.equal(dashboardData({id:'other'},r.accounts,r.transactions,30,now,'KCB').summary.availableCashMinor,0);
});

test('realistic personas cover banks, filters, dates and isolate their activity',()=>{
 for(const kind of ['individual','organization']){
 const u={id:'realistic-'+kind,kind,name:'Fictional test'};
 const r=sampleRecords(u,now);
 assert.equal(r.accounts.length,4);assert.ok(r.transactions.length>50);
 assert.deepEqual(new Set(r.accounts.map(a=>a.bank)),new Set(['KCB','Equity','Stanbic','NCBA']));
 for(const status of ['POSTED','PENDING','CANCELLED','FAILED'])assert.ok(r.transactions.some(t=>t.status===status));
 assert.ok(r.transactions.some(t=>t.date===now.toISOString().slice(0,10)));
 assert.ok(r.accounts.every(a=>a.availableBalanceMinor>=0&&a.creditOutstandingMinor>=0));
 const all=dashboardData(u,r.accounts,r.transactions,90,now);
 assert.equal(all.transactionCount,r.transactions.length);
 assert.equal(all.transactions.length,12);
 const scoped=['KCB','Equity','Stanbic','NCBA'].map(b=>dashboardData(u,r.accounts,r.transactions,90,now,b));
 assert.equal(scoped.reduce((s,d)=>s+d.summary.moneyInMinor,0),all.summary.moneyInMinor);
 assert.equal(scoped.reduce((s,d)=>s+d.summary.moneyOutMinor,0),all.summary.moneyOutMinor);
 assert.equal(dashboardData({id:'other'},r.accounts,r.transactions,90,now).transactionCount,0);
 }
});
