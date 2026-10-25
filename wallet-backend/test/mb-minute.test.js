'use strict';
const {test}=require('node:test');const assert=require('node:assert/strict');
const {WalletService}=require('../wallet-service');const c=require('../core');
const {reconcile,options,POLICY}=require('../reconcile-mb-minute');
const {report,options:diagnosticOptions}=require('../diagnose');
const {FakeFirestore,Timestamp}=require('./fake-firestore');

function fixture(bankCode='MBBank'){
  const db=new FakeFirestore();let now=Date.parse('2026-10-24T03:00:00Z'),seq=1000;
  const cfg={adminUid:'admin',bankCode,bankAccount:'123456789',receiveAccount:'123456789',bankSubAccount:'',accountName:'DEMO ADMIN',topupMinutes:15};
  const service=new WalletService(db,Timestamp,cfg,()=>now),actor={uid:'A',accountId:'A',name:'A',isAdmin:false};
  const date=ms=>new Date(ms+7*3600000).toISOString().slice(0,19).replace('T',' ');
  async function pending(created=Date.parse('2026-10-24T02:21:38.243Z'),amount=10000){
    now=created;return service.createTopup(actor,{amount,operationId:'operation_'+String(seq++).padEnd(12,'0')});
  }
  function raw(t,changes={}){return {id:seq++,gateway:cfg.bankCode,accountNumber:cfg.bankAccount,subAccount:'',transferType:'in',
    transferAmount:t.amount,content:t.topupId,transactionDate:date(Math.floor(c.milliseconds(t.createdAt)/60000)*60000),referenceCode:'FT_DEMO_'+seq,...changes};}
  async function legacy(created,received){
    const t=await pending(created),event={...c.parseEvent(raw(t)),result:'REVIEW',createdAt:Timestamp.fromMillis(received)};
    const saved=db.docs.get('walletTopups/'+t.topupId);Object.assign(saved,{status:'REVIEW',reviewEventId:event.id,reviewReason:'Legacy review: amount or transfer time.'});
    db.docs.set('walletBankEvents/'+event.id,event);now=received;
    return {t:saved,e:event,id:t.topupId,eventId:event.id};
  }
  async function pair(){
    const one=await legacy(Date.parse('2026-10-24T02:21:38.243Z'),Date.parse('2026-10-24T02:22:02.366Z'));
    const two=await legacy(Date.parse('2026-10-24T02:23:10.608Z'),Date.parse('2026-10-24T02:23:29.620Z'));
    now=Date.parse('2026-10-24T03:30:00Z');return {one,two,ids:[one.id,two.id]};
  }
  return {db,cfg,service,actor,date,pending,raw,legacy,pair,setNow:n=>{now=n;},now:()=>now,
    balance:()=>db.docs.get('wallets/A')?.available||0,ledger:()=>[...db.docs].filter(([p])=>p.startsWith('walletLedger/'))};
}

