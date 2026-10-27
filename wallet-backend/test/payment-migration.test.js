'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const {FakeFirestore,Timestamp}=require('./fake-firestore');
const {WalletService}=require('../wallet-service');
const {CONTROL_PATH,physicalPath,PaymentDatabase}=require('../payment-storage');
const {inspect,applyMigration,pruneLegacy,cancelMigration,options,hash}=require('../payment-migration');
const PROJECT='demo-payments-migration';

function setup(){
 const db=new FakeFirestore();let now=Date.parse('2026-11-01T03:00:00Z'),sequence=100;
 const cfg={projectId:PROJECT,adminUid:'admin',bankCode:'MBBank',bankAccount:'123456789',receiveAccount:'123456789',accountName:'ADMIN',topupMinutes:15};
 for(const uid of ['A','B','C','admin'])db.docs.set('accounts/'+uid,{accountId:uid,status:'ACTIVE',role:uid==='admin'?'ADMIN':'USER'});
 db.docs.set('posts/room',{accountId:'B',status:'APPROVED',price:100000,title:'Room',startDate:Timestamp.fromMillis(now+86400000),endDate:Timestamp.fromMillis(now+3*86400000)});
 const service=new WalletService(db,Timestamp,cfg,()=>now),actor=uid=>({uid,accountId:uid,name:uid,isAdmin:uid==='admin'});
 const op=()=>('test-operation-'+(++sequence)).padEnd(24,'0');
 const event=topup=>({id:++sequence,gateway:'MBBank',accountNumber:'123456789',transferType:'in',transferAmount:topup.amount,content:topup.topupId,transactionDate:new Date(now+7*3600000).toISOString().slice(0,19).replace('T',' '),referenceCode:'FT'+sequence});
 const balance=uid=>db.docs.get('wallets/'+uid)||{available:0,held:0};
 let backup;
 const migrate=()=>applyMigration(db,{projectId:PROJECT,saveBackup:async value=>{backup=JSON.parse(JSON.stringify(value));}});
 const prune=()=>pruneLegacy(db,{projectId:PROJECT,backup});
 const pending=(amount=100000)=>service.createTopup(actor('A'),{amount,operationId:op()});
 const deposit=async(amount=100000)=>{const topup=await pending(amount),bank=event(topup);await service.bankEvent(bank);return {topup,bank};};
 const pay=()=>service.pay(actor('A'),{postId:'room',expectedAmount:100000,operationId:op()});
 const conservation=()=>{const ledger=[...db.docs].filter(([p])=>p.startsWith('payments/_system/ledger/'));const credited=ledger.filter(([,d])=>d.topupId&&d.amount>0).reduce((s,[,d])=>s+d.amount,0);const total=[...db.docs].filter(([p])=>p.startsWith('wallets/')).reduce((s,[,w])=>s+w.available+w.held,0);assert.equal(total,credited);};
 return {db,service,actor,op,event,balance,migrate,prune,pending,deposit,pay,conservation,backup:()=>backup,advance:ms=>{now+=ms;}};
}
test('preview is read-only and maps six legacy collections into payments without moving balances',async()=>{
 const h=setup();await h.deposit();await h.pay();const before=hash([...h.db.docs]);
 const result=await inspect(h.db);assert.equal(result.mode,'PREVIEW');assert.equal(result.availableTotal,0);assert.equal(result.heldTotal,100000);assert.equal(result.rentalPayments,1);
 assert.equal(hash([...h.db.docs]),before);assert(!h.db.docs.has(CONTROL_PATH));
});
test('migration copies complete records and leaves balances, exchanges, posts and notifications untouched',async()=>{
 const h=setup();const {topup}=await h.deposit();const booking=await h.pay();
 const stable=[...h.db.docs].filter(([p])=>/^(wallets|exchanges|posts|notifications)\//.test(p));
 const result=await h.migrate();assert.equal(result.storageSchema,2);assert.equal(result.balancesChanged,false);
 for(const [p,d]of stable)assert.equal(hash(h.db.docs.get(p)),hash(d));
 assert.equal(h.db.docs.get('payments/'+topup.topupId).recordType,'TOPUP');
 assert.equal(h.db.docs.get('payments/rental_'+booking.exchangeId).status,'PAID');
 for(const entry of h.backup().entries)assert(h.db.docs.has(entry.targetPath));
 assert.equal(h.db.docs.get(CONTROL_PATH).mode,'READY');h.conservation();
});
test('pending topup keeps its QR, operation retry and single-request lock after activation',async()=>{
 const h=setup(),operationId=h.op();const old=await h.service.createTopup(h.actor('A'),{amount:100000,operationId});
 await h.migrate();const retry=await h.service.createTopup(h.actor('A'),{amount:100000,operationId});
 assert.equal(retry.topupId,old.topupId);assert.equal(retry.qrUrl,old.qrUrl);
 assert.equal((await h.pending()).topupId,old.topupId);await assert.rejects(h.pending(10000),e=>e.status===409);
 await h.service.bankEvent(h.event(old));assert.equal(h.balance('A').available,100000);h.conservation();
});
test('webhook replay after migration and pruning cannot credit an old paid transfer twice',async()=>{
 const h=setup(),{topup,bank}=await h.deposit();await h.migrate();await h.prune();
 await Promise.all([h.service.bankEvent(bank),h.service.bankEvent(bank)]);
 assert.equal(h.balance('A').available,100000);
 assert.equal(h.db.docs.get('payments/'+topup.topupId).status,'PAID');
 assert.equal([...h.db.docs].filter(([p])=>p.startsWith('payments/_system/ledger/')).length,1);h.conservation();
});
test('new bank events and ledgers use nested paths only after old roots are removed',async()=>{
 const h=setup();await h.migrate();await h.prune();const {topup,bank}=await h.deposit();
 assert(h.db.docs.has('payments/'+topup.topupId));assert(h.db.docs.has('payments/_system/bankEvents/'+bank.id));
 assert(![...h.db.docs.keys()].some(p=>/^wallet(Topups|Ledger|BankEvents|Operations|TopupLocks|BookingLocks)\//.test(p)));
 h.conservation();
});
test('paid booking mirror stays atomic through accept and settlement after migration',async()=>{
 const h=setup();await h.deposit();const b=await h.pay();await h.migrate();await h.prune();
 await h.service.action(h.actor('B'),{exchangeId:b.exchangeId,action:'ACCEPT'});
 assert.equal(h.db.docs.get('payments/rental_'+b.exchangeId).settlementState,'HELD');
 h.advance(4*86400000);assert.equal(await h.service.settleDue(),1);
 assert.equal(h.db.docs.get('payments/rental_'+b.exchangeId).status,'RELEASED');assert.equal(h.balance('B').available,100000);h.conservation();
});
test('a new v2 payment creates its payment record in the same hold transaction and cancellation mirrors the refund',async()=>{
 const h=setup();await h.deposit();await h.migrate();const b=await h.pay();
 assert.equal(h.db.docs.get('payments/rental_'+b.exchangeId).amount,100000);
 await h.service.action(h.actor('A'),{exchangeId:b.exchangeId,action:'CANCEL'});
 assert.equal(h.db.docs.get('payments/rental_'+b.exchangeId).status,'REFUNDED_PARTIAL');assert.equal(h.balance('A').available,80000);h.conservation();
});
test('late cancellation retains the original 20 percent fee in both exchange and payment',async()=>{
 const h=setup();await h.deposit();await h.migrate();const b=await h.pay();
 await h.service.action(h.actor('B'),{exchangeId:b.exchangeId,action:'ACCEPT'});
 await h.service.action(h.actor('A'),{exchangeId:b.exchangeId,action:'CANCEL'});
 const p=h.db.docs.get('payments/rental_'+b.exchangeId);
 assert.equal(p.status,'REFUNDED_PARTIAL');assert.equal(p.refundedAmount,80000);assert.equal(p.cancellationFee,20000);h.conservation();
});
test('legacy payment documents remain readable and are never overwritten or pruned',async()=>{
 const h=setup();h.db.docs.set('payments/legacy',{exchangeId:'older',status:'PAID',amount:1234});
 const before=hash(h.db.docs.get('payments/legacy'));await h.deposit();await h.migrate();await h.prune();
 assert.equal(hash(h.db.docs.get('payments/legacy')),before);
});
test('target collision aborts activation and leaves the original money untouched',async()=>{
 const h=setup(),t=await h.pending();h.db.docs.set('payments/'+t.topupId,{recordType:'RENTAL',amount:9});
 await assert.rejects(h.migrate(),e=>e.status===409);assert.equal(h.db.docs.get(CONTROL_PATH).storageSchema,1);
 assert.equal(h.balance('A').available,0);assert.equal(h.db.docs.get('walletTopups/'+t.topupId).status,'PENDING');
});
test('a failed backup prevents all destination copies and returns the legacy writer to ready',async()=>{
 const h=setup(),t=await h.pending();
 await assert.rejects(applyMigration(h.db,{projectId:PROJECT,saveBackup:()=>{throw Error('disk full');}}),/disk full/);
 assert(!h.db.docs.has('payments/'+t.topupId));assert.equal(h.db.docs.get(CONTROL_PATH).mode,'READY');
 assert.equal((await h.pending()).topupId,t.topupId);
});
test('all financial writes and snapshots are blocked while the migration gate is held',async()=>{
 const h=setup(),t=await h.pending(),bank=h.event(t);
 h.db.docs.set(CONTROL_PATH,{recordType:'WALLET_SYSTEM',storageSchema:1,mode:'MIGRATING',migrationId:'other'});
 await assert.rejects(h.service.bankEvent(bank),e=>e.status===503);
 await assert.rejects(h.pending(),e=>e.status===503);
 await assert.rejects(h.service.snapshot(h.actor('A')),e=>e.status===503);
 await assert.rejects(h.migrate(),e=>e.status===409);assert.equal(h.balance('A').available,0);
 const status=await h.service.storageStatus();assert.equal(status.storageState,'MIGRATING');assert.equal(status.storageAdapterVersion,1);
});
test('unknown control documents fail closed rather than switching collections silently',async()=>{
 const h=setup();h.db.docs.set(CONTROL_PATH,{storageSchema:2,mode:'READY'});
 await assert.rejects(h.pending(),e=>e.status===503);assert.equal(h.balance('A').available,0);
});
test('pruning requires the exact intact backup for the active project and keeps every wallet',async()=>{
 const h=setup();await h.deposit();await h.migrate();const original=hash([...h.db.docs]);
 const tampered=JSON.parse(JSON.stringify(h.backup()));tampered.entries[0].sourcePath='wallets/A';
 await assert.rejects(pruneLegacy(h.db,{projectId:PROJECT,backup:tampered}),e=>e.status===409);
 await assert.rejects(pruneLegacy(h.db,{projectId:'wrong-project',backup:h.backup()}),e=>e.status===409);
 assert.equal(hash([...h.db.docs]),original);
 const result=await h.prune();assert(result.deleted>0);assert.equal(result.legacyRemaining,0);assert(h.db.docs.has('wallets/A'));
 assert.equal((await h.prune()).deleted,0);h.conservation();
});
test('legacy writers or missing targets stop pruning before any source document is deleted',async()=>{
 for(const cause of ['changed','missing','damaged']){
  const h=setup(),{topup}=await h.deposit();await h.migrate();
  if(cause==='changed')h.db.docs.get('walletTopups/'+topup.topupId).amount=123000;
  else if(cause==='missing')h.db.docs.delete('payments/'+topup.topupId);
  else h.db.docs.get('payments/'+topup.topupId).amount=999999;
  const before=hash([...h.db.docs]);await assert.rejects(h.prune(),e=>e.status===409);assert.equal(hash([...h.db.docs]),before);
 }
});
test('app snapshots still return original topup and ledger IDs with only current user records',async()=>{
 const h=setup(),{topup}=await h.deposit();await h.migrate();await h.prune();
 const data=await h.service.snapshot(h.actor('A'));assert.equal(data.storageSchema,2);assert.equal(data.topups[0].topupId,topup.topupId);
 assert.equal(data.ledger.length,1);assert(!data.ledger[0].ledgerId.includes('/'));
 const other=await h.service.snapshot(h.actor('C'));assert.equal(other.topups.length,0);assert.equal(other.ledger.length,0);
});
test('diagnostics follow the activated paths and remain read-only',async()=>{
 const h=setup(),{topup}=await h.deposit();await h.migrate();await h.prune();
 const before=hash([...h.db.docs]);const result=await require('../diagnose').report(h.db,{BANK_CODE:'MBBank'},{ids:[topup.topupId]});
 assert.equal(result.topups[0].status,'PAID');assert.equal(result.topups[0].ledger.length,1);assert.equal(hash([...h.db.docs]),before);
});
test('repeating apply is a no-op and cancellation cannot switch an active schema back to old paths',async()=>{
 const h=setup();await h.deposit();await h.migrate();const before=hash([...h.db.docs]);
 assert.equal((await h.migrate()).mode,'ALREADY_ACTIVE');assert.equal(hash([...h.db.docs]),before);
 await assert.rejects(cancelMigration(h.db),e=>e.status===409);
});
test('a cancelled inactive migration can resume safely from current legacy data',async()=>{
 const h=setup(),t=await h.pending();
 h.db.docs.set(CONTROL_PATH,{recordType:'WALLET_SYSTEM',storageSchema:1,mode:'MIGRATING',migrationId:'crashed'});
 h.db.docs.set('payments/'+t.topupId,{...h.db.docs.get('walletTopups/'+t.topupId),recordType:'TOPUP',storageSchema:2,_migration:{sourcePath:'walletTopups/'+t.topupId,migrationId:'crashed'}});
 await cancelMigration(h.db);await h.service.cancelTopup(h.actor('A'),{topupId:t.topupId,confirmUnpaid:true});
 await h.migrate();assert.equal(h.db.docs.get('payments/'+t.topupId).status,'CANCELLED');assert.equal(h.balance('A').available,0);
});
test('migration CLI defaults to preview, requires explicit project, and never accepts an amount or reset flag',()=>{
 assert.deepEqual(options(['--project',PROJECT]),{mode:'PREVIEW',projectId:PROJECT});
 for(const args of [[],['--project',PROJECT,'--apply'],['--project',PROJECT,'--prune-legacy'],['--project',PROJECT,'--amount','999'],['--project',PROJECT,'--reset'],['--project',PROJECT,'--apply','--prune-legacy','--backup','a']])assert.throws(()=>options(args));
 assert.equal(physicalPath('walletLedger/topup_1',2),'payments/_system/ledger/topup_1');
 assert.equal(physicalPath('walletTopups/NAP0123456789ABCDEF',2),'payments/NAP0123456789ABCDEF');
});
test('new backend code continues to use legacy collections before an explicit migration',async()=>{
 const h=setup();await h.deposit();assert(!h.db.docs.has(CONTROL_PATH));
 assert.equal((await h.service.storageStatus()).storageSchema,1);
 assert([...h.db.docs.keys()].some(p=>p.startsWith('walletTopups/')));assert(![...h.db.docs.keys()].some(p=>p.startsWith('payments/')));
});
test('more than two hundred old events are copied and pruned without losing a page',async()=>{
 const h=setup();for(let i=1;i<=230;i++)h.db.docs.set('walletBankEvents/'+i,{id:String(i),result:'UNMATCHED',amount:1000,createdAt:Timestamp.fromMillis(i)});
 await h.migrate();const result=await h.prune();assert.equal(result.deleted,230);assert.equal(result.legacyRemaining,0);
 assert.equal([...h.db.docs.keys()].filter(p=>p.startsWith('payments/_system/bankEvents/')).length,230);
});
test('an occupied rental payment ID rejects checkout before debiting the wallet',async()=>{
 const h=setup();await h.deposit();await h.migrate();
 h.db.docs.set('exchanges/old',{requester_accountId:'A',host_accountId:'B',postId:'room',status:'PENDING',exchangeType:'NON_RECIPROCAL'});
 h.db.docs.set('payments/rental_old',{recordType:'OTHER',amount:999});
 const before=hash([...h.db.docs]);
 await assert.rejects(h.service.pay(h.actor('A'),{postId:'room',exchangeId:'old',expectedAmount:100000,operationId:h.op()}),e=>e.status===409);
 assert.equal(hash([...h.db.docs]),before);assert.equal(h.balance('A').available,100000);
});
