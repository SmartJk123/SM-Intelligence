import test from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { startMockServer } from './mock-api.mjs';
test('hosted auth leads to empty onboarding, isolated workspace, logout and fresh restart', async () => {
 const calls=[];
 const fetchAuth=async (url,options) => {
  const body=JSON.parse(options.body);calls.push({url,body});
  if (body.password==='incorrect') return Response.json({}, {status:401});
  if (url.endsWith('/register')) return Response.json({id:'remote',passwordHash:'must-not-leak'}, {status:201});
  return Response.json({token:'header.'+Buffer.from(JSON.stringify({sub:body.email,exp:Date.now()/1000+60})).toString('base64url')+'.signature'});
 };
 const server=startMockServer(0,4200,{hostedAuth:true,fetchAuth});
 await once(server,'listening');
 const base='http://127.0.0.1:'+server.address().port;
 async function call(path,body,cookie) {
  const response=await fetch(base+path,{method:body ? 'POST':'GET',headers:{'Content-Type':'application/json',...(cookie?{Cookie:cookie}:{})},...(body?{body:JSON.stringify(body)}:{})});
  return {status:response.status,body:await response.json(),cookie:response.headers.get('set-cookie')?.split(';')[0]};
 }
 try {
  assert.equal((await call('/api/workspace')).status,401);
  const registration=await call('/api/auth/register',{name:'Fresh User',email:'fresh@example.invalid',phone:'+254700000000',password:'TestPassword123!',kind:'organization'});
  assert.deepEqual(registration.body,{registered:true});
  assert.equal(calls[0].body.phoneNumber,'+254700000000');
  assert.equal(registration.cookie,undefined);
  assert.equal((await call('/api/auth/login',{email:'fresh@example.invalid',password:'incorrect'})).status,401);
  const login=await call('/api/auth/login',{email:'fresh@example.invalid',password:'TestPassword123!'});
  assert.equal(login.body.user.kind,'organization');
  assert.equal(login.body.user.setupCompleted,false);
  const empty=(await call('/api/workspace',null,login.cookie)).body;
  for(const key of ['accounts','transactions','budgets','investments','read','audit']) assert.deepEqual(empty[key],[]);
  assert.equal(empty.source,'local');
  const setup=await call('/api/accounts',{bank:'KCB',accountName:'My account',accountNumber:'12345678',cardType:'debit',currency:'KES',balance:0,balanceDate:new Date().toISOString().slice(0,10)},login.cookie);
  assert.equal(setup.status,201);
  assert.equal((await call('/api/auth/session',null,login.cookie)).body.user.setupCompleted,true);
  const dashboard=(await call('/api/dashboard',null,login.cookie)).body;
  assert.equal(dashboard.accounts.length,1);
  assert.equal(dashboard.summary.availableCashMinor,0);
  const other=await call('/api/auth/login',{email:'other@example.invalid',password:'TestPassword123!'});
  assert.deepEqual((await call('/api/workspace',null,other.cookie)).body.accounts,[]);
  await call('/api/auth/logout',{},login.cookie);
  assert.equal((await call('/api/workspace',null,login.cookie)).status,401);
 } finally { server.closeAllConnections(); await new Promise(resolve=>server.close(resolve)); }
 const fresh=startMockServer(0,4200,{hostedAuth:true,fetchAuth});
 await once(fresh,'listening');
 try {
  const url='http://127.0.0.1:'+fresh.address().port;
  const login=await fetch(url+'/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:'fresh@example.invalid',password:'TestPassword123!'})});
  assert.equal((await login.json()).user.setupCompleted,false);
  const workspace=await fetch(url+'/api/workspace',{headers:{Cookie:login.headers.get('set-cookie').split(';')[0]}});
  assert.deepEqual((await workspace.json()).accounts,[]);
 } finally { fresh.closeAllConnections(); await new Promise(resolve=>fresh.close(resolve)); }
});
