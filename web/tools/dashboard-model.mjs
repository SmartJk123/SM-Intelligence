export function dashboardData(user, accounts, transactions, days = 30, now = new Date()) {
  const to = now.toISOString().slice(0,10);
  const start = new Date(to+'T00:00:00Z'); start.setUTCDate(start.getUTCDate()-(days-1));
  const from = start.toISOString().slice(0,10);
  const owned = accounts.filter(a=>a.userId===user.id);
  const ids = new Set(owned.map(a=>a.id));
  const depositIds = new Set(owned.filter(a=>a.accountType==='DEPOSIT').map(a=>a.id));
  const recent = transactions.filter(t=>ids.has(t.accountId)&&t.date>=from&&t.date<=to).sort((a,b)=>b.date.localeCompare(a.date));
  const postedCash = recent.filter(t=>t.status==='POSTED'&&depositIds.has(t.accountId));
  const moneyIn = postedCash.filter(t=>t.direction==='CREDIT').reduce((s,t)=>s+t.amountMinor,0);
  const moneyOut = postedCash.filter(t=>t.direction==='DEBIT').reduce((s,t)=>s+t.amountMinor,0);
  const cashFlow = Array.from({length:6},(_,i)=>{const begin=new Date(start);begin.setUTCDate(begin.getUTCDate()+Math.floor(i*days/6));const end=new Date(start);end.setUTCDate(end.getUTCDate()+Math.floor((i+1)*days/6)-1);const a=begin.toISOString().slice(0,10),b=end.toISOString().slice(0,10);const rows=postedCash.filter(t=>t.date>=a&&t.date<=b);return {from:a,to:b,moneyInMinor:rows.filter(t=>t.direction==='CREDIT').reduce((s,t)=>s+t.amountMinor,0),moneyOutMinor:rows.filter(t=>t.direction==='DEBIT').reduce((s,t)=>s+t.amountMinor,0)};});
  return {source:'sample',currency:'KES',user:{name:user.name,kind:user.kind},period:{from,to,days},summary:{availableCashMinor:owned.filter(a=>a.accountType==='DEPOSIT').reduce((s,a)=>s+a.availableBalanceMinor,0),creditOutstandingMinor:owned.filter(a=>a.accountType==='CREDIT').reduce((s,a)=>s+a.creditOutstandingMinor,0),moneyInMinor:moneyIn,moneyOutMinor:moneyOut,netCashFlowMinor:moneyIn-moneyOut},accounts:owned.map(({userId,...a})=>a),cashFlow,transactions:recent.slice(0,12),transactionCount:recent.length};
}

export function sampleRecords(user, now=new Date()) {
  if(!['seed-returning','seed-business','seed-slow','seed-error'].includes(user.id))return {accounts:[],transactions:[]};
  const business=user.kind==='organization';const factor=business?8:1;
  const accounts=[{id:user.id+'-deposit',userId:user.id,bank:'KCB',accountName:business?'Business operating account':'Everyday account',maskedIdentifier:'•••• 4321',accountType:'DEPOSIT',availableBalanceMinor:18500000*factor,creditOutstandingMinor:0},{id:user.id+'-savings',userId:user.id,bank:'Equity',accountName:'Reserve savings',maskedIdentifier:'•••• 9072',accountType:'DEPOSIT',availableBalanceMinor:9500000*factor,creditOutstandingMinor:0},{id:user.id+'-credit',userId:user.id,bank:'NCBA',accountName:'Credit account',maskedIdentifier:'•••• 2184',accountType:'CREDIT',availableBalanceMinor:0,creditOutstandingMinor:1850000*factor}];
  const entries=[[2,'Salary / customer receipts','Income','CREDIT',12500000,'POSTED',0],[3,'Office and household supplies','Shopping','DEBIT',650000,'POSTED',0],[7,'Rent payment','Rent','DEBIT',3200000,'POSTED',0],[9,'Client payment','Income','CREDIT',2800000,'POSTED',0],[13,'Transport','Transport','DEBIT',450000,'POSTED',0],[18,'Utilities','Utilities','DEBIT',800000,'POSTED',0],[23,'Interest received','Interest','CREDIT',125000,'POSTED',1],[25,'Card purchase','Shopping','DEBIT',420000,'POSTED',2],[1,'Scheduled payment','Utilities','DEBIT',300000,'PENDING',0],[4,'Cancelled purchase','Shopping','DEBIT',250000,'CANCELLED',0],[42,'Earlier client payment','Income','CREDIT',1800000,'POSTED',0],[65,'Earlier supplies','Shopping','DEBIT',350000,'POSTED',0]];
  const transactions=entries.map(([ago,description,category,direction,amount,status,index],i)=>{const date=new Date(now);date.setUTCDate(date.getUTCDate()-ago);return {id:user.id+'-tx-'+i,accountId:accounts[index].id,description,category,direction,amountMinor:amount*factor,status,date:date.toISOString().slice(0,10)};});
  return {accounts,transactions};
}
