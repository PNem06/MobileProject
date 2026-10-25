'use strict';
const {test}=require('node:test');const assert=require('node:assert/strict');
const {WalletService}=require('../wallet-service');const {FakeFirestore,Timestamp}=require('./fake-firestore');const core=require('../core');
function setup(bankCode='VPBank'){const db=new FakeFirestore();let now=Date.parse('2026-11-01T03:00:00Z');
 const cfg={adminUid:'admin',bankCode,bankAccount:'123456789',receiveAccount:'123456789',bankSubAccount:'',accountName:'ADMIN DEMO',topupMinutes:15};
 for(const uid of ['A','B','admin','C'])db.docs.set('accounts/'+uid,{accountId:uid,fullName:uid,status:'ACTIVE'});
 db.docs.set('posts/post1',{accountId:'B',title:'Căn hộ thử nghiệm',price:100000,status:'APPROVED',startDate:Timestamp.fromMillis(now+2*86400000),endDate:Timestamp.fromMillis(now+4*86400000)});
 const service=new WalletService(db,Timestamp,cfg,()=>now);
 const actor=uid=>({uid,accountId:uid,name:uid,isAdmin:uid==='admin'});
 let sequence=1;
 const op=()=>('operation_'+(sequence++)).padEnd(20,'0');
 const date=ms=>new Date(ms+7*3600000).toISOString().slice(0,19).replace('T',' ');
 const event=(t,changes={})=>({id:sequence++,gateway:cfg.bankCode,accountNumber:'123456789',subAccount:'',transferType:'in',transferAmount:t.amount,content:t.topupId,transactionDate:date(now),referenceCode:'FTTEST'+sequence,...changes});
 async function deposit(uid='A',amount=100000){const t=await service.createTopup(actor(uid),{amount,operationId:op()});await service.bankEvent(event(t));return t;}
 async function pay(){return service.pay(actor('A'),{postId:'post1',expectedAmount:100000,operationId:op()});}
 const balance=uid=>db.docs.get('wallets/'+uid)||{available:0,held:0};
 function conservation(){let sum=0;for(const[k,v]of db.docs)if(k.startsWith('wallets/')){assert(v.available>=0&&v.held>=0);sum+=v.available+v.held;}let credits=0;for(const[k,v]of db.docs)if(k.startsWith('walletLedger/topup_'))credits+=v.amount;assert.equal(sum,credits,'Wallet liabilities must equal verified external topups');}
 return {db,service,actor,cfg,op,event,deposit,pay,balance,conservation,now:()=>now,setNow:n=>{now=n;},advance:n=>{now+=n;}};
}
test('topup credits exactly once across duplicate delivery and a distinct extra payment',async()=>{const h=setup();const t=await h.service.createTopup(h.actor('A'),{amount:100000,operationId:h.op()});const e=h.event(t);await Promise.all([h.service.bankEvent(e),h.service.bankEvent(e)]);assert.equal(h.balance('A').available,100000);await h.service.bankEvent(h.event(t));assert.equal(h.balance('A').available,100000);assert([...h.db.docs.values()].some(x=>x.result==='EXTRA_PAYMENT'));h.conservation();});
test('create topup retries and two taps recover same pending code',async()=>{const h=setup(),operationId=h.op();const [a,b]=await Promise.all([h.service.createTopup(h.actor('A'),{amount:100000,operationId}),h.service.createTopup(h.actor('A'),{amount:100000,operationId:h.op()})]);assert.equal(a.topupId,b.topupId);const retry=await h.service.createTopup(h.actor('A'),{amount:100000,operationId});assert.equal(a.topupId,retry.topupId);});
test('invalid bank/account/direction/VA never credits',async()=>{const h=setup();h.cfg.bankSubAccount='VA1234';const t=await h.service.createTopup(h.actor('A'),{amount:100000,operationId:h.op()});for(const patch of [{gateway:'MBBank'},{accountNumber:'987654321'},{transferType:'out'}])await h.service.bankEvent(h.event(t,patch));assert.equal(h.balance('A').available,0);await h.service.bankEvent(h.event(t,{subAccount:'OTHER'}));assert.equal(h.balance('A').available,0);await h.service.bankEvent(h.event(t,{subAccount:'VA1234'}));assert.equal(h.balance('A').available,100000);h.conservation();});
test('wrong amount, expired, malformed date, future bank date require review',async()=>{for(const patch of [{transferAmount:99999},{transferAmount:100001},{transactionDate:'2026-11-01 11:00:00'},{transactionDate:'bad'},{transactionDate:'2030-01-01 00:00:00'}]){const h=setup(),t=await h.service.createTopup(h.actor('A'),{amount:100000,operationId:h.op()});await h.service.bankEvent(h.event(t,patch));assert.equal(h.db.docs.get('walletTopups/'+t.topupId).status,'REVIEW');assert.equal(h.balance('A').available,0);}});
test('unknown or ambiguous reference is unmatched; substring is not a code',async()=>{const h=setup(),t=await h.service.createTopup(h.actor('A'),{amount:100000,operationId:h.op()});for(const content of ['No code',t.topupId+'A',t.topupId+' NAP0123456789ABCDEF'])await h.service.bankEvent(h.event(t,{content}));assert.equal(h.balance('A').available,0);});
test('payment debits A, holds admin, and uses exchanges schema of current app',async()=>{const h=setup();await h.deposit();const b=await h.pay();assert.equal(h.balance('A').available,0);assert.equal(h.balance('admin').held,100000);assert.equal(b.requester_accountId,'A');assert.equal(b.requesterId,'A');assert.equal(b.host_accountId,'B');assert.equal(b.exchangeType,'NON_RECIPROCAL');assert.equal(b.walletManaged,true);assert.equal(b.paymentStatus,'PAID');assert.equal(b.status,'PENDING');h.conservation();});
test('concurrent payment attempts with same or different operation IDs cannot double charge',async()=>{const h=setup();await h.deposit('A',200000);const [a,b]=await Promise.all([h.pay(),h.pay()]);assert.equal(a.exchangeId,b.exchangeId);assert.equal(h.balance('A').available,100000);assert.equal(h.balance('admin').held,100000);h.conservation();});
test('insufficient funds and forged client price do not create partial writes',async()=>{const h=setup();await assert.rejects(h.pay());await h.deposit();await assert.rejects(h.service.pay(h.actor('A'),{postId:'post1',expectedAmount:1,operationId:h.op()}));assert.equal(h.balance('A').available,100000);assert(![...h.db.docs.keys()].some(k=>k.startsWith('exchanges/')));h.conservation();});
test('unapproved post, own post, wrong dates and missing post are rejected',async()=>{for(const change of [{status:'PENDING'},{accountId:'A'},{price:100.5},{startDate:Timestamp.fromMillis(1)},null]){const h=setup();await h.deposit();if(change===null)h.db.docs.delete('posts/post1');else Object.assign(h.db.docs.get('posts/post1'),change);await assert.rejects(h.pay());assert.equal(h.balance('A').available,100000);}});
test('B rejection refunds 100% even after A cancellation deadline',async()=>{const h=setup();await h.deposit();const b=await h.pay();h.advance(2*86400000);await h.service.action(h.actor('B'),{exchangeId:b.exchangeId,action:'REJECT'});assert.equal(h.balance('A').available,100000);assert.equal(h.balance('admin').held,0);assert.equal(h.balance('admin').available,0);h.conservation();});
test('A cancels pending request -> A80k, admin20k; duplicate cancel is idempotent',async()=>{const h=setup();await h.deposit();const b=await h.pay();const input={exchangeId:b.exchangeId,action:'CANCEL'};await h.service.action(h.actor('A'),input);await h.service.action(h.actor('A'),input);assert.equal(h.balance('A').available,80000);assert.equal(h.balance('admin').available,20000);assert.equal(h.balance('admin').held,0);h.conservation();});
test('B acceptance does not release money or remove A right to cancel within24h',async()=>{const h=setup();await h.deposit();const b=await h.pay();await h.service.action(h.actor('B'),{exchangeId:b.exchangeId,action:'ACCEPT'});assert.equal(h.balance('B').available,0);h.advance(23*3600000);await h.service.action(h.actor('A'),{exchangeId:b.exchangeId,action:'CANCEL'});assert.equal(h.balance('A').available,80000);assert.equal(h.balance('B').available,0);h.conservation();});
test('at exact24h and later A cannot cancel; immediately before24h A can',async()=>{for(const delta of [86400000,86400001]){const h=setup();await h.deposit();const b=await h.pay();h.advance(delta);await assert.rejects(h.service.action(h.actor('A'),{exchangeId:b.exchangeId,action:'CANCEL'}));assert.equal(h.balance('admin').held,100000);}const h=setup();await h.deposit();const b=await h.pay();h.advance(86400000-1);await h.service.action(h.actor('A'),{exchangeId:b.exchangeId,action:'CANCEL'});h.conservation();});
test('only B accepts/rejects; only A cancels; no client can set COMPLETED',async()=>{const h=setup();await h.deposit();const b=await h.pay();for(const [uid,action]of [['C','ACCEPT'],['A','REJECT'],['B','CANCEL'],['admin','CANCEL'],['A','COMPLETE']])await assert.rejects(h.service.action(h.actor(uid),{exchangeId:b.exchangeId,action}));assert.equal(h.balance('admin').held,100000);h.conservation();});
test('scheduled completion releases once after end, even with multiple workers',async()=>{const h=setup();await h.deposit();const b=await h.pay();await h.service.action(h.actor('B'),{exchangeId:b.exchangeId,action:'ACCEPT'});h.setNow(b.endDate.toMillis()-1);assert.equal(await h.service.settle(b.exchangeId),false);h.advance(1);const result=await Promise.all([h.service.settle(b.exchangeId),h.service.settle(b.exchangeId)]);assert.equal(result.filter(Boolean).length,1);assert.equal(h.balance('B').available,100000);assert.equal(h.balance('admin').held,0);assert.equal(h.db.docs.get('exchanges/'+b.exchangeId).status,'COMPLETED');h.conservation();});
test('pending request never pays B automatically; cancelled/rejected never release',async()=>{for(const action of ['NONE','CANCEL','REJECT']){const h=setup();await h.deposit();const b=await h.pay();if(action!=='NONE')await h.service.action(h.actor(action==='CANCEL'?'A':'B'),{exchangeId:b.exchangeId,action});h.advance(10*86400000);assert.equal(await h.service.settle(b.exchangeId),false);h.conservation();}});
test('short trip cannot release before cancellation deadline',async()=>{const h=setup();const p=h.db.docs.get('posts/post1');p.startDate=Timestamp.fromMillis(h.now()+3600000);p.endDate=Timestamp.fromMillis(h.now()+2*3600000);await h.deposit();const b=await h.pay();await h.service.action(h.actor('B'),{exchangeId:b.exchangeId,action:'ACCEPT'});h.advance(3*3600000);assert.equal(await h.service.settle(b.exchangeId),false);h.advance(21*3600000);assert.equal(await h.service.settle(b.exchangeId),true);h.conservation();});
test('racing reject/cancel causes one terminal decision and preserves funds',async()=>{const h=setup();await h.deposit();const b=await h.pay();await Promise.allSettled([h.service.action(h.actor('A'),{exchangeId:b.exchangeId,action:'CANCEL'}),h.service.action(h.actor('B'),{exchangeId:b.exchangeId,action:'REJECT'})]);assert.equal(h.balance('admin').held,0);assert([80000,100000].includes(h.balance('A').available));h.conservation();});
test('bank notification delayed after expiry still credits if bank transfer time was within deadline',async()=>{const h=setup(),t=await h.service.createTopup(h.actor('A'),{amount:100000,operationId:h.op()});const e=h.event(t);h.advance(3600000);await h.service.bankEvent(e);assert.equal(h.balance('A').available,100000);h.conservation();});
test('legacy unpaid MONEY exchange is upgraded in place rather than creating a duplicate',async()=>{const h=setup();await h.deposit();h.db.docs.set('exchanges/legacy',{postId:'post1',requester_accountId:'A',host_accountId:'B',exchangeType:'NON_RECIPROCAL',status:'PENDING',createdAt:Timestamp.fromMillis(h.now())});const result=await h.service.pay(h.actor('A'),{postId:'post1',exchangeId:'legacy',expectedAmount:100000,operationId:h.op()});assert.equal(result.exchangeId,'legacy');assert.equal(h.db.docs.get('exchanges/legacy').walletManaged,true);h.conservation();});
test('operation retry recovers paid request even if original post is subsequently removed',async()=>{const h=setup();await h.deposit();const input={postId:'post1',expectedAmount:100000,operationId:h.op()};const b=await h.service.pay(h.actor('A'),input);h.db.docs.delete('posts/post1');assert.equal((await h.service.pay(h.actor('A'),input)).exchangeId,b.exchangeId);h.conservation();});
test('alias account IDs match Request screen while calendar UID aliases remain correct',async()=>{const h=setup();h.db.docs.get('accounts/A').accountId='account_A';h.db.docs.get('accounts/B').accountId='account_B';h.db.docs.get('posts/post1').accountId='account_B';const actor=await h.service.actor('A');await h.deposit();const b=await h.service.pay(actor,{postId:'post1',expectedAmount:100000,operationId:h.op()});assert.equal(b.requester_accountId,'account_A');assert.equal(b.requesterId,'A');assert.equal(b.host_accountId,'account_B');assert.equal(b.hostId,'B');h.conservation();});
test('settlement backlog job handles accepted requests after restart',async()=>{const h=setup();await h.deposit();const b=await h.pay();await h.service.action(h.actor('B'),{exchangeId:b.exchangeId,action:'ACCEPT'});h.advance(9*86400000);assert.equal(await h.service.settleDue(),1);assert.equal(await h.service.settleDue(),0);h.conservation();});
test('secret validation and malformed events reject before money changes',()=>{assert(core.authorization('Apikey '+'x'.repeat(40),'x'.repeat(40)));assert(!core.authorization('Apikey wrong','x'.repeat(40)));assert(!core.authorization('Apikey ',''));for(const b of [null,{id:-1,accountNumber:'1',transferAmount:1},{id:1,accountNumber:'1',transferAmount:-1},{id:1,accountNumber:'1',transferAmount:1.5}])assert.throws(()=>core.parseEvent(b));});
test('concurrent payments to different posts cannot overspend available balance',async()=>{const h=setup();await h.deposit();h.db.docs.set('posts/post2',{...h.db.docs.get('posts/post1'),title:'Chỗ ở thứ hai'});const results=await Promise.allSettled([h.pay(),h.service.pay(h.actor('A'),{postId:'post2',expectedAmount:100000,operationId:h.op()})]);assert.equal(results.filter(x=>x.status==='fulfilled').length,1);assert.equal(h.balance('A').available,0);assert.equal(h.balance('admin').held,100000);h.conservation();});
test('fee rounds to nearest VND and total balances still match cash received',async()=>{const h=setup();h.db.docs.get('posts/post1').price=1001;await h.deposit('A',1001);const b=await h.service.pay(h.actor('A'),{postId:'post1',expectedAmount:1001,operationId:h.op()});await h.service.action(h.actor('A'),{exchangeId:b.exchangeId,action:'CANCEL'});assert.equal(h.balance('A').available,801);assert.equal(h.balance('admin').available,200);h.conservation();});
test('wallet snapshot exposes only A related bookings/transactions; admin view has held amounts',async()=>{const h=setup();await h.deposit();const b=await h.pay();const a=await h.service.snapshot(h.actor('A'));const other=await h.service.snapshot(h.actor('C'));const admin=await h.service.snapshot(h.actor('admin'));assert.equal(a.bookings[0].exchangeId,b.exchangeId);assert.equal(other.bookings.length,0);assert.equal(other.ledger.length,0);assert.equal(admin.wallet.held,100000);assert.equal(a.bookings[0].paidAt,h.now());assert.equal(a.isAdmin,false);});
test('MB QR and webhook credit once; a VPBank event cannot credit an MB topup',async()=>{
 const h=setup('MBBank'),t=await h.service.createTopup(h.actor('A'),{amount:100000,operationId:h.op()});
 assert.equal(t.bank,'MBBank');assert.equal(new URL(t.qrUrl).searchParams.get('bank'),'MBBank');
 assert.equal(new URL(t.qrUrl).searchParams.get('acc'),h.cfg.receiveAccount);
 assert.equal((await h.service.bankEvent(h.event(t,{gateway:'VPBank'}))).ignored,true);
 assert.equal(h.balance('A').available,0);const event=h.event(t);await h.service.bankEvent(event);await h.service.bankEvent(event);
 assert.equal(h.balance('A').available,100000);h.conservation();
});
test('MB gateway aliases map to the same configured bank and reject unrelated banks',async()=>{
 for(const gateway of ['MBBank','MB','970422']){
  const h=setup('MBBank'),t=await h.service.createTopup(h.actor('A'),{amount:1000,operationId:h.op()});
  await h.service.bankEvent(h.event(t,{gateway:'Vietcombank'}));assert.equal(h.balance('A').available,0);
  await h.service.bankEvent(h.event(t,{gateway}));assert.equal(h.balance('A').available,1000);h.conservation();
 }
});
test('switching VPBank to MB with the same account number never reuses or credits an old pending QR',async()=>{
 const h=setup(),operationId=h.op();const old=await h.service.createTopup(h.actor('A'),{amount:100000,operationId});
 h.cfg.bankCode='MBBank';
 await assert.rejects(h.service.createTopup(h.actor('A'),{amount:100000,operationId}),e=>e.status===409);
 const fresh=await h.service.createTopup(h.actor('A'),{amount:100000,operationId:h.op()});assert.notEqual(fresh.topupId,old.topupId);
 assert.equal((await h.service.bankEvent(h.event(old))).result,'BANK_MISMATCH');assert.equal(h.balance('A').available,0);
 assert.equal(h.db.docs.get('walletTopups/'+old.topupId).status,'PENDING');
 await h.service.bankEvent(h.event(fresh));assert.equal(h.balance('A').available,100000);h.conservation();
});
test('changing the receiving account or VA invalidates a pending configuration without changing old records',async()=>{
 for(const change of [{receiveAccount:'987654321'},{bankSubAccount:'VA12345'}]){
  const h=setup('MBBank'),old=await h.service.createTopup(h.actor('A'),{amount:1000,operationId:h.op()});
  Object.assign(h.cfg,change);const fresh=await h.service.createTopup(h.actor('A'),{amount:1000,operationId:h.op()});
  assert.notEqual(old.topupId,fresh.topupId);assert.notEqual(old.bankConfigId,fresh.bankConfigId);
  assert.equal((await h.service.bankEvent(h.event(old,{subAccount:h.cfg.bankSubAccount}))).result,'BANK_MISMATCH');
  assert.equal(h.balance('A').available,0);h.conservation();
 }
});
test('legacy VPBank topups stay readable and valid for the same bank; paid history survives a bank switch',async()=>{
 const h=setup(),operationId=h.op(),t=await h.service.createTopup(h.actor('A'),{amount:100000,operationId});
 const legacy=h.db.docs.get('walletTopups/'+t.topupId);delete legacy.bankCode;delete legacy.bankConfigId;
 await h.service.bankEvent(h.event(t));assert.equal(h.balance('A').available,100000);
 h.cfg.bankCode='MBBank';const recovered=await h.service.createTopup(h.actor('A'),{amount:100000,operationId});
 assert.equal(recovered.status,'PAID');assert.equal(recovered.topupId,t.topupId);h.conservation();
});
test('unknown bank configuration fails without issuing a QR or changing funds',async()=>{
 const h=setup('UNSUPPORTED');await assert.rejects(h.service.createTopup(h.actor('A'),{amount:1000,operationId:h.op()}));
 assert(![...h.db.docs.keys()].some(k=>k.startsWith('walletTopups/')));assert.equal(h.balance('A').available,0);
});
test('snapshot tells Android the configured bank and receiving account without exposing webhook credentials',async()=>{
 const h=setup('MBBank'),s=await h.service.snapshot(h.actor('A'));
 assert.equal(s.bank.code,'MBBank');assert.equal(s.bank.appPackage,'com.mbmobile');assert.equal(s.bank.accountNumber,h.cfg.receiveAccount);
 assert.equal(s.bank.configurationId.length,64);assert(!JSON.stringify(s).includes('SEPAY_WEBHOOK_KEY'));
});