test('MB minute timestamps reproduce both reported gaps and now credit new requests once',async()=>{
  const h=fixture();
  for(const [created,received] of [[Date.parse('2026-10-24T02:21:38.243Z'),Date.parse('2026-10-24T02:22:02.366Z')],
    [Date.parse('2026-10-24T02:23:10.608Z'),Date.parse('2026-10-24T02:23:29.620Z')]]){
    const t=await h.pending(created),raw=h.raw(t);h.setNow(received);
    assert.equal((await h.service.bankEvent(raw)).result,'PAID');assert((await h.service.bankEvent(raw)).duplicate);
    assert.equal(h.db.docs.get('walletTopups/'+t.topupId).minutePrecisionApplied,true);
  }
  assert.equal(h.balance(),20000);assert.equal(h.ledger().length,2);
});
test('MB same-minute allowance stops at the minute boundary',()=>{
  const created=Date.parse('2026-10-24T02:21:59.999Z'),t={status:'PENDING',amount:10000,createdAt:created,expiresAt:created+900000};
  const e={bank:'MBBANK',amount:10000,time:Date.parse('2026-10-24T02:21:00Z')};
  assert.equal(c.topupResult(t,e,created+10000),'PAID');
  assert.equal(c.topupResult({...t,createdAt:Date.parse('2026-10-24T02:22:00Z')},e,created+10000),'REVIEW');
});
test('MB nonzero seconds and other banks retain strict before-request checks',()=>{
  const created=Date.parse('2026-10-24T02:21:38.243Z'),t={status:'PENDING',amount:10000,createdAt:created,expiresAt:created+900000};
  assert.equal(c.topupResult(t,{bank:'MBBANK',amount:10000,time:Date.parse('2026-10-24T02:21:37Z')},created+10000),'REVIEW');
  assert.equal(c.topupResult(t,{bank:'VPBANK',amount:10000,time:Date.parse('2026-10-24T02:21:00Z')},created+10000),'REVIEW');
  assert.equal(c.topupResult(t,{bank:'MBBANK',amount:10000,time:Date.parse('2026-10-24T02:21:38Z')},created+10000),'PAID');
});
test('new MB minute policy still rejects wrong amount, expired and future bank time',async()=>{
  for(const changes of [{transferAmount:9999},{transactionDate:'2026-10-24 09:37:00'},{transactionDate:'2026-10-24 09:24:00'}]){
    const h=fixture(),t=await h.pending();h.setNow(c.milliseconds(t.createdAt)+30000);
    assert.equal((await h.service.bankEvent(h.raw(t,changes))).result,'REVIEW');assert.equal(h.balance(),0);
  }
});
test('minute policy cannot turn a cancelled request into a paid one',async()=>{
  const h=fixture(),t=await h.pending();await h.service.cancelTopup(h.actor,{topupId:t.topupId,confirmUnpaid:true});
  h.setNow(c.milliseconds(t.createdAt)+30000);assert.equal((await h.service.bankEvent(h.raw(t))).result,'REVIEW');assert.equal(h.balance(),0);
});
test('invalid request deadlines cannot pass the time check',()=>{
  const e={bank:'MBBANK',amount:10000,time:Date.parse('2026-10-24T02:21:00Z')};
  for(const t of [{createdAt:null,expiresAt:100},{createdAt:100,expiresAt:NaN},{createdAt:100,expiresAt:1}])
    assert.equal(c.topupResult({...t,status:'PENDING',amount:10000},e,Date.now()),'REVIEW');
});
test('reconciliation dry-run plans exactly two 10k credits without any writes',async()=>{
  const h=fixture(),p=await h.pair(),before=JSON.stringify([...h.db.docs]);
  const r=await reconcile(h.service,p.ids);assert.equal(r.mode,'DRY_RUN');assert.equal(r.creditTotal,20000);
  assert(r.topups.every(t=>t.status==='ELIGIBLE'));assert.equal(r.wallets[0].availableAfter,20000);
  assert.equal(JSON.stringify([...h.db.docs]),before);
});
test('reconciliation applies two credits atomically and preserves original review evidence',async()=>{
  const h=fixture(),p=await h.pair(),r=await reconcile(h.service,p.ids,{apply:true});
  assert.equal(r.creditTotal,20000);assert.equal(h.balance(),20000);assert.equal(h.ledger().length,2);
  for(const item of [p.one,p.two]){
    const t=h.db.docs.get('walletTopups/'+item.id),e=h.db.docs.get('walletBankEvents/'+item.eventId);
    assert.equal(t.status,'PAID');assert.equal(t.reviewReason,'Legacy review: amount or transfer time.');
    assert.equal(e.result,'PAID');assert.equal(e.originalResult,'REVIEW');assert.equal(e.resolution,POLICY);
    assert.equal(e.time,item.e.time);assert(h.db.docs.has('notifications/topup_'+item.eventId));
  }
});
test('reconciliation rerun and later webhook replay never double-credit',async()=>{
  const h=fixture(),p=await h.pair();await reconcile(h.service,p.ids,{apply:true});
  const again=await reconcile(h.service,p.ids,{apply:true});assert.equal(again.creditTotal,0);assert(again.topups.every(t=>t.status==='ALREADY_PAID'));
  const raw={id:Number(p.one.eventId),gateway:'MBBank',accountNumber:h.cfg.bankAccount,transferType:'in',transferAmount:10000,
    content:p.one.id,transactionDate:h.date(p.one.e.time)};
  assert((await h.service.bankEvent(raw)).duplicate);assert.equal(h.balance(),20000);assert.equal(h.ledger().length,2);
});
test('two concurrent reconciliation runs produce one set of credits',async()=>{
  const h=fixture(),p=await h.pair();const rs=await Promise.all([reconcile(h.service,p.ids,{apply:true}),reconcile(h.service,p.ids,{apply:true})]);
  assert.equal(rs.reduce((s,r)=>s+r.creditTotal,0),20000);assert.equal(h.balance(),20000);assert.equal(h.ledger().length,2);
});
test('one invalid event rejects the whole batch without crediting the valid request',async()=>{
  const h=fixture(),p=await h.pair();h.db.docs.get('walletBankEvents/'+p.two.eventId).amount=9999;
  const before=JSON.stringify([...h.db.docs]);await assert.rejects(reconcile(h.service,p.ids,{apply:true}));assert.equal(JSON.stringify([...h.db.docs]),before);
});
test('reconciliation refuses a bank event received before creation or in the future',async()=>{
  for(const receipt of ['2026-10-24T02:21:37Z','2030-01-01T00:00:00Z']){
    const h=fixture(),p=await h.pair();h.db.docs.get('walletBankEvents/'+p.one.eventId).createdAt=Timestamp.fromMillis(Date.parse(receipt));
    await assert.rejects(reconcile(h.service,[p.one.id],{apply:true}));assert.equal(h.balance(),0);
  }
});
test('reconciliation refuses the previous minute and an exact nonzero second',async()=>{
  for(const time of ['2026-10-24T02:20:00Z','2026-10-24T02:21:37Z']){
    const h=fixture(),p=await h.pair();h.db.docs.get('walletBankEvents/'+p.one.eventId).time=Date.parse(time);
    await assert.rejects(reconcile(h.service,[p.one.id],{apply:true}));assert.equal(h.balance(),0);
  }
});
test('reconciliation refuses cancellation, different review reasons and malformed paid states',async()=>{
  for(const patch of [{status:'CANCELLED'},{status:'PENDING'},{status:'PAID'},{cancelledAt:Timestamp.fromMillis(Date.now())},
    {paidAt:Timestamp.fromMillis(Date.now())},{bankEventId:'99999'},{reviewCode:'AMOUNT_MISMATCH'}]){
    const h=fixture(),p=await h.pair();Object.assign(h.db.docs.get('walletTopups/'+p.one.id),patch);
    await assert.rejects(reconcile(h.service,[p.one.id],{apply:true}));assert.equal(h.balance(),0);
  }
});
test('reconciliation refuses mismatched bank, receiver, VA, amount, direction, event identity or missing reference',async()=>{
  for(const patch of [{bank:'VPBANK'},{account:'987654321'},{amount:9999},{direction:'out'},{id:'99999'},
    {reference:''},{result:'EXTRA_PAYMENT'}]){
    const h=fixture(),p=await h.pair();Object.assign(h.db.docs.get('walletBankEvents/'+p.one.eventId),patch);
    await assert.rejects(reconcile(h.service,[p.one.id],{apply:true}));assert.equal(h.balance(),0);
  }
  const h=fixture(),p=await h.pair();h.cfg.bankSubAccount='VA_NEW';
  await assert.rejects(reconcile(h.service,p.ids,{apply:true}));assert.equal(h.balance(),0);
});
test('reconciliation refuses ambiguous multiple events for one topup',async()=>{
  const h=fixture(),p=await h.pair();h.db.docs.set('walletBankEvents/999999',{...p.one.e,id:'999999'});
  await assert.rejects(reconcile(h.service,[p.one.id],{apply:true}));assert.equal(h.balance(),0);
});
test('reconciliation refuses existing ledgers or notifications and never overwrites them',async()=>{
  for(const kind of ['linkedLedger','occupiedCanonicalLedger','notification']){
    const h=fixture(),p=await h.pair();
    if(kind==='linkedLedger')h.db.docs.set('walletLedger/custom',{topupId:p.one.id,amount:10000});
    if(kind==='occupiedCanonicalLedger')h.db.docs.set('walletLedger/topup_'+p.one.eventId,{topupId:'another',amount:10000});
    if(kind==='notification')h.db.docs.set('notifications/topup_'+p.one.eventId,{title:'Existing'});
    const before=JSON.stringify([...h.db.docs]);await assert.rejects(reconcile(h.service,[p.one.id],{apply:true}));assert.equal(JSON.stringify([...h.db.docs]),before);
  }
});
test('reconciliation preserves existing available and held balances',async()=>{
  const h=fixture(),p=await h.pair();h.db.docs.set('wallets/A',{userId:'A',available:4321,held:777,currency:'VND'});
  const r=await reconcile(h.service,p.ids,{apply:true});assert.equal(h.balance(),24321);assert.equal(h.db.docs.get('wallets/A').held,777);
  assert.equal(r.wallets[0].availableBefore,4321);assert.equal(r.wallets[0].availableAfter,24321);
});
test('reconciliation rejects unsafe wallets without any partial credit',async()=>{
  for(const available of [-1,Number.MAX_SAFE_INTEGER]){
    const h=fixture(),p=await h.pair();h.db.docs.set('wallets/A',{available,held:0});const before=JSON.stringify([...h.db.docs]);
    await assert.rejects(reconcile(h.service,p.ids,{apply:true}));assert.equal(JSON.stringify([...h.db.docs]),before);
  }
});
test('reconciliation CLI defaults to preview and rejects arbitrary amounts or invalid IDs',()=>{
  const id='NAP0123456789ABCDEF';assert.equal(options(['--topup',id]).apply,false);
  assert.equal(options(['--topup',id,'--apply']).apply,true);
  for(const args of [[],['--topup','invalid'],['--topup',id,'--amount','10000'],['--topup',id,'--topup',id],['--topup',id,'--apply','--apply']])
    assert.throws(()=>options(args));
});
test('diagnostics explain the old early timestamp and current minute policy without changing data',async()=>{
  const h=fixture(),p=await h.pair();const before=JSON.stringify([...h.db.docs]);
  const r=await report(h.db,{BANK_CODE:'MBBank',BANK_ACCOUNT_NUMBER:h.cfg.bankAccount,RECEIVE_ACCOUNT_NUMBER:h.cfg.receiveAccount},{ids:p.ids},h.now());
  for(const t of r.topups){assert(t.bankEvents[0].beforeRequest);assert(t.bankEvents[0].acceptedByTimePolicy);assert(t.bankEvents[0].mbMinutePrecisionApplied);}
  assert.equal(JSON.stringify([...h.db.docs]),before);assert.equal(diagnosticOptions(['--output','report.json']).output,'report.json');
});
