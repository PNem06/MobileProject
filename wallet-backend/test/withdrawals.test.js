'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const {WalletService} = require('../wallet-service');
const {createServer} = require('../server');
const {FakeFirestore, Timestamp} = require('./fake-firestore');

function setup(schema = 2) {
  const db = new FakeFirestore();
  const config = {adminUid:'treasury',bankCode:'MBBank',bankAccount:'123456789',
    receiveAccount:'123456789',accountName:'TEAM',topupMinutes:15};
  for (const uid of ['treasury','admin2','A','B']) db.docs.set('accounts/'+uid,
    {accountId:uid,fullName:uid,role:['treasury','admin2'].includes(uid)?'ADMIN':'USER',status:'ACTIVE'});
  if (schema===2) db.docs.set('payments/_system',{recordType:'WALLET_SYSTEM',storageSchema:2,mode:'READY'});
  for (const uid of ['A','B']) db.docs.set('wallets/'+uid,{userId:uid,available:100000,held:0,currency:'VND'});
  db.docs.set('wallets/treasury',{userId:'treasury',available:2000,held:12000,currency:'VND'});
  let seq=0;
  const service=new WalletService(db,Timestamp,config,()=>Date.parse('2026-11-01T00:00:00Z'));
  const actor=uid=>service.actor(uid), op=()=>('withdraw-operation-'+(++seq)).padEnd(30,'0');
  const input=(extra={})=>({amount:30000,bankName:'Vietcombank',accountNumber:'0123456789',accountName:'NGUYỄN QUỐC HƯNG',operationId:op(),...extra});
  const create=async(uid='A',extra={})=>service.withdrawals.create(await actor(uid),input(extra));
  const action=async(id,what,uid='treasury',extra={})=>service.withdrawals.action(await actor(uid),
    {withdrawalId:id,action:what,operationId:op(),confirmTransferred:true,confirmUnpaid:true,reason:'Sai thông tin nhận tiền',...extra});
  const balance=uid=>db.docs.get('wallets/'+uid);
  const saved=row=>db.docs.get('payments/withdrawal_'+row.withdrawalId);
  const ledger=()=>[...db.docs.entries()].filter(([key])=>key.startsWith(schema===2?'payments/_system/ledger/':'walletLedger/'));
  return {db,service,actor,op,input,create,action,balance,saved,ledger};
}

for (const schema of [1,2]) {
  test('schema '+schema+': reserve money without adding it to the admin balance',async()=>{
    const h=setup(schema),treasury={...h.balance('treasury')},row=await h.create();
    assert.equal(row.status,'PENDING');assert.equal(row.recordType,'WITHDRAWAL');
    assert.equal(h.balance('A').available,70000);assert.equal(h.balance('A').held,30000);
    assert.deepEqual(h.balance('treasury'),treasury);assert.equal(h.saved(row).accountNumber,'0123456789');
    assert.equal(h.ledger().length,2);assert.equal(h.db.docs.get('payments/_system/withdrawalLocks/A').withdrawalId,row.withdrawalId);
  });
  test('schema '+schema+': rejection returns the full hold, once, to the correct user',async()=>{
    const h=setup(schema),row=await h.create();
    const result=await h.action(row.withdrawalId,'REJECT','admin2');
    assert.equal(result.status,'REJECTED');assert.equal(result.rejectedBy,'admin2');
    assert.equal(h.balance('A').available,100000);assert.equal(h.balance('A').held,0);
    await h.action(row.withdrawalId,'REJECT','admin2');assert.equal(h.balance('A').available,100000);
    assert.equal(h.ledger().length,4);assert.equal(h.db.docs.get('payments/_system/withdrawalLocks/A').withdrawalId,'');
  });
  test('schema '+schema+': manual bank payout is finalized by a click, without any transaction reference field',async()=>{
    const h=setup(schema),row=await h.create(),treasury={...h.balance('treasury')};
    await h.action(row.withdrawalId,'CLAIM','admin2');
    assert.equal(h.balance('A').held,30000);
    const paid=await h.action(row.withdrawalId,'PAID','admin2');
    assert.equal(paid.status,'PAID');assert.equal(paid.paidBy,'admin2');assert.equal(h.balance('A').available,70000);
    assert.equal(h.balance('A').held,0);assert.deepEqual(h.balance('treasury'),treasury);
    await h.action(row.withdrawalId,'PAID','admin2');assert.equal(h.ledger().length,3);
    assert.equal(h.saved(row).bankReference,undefined);
    await assert.rejects(h.action(row.withdrawalId,'REJECT','admin2'),e=>e.status===409);
  });
  test('schema '+schema+': lost create responses and concurrent identical retries reserve only once',async()=>{
    const h=setup(schema),actor=await h.actor('A'),input=h.input();
    const rows=await Promise.all(Array.from({length:8},()=>h.service.withdrawals.create(actor,input)));
    assert.equal(new Set(rows.map(r=>r.withdrawalId)).size,1);assert.equal(h.balance('A').available,70000);
    assert.equal(h.balance('A').held,30000);assert.equal(h.ledger().length,2);
    const again=await h.create('A');assert.equal(again.withdrawalId,rows[0].withdrawalId);
    await assert.rejects(h.create('A',{amount:40000}),e=>e.status===409);
  });
  test('schema '+schema+': snapshot isolates users and gives every admin the same pending queue',async()=>{
    const h=setup(schema),a=await h.create('A'),b=await h.create('B',{amount:20000});
    const snapshot=async uid=>h.service.snapshot(await h.actor(uid));
    const [user,admin1,admin2]=await Promise.all([snapshot('A'),snapshot('treasury'),snapshot('admin2')]);
    assert.deepEqual(user.withdrawals.map(r=>r.withdrawalId),[a.withdrawalId]);assert.equal(user.withdrawalPendingAmount,30000);
    assert.deepEqual(admin1.withdrawals,admin2.withdrawals);assert.equal(admin1.withdrawals.length,2);
    assert.equal(admin1.withdrawalPendingAmount,50000);assert.equal(admin1.withdrawalPendingCount,2);
    assert.equal(admin1.withdrawalVersion,1);
    await assert.rejects(h.service.withdrawals.get(await h.actor('A'),b.withdrawalId),e=>e.status===403);
    assert.equal((await h.service.withdrawals.get(await h.actor('admin2'),b.withdrawalId)).userId,'B');
  });
}