test('a pending 100k request cannot silently replace a newly entered 10k amount',async()=>{
 const h=setup('MBBank');const original=await h.service.createTopup(h.actor('A'),{amount:100000,operationId:h.op()});
 await assert.rejects(h.service.createTopup(h.actor('A'),{amount:10000,operationId:h.op()}),e=>e.status===409);
 assert.equal(h.db.docs.get('walletTopups/'+original.topupId).amount,100000);
 assert.equal([...h.db.docs.keys()].filter(k=>k.startsWith('walletTopups/')).length,1);h.conservation();
});
test('topup operation ID cannot be replayed with a different amount',async()=>{
 const h=setup(),operationId=h.op();await h.service.createTopup(h.actor('A'),{amount:100000,operationId});
 await assert.rejects(h.service.createTopup(h.actor('A'),{amount:10000,operationId}),e=>e.status===409);
});
test('owner can cancel unpaid pending topup without changing balances or ledger',async()=>{
 const h=setup(),t=await h.service.createTopup(h.actor('A'),{amount:10000,operationId:h.op()});
 const cancelled=await h.service.cancelTopup(h.actor('A'),{topupId:t.topupId,confirmUnpaid:true});
 assert.equal(cancelled.status,'CANCELLED');assert.equal(cancelled.cancelledAt.toMillis(),h.now());
 assert.equal(h.balance('A').available,0);assert.equal([...h.db.docs.keys()].filter(k=>k.startsWith('walletLedger/')).length,0);h.conservation();
});
test('another user and the admin client cannot cancel A topup',async()=>{
 const h=setup(),t=await h.service.createTopup(h.actor('A'),{amount:10000,operationId:h.op()});
 for(const uid of ['B','admin'])await assert.rejects(h.service.cancelTopup(h.actor(uid),{topupId:t.topupId,confirmUnpaid:true}),e=>e.status===403);
 assert.equal(h.db.docs.get('walletTopups/'+t.topupId).status,'PENDING');
});
test('cancellation requires explicit unpaid confirmation and an existing topup',async()=>{
 const h=setup(),t=await h.service.createTopup(h.actor('A'),{amount:10000,operationId:h.op()});
 for(const confirmUnpaid of [undefined,false,'true'])await assert.rejects(h.service.cancelTopup(h.actor('A'),{topupId:t.topupId,confirmUnpaid}),e=>e.status===400);
 await assert.rejects(h.service.cancelTopup(h.actor('A'),{topupId:'NAP0000000000000000',confirmUnpaid:true}),e=>e.status===404);
});
test('duplicate cancellation is idempotent',async()=>{
 const h=setup(),t=await h.service.createTopup(h.actor('A'),{amount:10000,operationId:h.op()});const input={topupId:t.topupId,confirmUnpaid:true};
 const both=await Promise.all([h.service.cancelTopup(h.actor('A'),input),h.service.cancelTopup(h.actor('A'),input)]);
 assert(both.every(x=>x.status==='CANCELLED'));assert.equal(h.balance('A').available,0);h.conservation();
});
test('after cancellation a new amount gets a new QR; the old operation remains cancelled',async()=>{
 const h=setup(),operationId=h.op(),old=await h.service.createTopup(h.actor('A'),{amount:100000,operationId});
 await h.service.cancelTopup(h.actor('A'),{topupId:old.topupId,confirmUnpaid:true});
 const next=await h.service.createTopup(h.actor('A'),{amount:10000,operationId:h.op()});
 assert.notEqual(next.topupId,old.topupId);assert.equal(next.amount,10000);assert.equal(new URL(next.qrUrl).searchParams.get('amount'),'10000');
 assert.equal((await h.service.createTopup(h.actor('A'),{amount:100000,operationId})).status,'CANCELLED');
});
test('a paid topup cannot be cancelled and credited money is preserved',async()=>{
 const h=setup(),t=await h.deposit('A',10000);
 await assert.rejects(h.service.cancelTopup(h.actor('A'),{topupId:t.topupId,confirmUnpaid:true}),e=>e.status===409);
 assert.equal(h.balance('A').available,10000);h.conservation();
});
test('a topup requiring reconciliation cannot be cancelled or erased',async()=>{
 const h=setup(),t=await h.service.createTopup(h.actor('A'),{amount:100000,operationId:h.op()});
 await h.service.bankEvent(h.event(t,{transferAmount:10000}));
 await assert.rejects(h.service.cancelTopup(h.actor('A'),{topupId:t.topupId,confirmUnpaid:true}),e=>e.status===409);
 assert.equal(h.db.docs.get('walletTopups/'+t.topupId).status,'REVIEW');assert.equal(h.balance('A').available,0);
});
test('money arriving for a cancelled QR is retained for review and never silently credited',async()=>{
 const h=setup(),t=await h.service.createTopup(h.actor('A'),{amount:10000,operationId:h.op()});
 await h.service.cancelTopup(h.actor('A'),{topupId:t.topupId,confirmUnpaid:true});
 const event=h.event(t);assert.equal((await h.service.bankEvent(event)).result,'REVIEW');
 const current=h.db.docs.get('walletTopups/'+t.topupId);assert.equal(current.reviewCode,'CANCELLED_TOPUP');
 assert.equal(current.status,'REVIEW');assert.equal(h.balance('A').available,0);
 await h.service.bankEvent(event);assert.equal([...h.db.docs.keys()].filter(k=>k.startsWith('walletBankEvents/')).length,1);h.conservation();
});
test('webhook then cancel and cancel then webhook both preserve state and funds',async()=>{
 for(const bankFirst of [true,false]){
  const h=setup(),t=await h.service.createTopup(h.actor('A'),{amount:10000,operationId:h.op()}),event=h.event(t);
  const cancel=()=>h.service.cancelTopup(h.actor('A'),{topupId:t.topupId,confirmUnpaid:true});const receive=()=>h.service.bankEvent(event);
  await Promise.allSettled(bankFirst?[receive(),cancel()]:[cancel(),receive()]);
  assert.equal(h.balance('A').available,bankFirst?10000:0);assert.equal(h.db.docs.get('walletTopups/'+t.topupId).status,bankFirst?'PAID':'REVIEW');h.conservation();
 }
});
test('expired unpaid pending request can be marked cancelled',async()=>{
 const h=setup(),t=await h.service.createTopup(h.actor('A'),{amount:10000,operationId:h.op()});h.advance(20*60000);
 assert.equal((await h.service.cancelTopup(h.actor('A'),{topupId:t.topupId,confirmUnpaid:true})).status,'CANCELLED');h.conservation();
});
test('two 10k transfers for a 100k request remain separately traceable for review',async()=>{
 const h=setup(),t=await h.service.createTopup(h.actor('A'),{amount:100000,operationId:h.op()});
 await h.service.bankEvent(h.event(t,{transferAmount:10000}));await h.service.bankEvent(h.event(t,{transferAmount:10000}));
 assert.equal(h.db.docs.get('walletTopups/'+t.topupId).reviewCode,'AMOUNT_MISMATCH');
 const events=[...h.db.docs.entries()].filter(([k])=>k.startsWith('walletBankEvents/'));
 assert.equal(events.length,2);assert.equal(events.reduce((n,[,v])=>n+v.amount,0),20000);
 assert.equal(h.balance('A').available,0);h.conservation();
});
test('new snapshot exposes cancelled request history and API version 3',async()=>{
 const h=setup(),t=await h.service.createTopup(h.actor('A'),{amount:10000,operationId:h.op()});
 await h.service.cancelTopup(h.actor('A'),{topupId:t.topupId,confirmUnpaid:true});const snapshot=await h.service.snapshot(h.actor('A'));
 assert.equal(snapshot.apiVersion,3);assert.equal(snapshot.topups[0].status,'CANCELLED');assert.equal(snapshot.wallet.available,0);
});
test('rental quote exposes valid 1000 VND price and rejects a past start date clearly',async()=>{
 const h=setup('MBBank');h.db.docs.get('posts/post1').price=1000;
 const quote=await h.service.quote(h.actor('A'),'post1');assert.equal(quote.amount,1000);assert.equal(quote.hostUid,'B');
 h.db.docs.get('posts/post1').startDate=Timestamp.fromMillis(h.now()-86400000);
 await assert.rejects(h.service.quote(h.actor('A'),'post1'),e=>e.status===409&&e.message.includes('Ngày bắt đầu/kết thúc'));
});
