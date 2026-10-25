'use strict';
const {test}=require('node:test');const assert=require('node:assert/strict');
const {FakeFirestore,Timestamp}=require('./fake-firestore');const {report,options}=require('../diagnose');
test('diagnostics are read-only, explain amount mismatch and omit bank secrets',async()=>{
 const db=new FakeFirestore(),now=Date.now(),id='NAP0123456789ABCDEF';
 db.docs.set('walletTopups/'+id,{userId:'A',amount:100000,status:'REVIEW',bank:'MBBank',accountNumber:'0867875117',createdAt:Timestamp.fromMillis(now),expiresAt:Timestamp.fromMillis(now+900000)});
 db.docs.set('wallets/A',{available:0,held:0});
 db.docs.set('walletBankEvents/123',{topupId:id,bank:'MBBANK',account:'0867875117',amount:10000,result:'REVIEW',time:now,createdAt:Timestamp.fromMillis(now)});
 const before=JSON.stringify([...db.docs]);
 const data=await report(db,{BANK_CODE:'MBBank',BANK_ACCOUNT_NUMBER:'0867875117',RECEIVE_ACCOUNT_NUMBER:'0867875117',SEPAY_WEBHOOK_KEY:'VERY_SECRET',GOOGLE_APPLICATION_CREDENTIALS:'SECRET_PATH'}, {ids:[id]},now);
 assert.equal(data.topups[0].requestedAmount,100000);assert.equal(data.topups[0].bankEvents[0].amount,10000);
 assert.equal(data.topups[0].bankEvents[0].sameRequestedAmount,false);assert.equal(data.topups[0].bankEvents[0].matchesConfiguredAccount,true);
 const text=JSON.stringify(data);assert(!text.includes('VERY_SECRET'));assert(!text.includes('SECRET_PATH'));assert(!text.includes('0867875117'));
 assert.equal(JSON.stringify([...db.docs]),before);
});
test('diagnostic input is bounded and rejects unknown commands',()=>{
 assert.deepEqual(options([]),{ids:[],recent:5});assert.equal(options(['--recent','2']).recent,2);
 for(const args of [['--recent','50'],['--topup','invalid'],['--credit','10000']])assert.throws(()=>options(args));
});
test('diagnostics report a missing topup without creating data',async()=>{
 const db=new FakeFirestore(),id='NAP0123456789ABCDEF';const data=await report(db,{BANK_CODE:'MBBank'},{ids:[id]});
 assert.equal(data.topups[0].missing,true);assert.equal(db.docs.size,0);
});