test('two admins racing to claim get one owner, and the other cannot confirm or reject',async()=>{
  const h=setup(),row=await h.create();
  const results=await Promise.allSettled([h.action(row.withdrawalId,'CLAIM'),h.action(row.withdrawalId,'CLAIM','admin2')]);
  assert.equal(results.filter(r=>r.status==='fulfilled').length,1);
  const owner=h.saved(row).processingAdminUid,other=owner==='treasury'?'admin2':'treasury';
  await assert.rejects(h.action(row.withdrawalId,'PAID',other),e=>e.status===403);
  await assert.rejects(h.action(row.withdrawalId,'REJECT',other),e=>e.status===403);
  assert.equal(h.balance('A').held,30000);assert.equal(h.ledger().length,2);
});
test('two confirmation clicks and a simultaneous rejection produce exactly one terminal movement',async()=>{
  const h=setup(),row=await h.create();await h.action(row.withdrawalId,'CLAIM');
  const results=await Promise.allSettled([h.action(row.withdrawalId,'PAID'),h.action(row.withdrawalId,'PAID'),h.action(row.withdrawalId,'REJECT')]);
  assert.equal(h.saved(row).status,'PAID');assert.equal(h.balance('A').held,0);
  assert.equal(h.balance('A').available,70000);assert.equal(h.ledger().length,3);
  assert.equal(results[2].status,'rejected');
});
test('a claimed request can be rejected by its owner before transfer and the hold is restored',async()=>{
  const h=setup(),row=await h.create();await h.action(row.withdrawalId,'CLAIM','admin2');
  await h.action(row.withdrawalId,'REJECT','admin2');assert.equal(h.balance('A').available,100000);assert.equal(h.balance('A').held,0);
});
test('cashout cannot complete before claim or without explicit bank-transfer confirmation',async()=>{
  const h=setup(),row=await h.create();
  await assert.rejects(h.action(row.withdrawalId,'PAID'),e=>e.status===409);
  await h.action(row.withdrawalId,'CLAIM');
  await assert.rejects(h.action(row.withdrawalId,'PAID','treasury',{confirmTransferred:false}),e=>e.status===400);
  await assert.rejects(h.action(row.withdrawalId,'REJECT','treasury',{confirmUnpaid:false}),e=>e.status===400);
  assert.equal(h.saved(row).status,'PROCESSING');assert.equal(h.balance('A').held,30000);
});
test('a user cannot act as an admin or withdraw from the treasury via forged input',async()=>{
  const h=setup(),row=await h.create('A',{userId:'treasury',walletOwnerId:'treasury',isAdmin:true,status:'PAID'});
  assert.equal(row.userId,'A');assert.equal(row.status,'PENDING');
  await assert.rejects(h.action(row.withdrawalId,'CLAIM','A'),e=>e.status===403);
  await assert.rejects(h.create('admin2'),e=>e.status===403);
  h.db.docs.get('accounts/admin2').status='LOCKED';await assert.rejects(h.action(row.withdrawalId,'CLAIM','admin2'),e=>e.status===403);
});
test('pending holds cannot be spent again on a rental booking',async()=>{
  const h=setup();await h.create('A',{amount:90000});
  const now=Date.parse('2026-11-01T00:00:00Z');
  h.db.docs.set('posts/room',{accountId:'B',status:'APPROVED',price:20000,title:'Room',startDate:Timestamp.fromMillis(now+86400000),endDate:Timestamp.fromMillis(now+2*86400000)});
  await assert.rejects(h.service.pay(await h.actor('A'),{postId:'room',expectedAmount:20000,operationId:h.op()}),e=>e.status===409);
  assert.equal(h.balance('A').available,10000);assert.equal(h.balance('A').held,90000);
});
test('a second withdrawal is possible after completion, but replaying the first request never creates another',async()=>{
  const h=setup(),actor=await h.actor('A'),input=h.input(),row=await h.service.withdrawals.create(actor,input);
  await h.action(row.withdrawalId,'CLAIM');await h.action(row.withdrawalId,'PAID');
  assert.equal((await h.service.withdrawals.create(actor,input)).status,'PAID');
  assert.equal(h.balance('A').available,70000);assert.equal(h.balance('A').held,0);
  const second=await h.create('A',{amount:20000});assert.notEqual(second.withdrawalId,row.withdrawalId);
  assert.equal(h.balance('A').available,50000);assert.equal(h.balance('A').held,20000);
});
test('the same operation key cannot target another amount or another admin action',async()=>{
  const h=setup(),actor=await h.actor('A'),input=h.input(),row=await h.service.withdrawals.create(actor,input);
  await assert.rejects(h.service.withdrawals.create(actor,{...input,amount:40000}),e=>e.status===409);
  const operationId=h.op();await h.action(row.withdrawalId,'CLAIM','treasury',{operationId});
  await assert.rejects(h.action(row.withdrawalId,'PAID','treasury',{operationId}),e=>e.status===409);
  assert.equal(h.balance('A').held,30000);
});
test('schema 2 creates no new legacy wallet collections',async()=>{
  const h=setup(),row=await h.create();await h.action(row.withdrawalId,'REJECT');
  for(const path of h.db.docs.keys())assert(!/^wallet(?:Ledger|Operations|Withdrawals|WithdrawalLocks)\//.test(path),path);
});
test('open withdrawals remain visible even when more than one hundred records exist',async()=>{
  const h=setup();
  for(let i=0;i<105;i++)h.db.docs.set('payments/withdrawal_open_'+i,{recordType:'WITHDRAWAL',withdrawalId:'open_'+i,userId:'A',status:'PENDING',amount:1000,createdAt:Timestamp.fromMillis(i)});
  for(let i=0;i<110;i++)h.db.docs.set('payments/withdrawal_closed_'+i,{recordType:'WITHDRAWAL',withdrawalId:'closed_'+i,userId:'A',status:'PAID',amount:1000,createdAt:Timestamp.fromMillis(i)});
  const list=await h.service.withdrawals.list(await h.actor('treasury'));
  assert.equal(list.withdrawalPendingCount,105);assert.equal(list.withdrawalPendingAmount,105000);
  assert.equal(list.withdrawals.filter(r=>r.status==='PENDING').length,105);assert.equal(list.withdrawals.length,205);
});
test('a pending withdrawal survives legacy payment consolidation and can still be paid once',async()=>{
  const h=setup(1),row=await h.create();const {applyMigration,pruneLegacy}=require('../payment-migration');let backup;
  await applyMigration(h.db,{projectId:'demo-withdrawal-migration',saveBackup:async value=>{backup=JSON.parse(JSON.stringify(value));}});
  await pruneLegacy(h.db,{projectId:'demo-withdrawal-migration',backup});
  assert.equal((await h.service.withdrawals.get(await h.actor('A'),row.withdrawalId)).status,'PENDING');
  await h.action(row.withdrawalId,'CLAIM');await h.action(row.withdrawalId,'PAID');
  assert.equal(h.balance('A').available,70000);assert.equal(h.balance('A').held,0);
  assert.equal(h.db.docs.get('payments/withdrawal_'+row.withdrawalId).status,'PAID');
});
test('migration gate blocks withdrawals and corrupted holds fail without partial writes',async()=>{
  const h=setup();h.db.docs.get('payments/_system').mode='MIGRATING';
  await assert.rejects(h.create(),e=>e.status===503);assert.equal(h.balance('A').available,100000);
  h.db.docs.get('payments/_system').mode='READY';const row=await h.create();await h.action(row.withdrawalId,'CLAIM');
  h.balance('A').held=0;const before=JSON.stringify([...h.db.docs]);
  await assert.rejects(h.action(row.withdrawalId,'PAID'),e=>e.status===409);assert.equal(JSON.stringify([...h.db.docs]),before);
});
test('outgoing SePay bank events do not automatically approve or duplicate a manual payout',async()=>{
  const h=setup(),row=await h.create();await h.action(row.withdrawalId,'CLAIM');
  const event={id:1,gateway:'MBBank',accountNumber:'123456789',transferType:'out',transferAmount:30000,content:row.withdrawalId};
  assert.equal((await h.service.bankEvent(event)).ignored,true);assert.equal(h.saved(row).status,'PROCESSING');assert.equal(h.balance('A').held,30000);
});
for (const [name,change] of Object.entries({
  negative:{amount:-1000},fraction:{amount:1000.5},textAmount:{amount:'10000'},tooSmall:{amount:999},tooLarge:{amount:50000001},
  missingBank:{bankName:''},controlBank:{bankName:'Bank\nHidden'},invalidAccount:{accountNumber:'123/456'},shortAccount:{accountNumber:'123'},
  missingName:{accountName:''},controlName:{accountName:'Name\nHidden'},missingOperation:{operationId:''}
})) test('reject invalid withdrawal: '+name,async()=>{
  const h=setup(),before=JSON.stringify([...h.db.docs]);await assert.rejects(h.create('A',change),e=>e.status===400);assert.equal(JSON.stringify([...h.db.docs]),before);
});
test('insufficient balance never reserves a partial amount or creates a request',async()=>{
  const h=setup(),before=JSON.stringify([...h.db.docs]);await assert.rejects(h.create('A',{amount:100001}),e=>e.status===409);assert.equal(JSON.stringify([...h.db.docs]),before);
});
test('withdrawal HTTP endpoints authenticate every call and authorize the actual current admin',async t=>{
  const h=setup(),server=createServer({service:h.service,webhookKey:'k'.repeat(40),authenticate:async token=>
    ['A','B','treasury','admin2'].includes(token)?{uid:token}:null,logger:{error(){}}});
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));t.after(()=>new Promise(resolve=>server.close(resolve)));
  const base='http://127.0.0.1:'+server.address().port;
  const post=(route,uid,body)=>fetch(base+route,{method:'POST',headers:{'Content-Type':'application/json',...(uid?{Authorization:'Bearer '+uid}:{})},body:JSON.stringify(body)});
  const input=h.input({userId:'B',role:'ADMIN'});
  assert.equal((await post('/v1/withdrawals','',input)).status,401);
  const create=await post('/v1/withdrawals','A',input);assert.equal(create.status,200);const row=(await create.json()).data;
  assert.equal(row.userId,'A');assert.equal((await post('/v1/withdrawals/get','B',{withdrawalId:row.withdrawalId})).status,403);
  const action=what=>({withdrawalId:row.withdrawalId,action:what,operationId:h.op(),confirmTransferred:true});
  assert.equal((await post('/v1/withdrawals/action','A',action('CLAIM'))).status,403);
  assert.equal((await post('/v1/withdrawals/action','admin2',action('CLAIM'))).status,200);
  assert.equal((await post('/v1/withdrawals/action','treasury',action('PAID'))).status,403);
  const paid=await post('/v1/withdrawals/action','admin2',action('PAID'));assert.equal(paid.status,200);assert.equal((await paid.json()).data.status,'PAID');
  h.db.docs.get('accounts/admin2').status='LOCKED';assert.equal((await post('/v1/withdrawals/get','admin2',{withdrawalId:row.withdrawalId})).status,403);
  const health=await(await fetch(base+'/health')).json();assert.equal(health.withdrawalVersion,1);assert.equal(health.backendVersion,'1.5.0');
});
