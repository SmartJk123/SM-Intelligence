// Fictional, deterministic activity. Relative dates keep timeline filters useful.
export function realisticRecords(user, now = new Date()) {
  const business = user.kind === 'organization', factor = business ? 6 : 1;
  const banks = ['KCB','Equity','Stanbic','NCBA'];
  const accounts = banks.map((bank,i)=>({id:user.id+'-bank-'+i,userId:user.id,bank,
    accountName:bank+' '+(i===3?'Credit card':i===1?'Savings':business?'Business account':'Everyday account'),
    maskedIdentifier:'•••• '+[4321,9072,6610,2184][i],accountType:i===3?'CREDIT':'DEPOSIT',
    availableBalanceMinor:0,creditOutstandingMinor:0}));
  const transactions=[];
  const add=(ago,account,category,direction,amount,status,description)=>{
    const date=new Date(now);date.setUTCDate(date.getUTCDate()-ago);
    transactions.push({id:user.id+'-activity-'+transactions.length,accountId:accounts[account].id,
      date:date.toISOString().slice(0,10),category,direction,amountMinor:amount*factor,status,description});
  };
  for(let ago=0;ago<90;ago++){
    if(ago%30===2)add(ago,0,'Income','CREDIT',14500000,'POSTED',business?'Monthly customer settlements':'Monthly salary');
    if(ago%30===5)add(ago,0,'Rent','DEBIT',3500000,'POSTED',business?'Premises rent':'Monthly rent');
    if(ago%30===8)add(ago,1,'Interest','CREDIT',85000,'POSTED','Savings interest');
    if(ago%7===0)add(ago,0,'Shopping','DEBIT',390000+(ago%4)*25000,'POSTED',business?'Inventory supplies':'Groceries and household supplies');
    if(ago%7===1)add(ago,2,'Transport','DEBIT',65000,'POSTED',business?'Delivery transport':'Commuting');
    if(ago%14===3)add(ago,2,'Utilities','DEBIT',180000,'POSTED',business?'Internet and electricity':'Internet and electricity');
    if(ago%15===4)add(ago,3,'Shopping','DEBIT',250000,'POSTED','Card purchase');
    if(business && ago%5===0)add(ago,2,'Income','CREDIT',1800000,'POSTED','Retail sales settlement');
  }
  add(0,2,'Income','CREDIT',250000,'POSTED',business?'Customer payment':'Freelance payment');
  add(1,0,'Utilities','DEBIT',240000,'PENDING','Scheduled electricity payment');
  add(0,1,'Income','CREDIT',420000,'PENDING','Incoming payment awaiting settlement');
  add(1,3,'Shopping','DEBIT',120000,'CANCELLED','Cancelled card order');
  add(2,2,'Transport','DEBIT',85000,'FAILED','Transport payment failed');
  // Closing snapshots reconcile to fictional opening balances plus posted activity.
  accounts.forEach((a,i)=>{
    const posted=transactions.filter(t=>t.accountId===a.id&&t.status==='POSTED');
    const net=posted.reduce((sum,t)=>sum+(t.direction==='CREDIT'?t.amountMinor:-t.amountMinor),0);
    if(i===3)a.creditOutstandingMinor=500000*factor-net;
    else a.availableBalanceMinor=[9000000,4000000,2800000][i]*factor+net;
  });
  return {accounts,transactions};
}
