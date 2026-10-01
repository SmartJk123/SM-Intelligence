const fs=require('fs');
const dir='C:/Users/admin/.codex/visualizations/2026/09/17/01a0ae86-0f4a-7043-8736-aae1eb869c6c/';
let s=fs.readFileSync(dir+'smartmoney-bank-swipe.html','utf8');
// Update the final carousel controller, leaving earlier construction stages intact.
s=s.replace('>1 · Total cash</button>','>Total</button>').replace('>2 · KCB</button>','>Specified bank</button>');
s=s.replace("let selected='KCB',active=0,hidden=false;","let selected=null,active=0,hidden=false;");
s=s.replace("paintAmounts(bankCard,amounts[selected]);bankCard.querySelector('.scope-badge').textContent=selected;bankCard.querySelector('.balance-period').textContent=selected+' account · This month';buttons[1].textContent='2 · '+selected;", "if(selected){paintAmounts(bankCard,amounts[selected])}else{bankCard.querySelector('.balance-value h1>span:last-child').textContent='—';bankCard.querySelectorAll('.balance-flows strong').forEach(n=>n.textContent='—')}bankCard.querySelector('.scope-badge').textContent=selected||'Specified bank';bankCard.querySelector('.balance-period').textContent=selected?selected+' account · This month':'Choose a bank using the arrow below';buttons[1].textContent=selected||'Specified bank';");
s=s.replace("toggle.querySelector('span').textContent=active?selected:'All banks';", "toggle.querySelector('span').textContent='';toggle.setAttribute('aria-label','Filter by bank'+(selected?' · '+selected:''));");
s=s.replace("+'<button class=\"sm-full\" data-go=\"Transfer\">Transfer money</button>'", "+'<p>Account activity is tracked from your linked bank.</p>'");
// Remove the page-level money movement routes rather than leaving hidden flows available.
s=s.replace(/else if\(\['Transfer','Add money','Cash out'\]\.includes\(name\)\)[\s\S]*?(?=else if\(name\.startsWith\('Transaction'\)\))/, '');
s=s.replace('Recent transactions</h2>','Recent bank activity</h2>');
s=s.replace('<p>Recent activity · Sample data</p>','<p>Linked bank activity · Sample data</p>');
s=s.replace("html='<div class=\"sm-filter\">'", "html='<p>Activity from your linked banks · Sample data</p><div class=\"sm-filter\">'");
s+=fs.readFileSync('monitoring-update.html','utf8');
fs.writeFileSync(dir+'smartmoney-monitoring.html',s);
