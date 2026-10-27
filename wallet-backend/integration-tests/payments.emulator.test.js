'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const {initializeApp,deleteApp}=require('firebase-admin/app');
const {getFirestore,Timestamp}=require('firebase-admin/firestore');
const {WalletService}=require('../wallet-service');
const {inspect,applyMigration,pruneLegacy}=require('../payment-migration');
const PROJECT='demo-stayexchange-rebuild';

test('payment consolidation against the real Firestore emulator',async t=>{
 assert.equal(process.env.FIRESTORE_EMULATOR_HOST,'127.0.0.1:8085','Only the local emulator may be used');
 assert.equal(process.env.GCLOUD_PROJECT,PROJECT,'Only the demo Firebase project may be used');
 const cleared=await fetch('http://127.0.0.1:8085/emulator/v1/projects/'+PROJECT+'/databases/(default)/documents',{method:'DELETE'});
 assert.equal(cleared.status,200);
 const app=initializeApp({projectId:PROJECT},'payment-integration-test');t.after(()=>deleteApp(app));
 const db=getFirestore(app),now=Date.parse('2026-11-01T03:00:00Z');let clock=now,seq=500,backup,topup,bank,booking;
 const service=new WalletService(db,Timestamp,{projectId:PROJECT,adminUid:'admin',bankCode:'MBBank',bankAccount:'123456789',receiveAccount:'123456789',accountName:'TEST',topupMinutes:15},()=>clock);
 const actor=uid=>({uid,accountId:uid,name:uid,isAdmin:uid==='admin'});
 const op=()=>('emulator-operation-'+(++seq)).padEnd(25,'0');
 const event=deposit=>({id:++seq,gateway:'MBBank',accountNumber:'123456789',transferType:'in',transferAmount:deposit.amount,content:deposit.topupId,transactionDate:new Date(clock+7*3600000).toISOString().slice(0,19).replace('T',' '),referenceCode:'FT'+seq});
 const migration=()=>applyMigration(db,{projectId:PROJECT,saveBackup:async b=>{backup=JSON.parse(JSON.stringify(b));}});
 await Promise.all(['A','B','C','admin'].map(uid=>db.doc('accounts/'+uid).set({accountId:uid,status:'ACTIVE',role:uid==='admin'?'ADMIN':'USER'})));
 await db.doc('posts/room').set({accountId:'B',status:'APPROVED',price:100000,title:'Room',startDate:Timestamp.fromMillis(now+86400000),endDate:Timestamp.fromMillis(now+3*86400000)});
 await t.test('existing paid deposit and held rental are created with legacy storage',async()=>{
  topup=await service.createTopup(actor('A'),{amount:200000,operationId:op()});bank=event(topup);
  await service.bankEvent(bank);booking=await service.pay(actor('A'),{postId:'room',expectedAmount:100000,operationId:op()});
  assert.equal((await db.doc('wallets/A').get()).get('available'),100000);
  assert.equal((await db.doc('wallets/admin').get()).get('held'),100000);
  assert.equal((await inspect(db)).rentalPayments,1);
 });
 await t.test('native transactions copy timestamps, preserve balances and activate one gate',async()=>{
  const old=(await db.doc('walletTopups/'+topup.topupId).get()).data();const result=await migration();
  assert.equal(result.storageSchema,2);const newer=(await db.doc('payments/'+topup.topupId).get()).data();
  assert(old.createdAt.isEqual(newer.createdAt));assert.equal(newer.amount,old.amount);assert.equal(newer.status,'PAID');
  assert.equal((await service.snapshot(actor('A'))).wallet.available,100000);
 });
 await t.test('legacy removal and two concurrent replayed webhooks never credit again',async()=>{
  assert.equal((await pruneLegacy(db,{projectId:PROJECT,backup})).legacyRemaining,0);
  await Promise.all([service.bankEvent(bank),service.bankEvent(bank)]);
  assert.equal((await db.doc('wallets/A').get()).get('available'),100000);
  assert.equal((await db.collection('payments/_system/ledger').where('topupId','==',topup.topupId).get()).size,1);
 });
 await t.test('a pending v2 topup credits exactly once with concurrent native transactions',async()=>{
  const deposit=await service.createTopup(actor('A'),{amount:10000,operationId:op()}),incoming=event(deposit);
  await Promise.all([service.bankEvent(incoming),service.bankEvent(incoming)]);
  assert.equal((await db.doc('wallets/A').get()).get('available'),110000);
  assert.equal((await db.collection('walletTopups').get()).size,0);
 });
 await t.test('settlement updates rental payment, wallets and ledgers atomically',async()=>{
  await service.action(actor('B'),{exchangeId:booking.exchangeId,action:'ACCEPT'});clock+=4*86400000;
  assert.equal(await service.settleDue(),1);assert.equal(await service.settleDue(),0);
  assert.equal((await db.doc('payments/rental_'+booking.exchangeId).get()).get('status'),'RELEASED');
  assert.equal((await db.doc('wallets/B').get()).get('available'),100000);
  assert.equal((await db.doc('wallets/admin').get()).get('held'),0);
  const wallets=await db.collection('wallets').get();assert.equal(wallets.docs.reduce((sum,d)=>sum+d.get('available')+d.get('held'),0),210000);
 });
 await t.test('API snapshots isolate users and preserve topup and booking identifiers',async()=>{
  const a=await service.snapshot(actor('A')),other=await service.snapshot(actor('C'));
  assert(a.topups.some(t=>t.topupId===topup.topupId));assert(a.bookings.some(b=>b.exchangeId===booking.exchangeId));
  assert.equal(other.topups.length,0);assert.equal(other.bookings.length,0);assert.equal(other.ledger.length,0);
 });
});
