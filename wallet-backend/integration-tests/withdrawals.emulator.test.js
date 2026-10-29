'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {createRequire} = require('node:module');
const {initializeApp,deleteApp} = require('firebase-admin/app');
const {getFirestore,Timestamp} = require('firebase-admin/firestore');
const {WalletService} = require('../wallet-service');
const PROJECT = 'demo-stayexchange-rebuild';

test('withdrawals use real Firestore transactions and preserve existing client Rules', {timeout:90000}, async t => {
  assert.equal(process.env.FIRESTORE_EMULATOR_HOST,'127.0.0.1:8085','Use only the local demo emulator');
  assert.equal(process.env.GCLOUD_PROJECT,PROJECT,'Never run against a real Firebase project');
  const reset=await fetch('http://127.0.0.1:8085/emulator/v1/projects/'+PROJECT+'/databases/(default)/documents',{method:'DELETE'});
  assert.equal(reset.status,200);
  const app=initializeApp({projectId:PROJECT},'withdrawals-test');t.after(()=>deleteApp(app));
  const db=getFirestore(app),now=Date.parse('2026-11-01T00:00:00Z');
  const service=new WalletService(db,Timestamp,{adminUid:'admin',bankCode:'MBBank',bankAccount:'123456789',
    receiveAccount:'123456789',accountName:'TEAM',topupMinutes:15},()=>now);
  const batch=db.batch();
  batch.set(db.doc('payments/_system'),{recordType:'WALLET_SYSTEM',storageSchema:2,mode:'READY'});
  for(const uid of ['A','B','C','race','admin','admin2']){
    batch.set(db.doc('accounts/'+uid),{accountId:uid,fullName:uid,status:'ACTIVE',role:uid.startsWith('admin')?'ADMIN':'USER'});
    batch.set(db.doc('wallets/'+uid),{userId:uid,available:uid.startsWith('admin')?0:100000,held:0,currency:'VND'});
  }
  batch.set(db.doc('posts/room'),{accountId:'B',status:'APPROVED',price:20000,title:'Room',startDate:Timestamp.fromMillis(now+86400000),endDate:Timestamp.fromMillis(now+2*86400000)});
  await batch.commit();
  let seq=0,row,owner;
  const op=()=>('native-withdrawal-'+(++seq)).padEnd(30,'0');
  const input=(extra={})=>({amount:30000,bankName:'Vietcombank',accountNumber:'0123456789',accountName:'NGUYEN QUOC HUNG',operationId:op(),...extra});
  const create=async(uid,extra={})=>service.withdrawals.create(await service.actor(uid),input(extra));
  const action=async(uid,id,what)=>service.withdrawals.action(await service.actor(uid),{withdrawalId:id,action:what,
    operationId:op(),confirmTransferred:true,confirmUnpaid:true,reason:'Sai thông tin nhận tiền'});
  const wallet=async uid=>(await db.doc('wallets/'+uid).get()).data();
  await t.test('concurrent identical submissions reserve once and store under payments',async()=>{
    const actor=await service.actor('A'),body=input();
    const requests=await Promise.all([service.withdrawals.create(actor,body),service.withdrawals.create(actor,body)]);
    assert.equal(requests[0].withdrawalId,requests[1].withdrawalId);row=requests[0];
    assert.equal((await wallet('A')).available,70000);assert.equal((await wallet('A')).held,30000);
    assert.equal((await db.collection('payments/_system/ledger').where('withdrawalId','==',row.withdrawalId).get()).size,2);
    assert.equal((await wallet('admin')).available,0);assert.equal((await wallet('admin')).held,0);
  });
  await t.test('two admins claiming concurrently produce one processing owner',async()=>{
    const results=await Promise.allSettled([action('admin',row.withdrawalId,'CLAIM'),action('admin2',row.withdrawalId,'CLAIM')]);
    assert.equal(results.filter(r=>r.status==='fulfilled').length,1);
    owner=(await db.doc('payments/withdrawal_'+row.withdrawalId).get()).get('processingAdminUid');
    const other=owner==='admin'?'admin2':'admin';await assert.rejects(action(other,row.withdrawalId,'PAID'),e=>e.status===403);
  });
  await t.test('duplicate bank-transfer confirmations debit the hold exactly once',async()=>{
    await Promise.all([action(owner,row.withdrawalId,'PAID'),action(owner,row.withdrawalId,'PAID')]);
    assert.equal((await wallet('A')).available,70000);assert.equal((await wallet('A')).held,0);
    assert.equal((await db.doc('payments/withdrawal_'+row.withdrawalId).get()).get('status'),'PAID');
    assert.equal((await db.collection('payments/_system/ledger').where('withdrawalId','==',row.withdrawalId).get()).size,3);
  });
  await t.test('rejection releases only the selected user hold, including simultaneous retries',async()=>{
    const b=await create('B');await Promise.all([action('admin',b.withdrawalId,'REJECT'),action('admin',b.withdrawalId,'REJECT')]);
    assert.equal((await wallet('B')).available,100000);assert.equal((await wallet('B')).held,0);
    assert.equal((await wallet('A')).available,70000);
  });
  await t.test('a concurrent rental payment and a withdrawal cannot overspend one wallet',async()=>{
    const actor=await service.actor('race');
    const results=await Promise.allSettled([service.withdrawals.create(actor,input({amount:90000})),
      service.pay(actor,{postId:'room',expectedAmount:20000,operationId:op()})]);
    assert.equal(results.filter(r=>r.status==='fulfilled').length,1);
    const w=await wallet('race');assert(w.available>=0);assert.equal(w.available,results[0].status==='fulfilled'?10000:80000);
  });
  await t.test('all admins see the same queue and other users cannot fetch a withdrawal',async()=>{
    const a=await service.snapshot(await service.actor('admin')),b=await service.snapshot(await service.actor('admin2'));
    assert.deepEqual(a.withdrawals,b.withdrawals);assert.equal(a.withdrawalPendingAmount,b.withdrawalPendingAmount);
    await assert.rejects(service.withdrawals.get(await service.actor('C'),row.withdrawalId),e=>e.status===403);
  });
  await t.test('existing Rules prevent direct client balance/status/lock writes',async()=>{
    const client=createRequire(require.resolve('../rules-tests/package.json'));
    const {initializeTestEnvironment,assertFails,assertSucceeds}=client('@firebase/rules-unit-testing');
    const {doc,getDoc,setDoc,updateDoc}=client('firebase/firestore');
    const env=await initializeTestEnvironment({projectId:PROJECT,firestore:{host:'127.0.0.1',port:8085,
      rules:fs.readFileSync(path.join(__dirname,'../firestore.wallet.rules'),'utf8')}});
    t.after(()=>env.cleanup());
    const payment='payments/withdrawal_'+row.withdrawalId;
    for(const uid of ['A','C','admin']){
      const store=env.authenticatedContext(uid).firestore();
      await assertFails(updateDoc(doc(store,payment),{status:'PAID'}));
      await assertFails(updateDoc(doc(store,'wallets/A'),{available:999999,held:0}));
      await assertFails(setDoc(doc(store,'payments/_system/withdrawalLocks/A'),{withdrawalId:''}));
    }
    await assertSucceeds(getDoc(doc(env.authenticatedContext('admin').firestore(),payment)));
    await assertFails(getDoc(doc(env.authenticatedContext('C').firestore(),payment)));
  });
});
