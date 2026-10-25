'use strict';
const {test}=require('node:test');const assert=require('node:assert/strict');
const {WalletService}=require('../wallet-service');const {updateExchange}=require('../exchange-service');const {FakeFirestore,Timestamp}=require('./fake-firestore');
function fixture(){
 const db=new FakeFirestore();let now=Date.parse('2026-11-01T03:00:00Z'),n=0;
 for(const uid of ['A','B','C','D','admin'])db.docs.set('accounts/'+uid,{accountId:uid,status:'ACTIVE',role:uid==='admin'?'ADMIN':'USER'});
 const post=accountId=>({accountId,status:'APPROVED',title:accountId+' room',price:10000,startDate:Timestamp.fromMillis(now+86400000),endDate:Timestamp.fromMillis(now+2*86400000)});
 for(const [id,owner]of [['target','B'],['offered','A'],['other','C']])db.docs.set('posts/'+id,post(owner));
 for(const uid of ['A','C'])db.docs.set('wallets/'+uid,{userId:uid,available:50000,held:0});
 const service=new WalletService(db,Timestamp,{adminUid:'admin',bankCode:'MBBank',bankAccount:'123456789',receiveAccount:'123456789',accountName:'ADMIN',topupMinutes:15},()=>now);
 const actor=uid=>({uid,accountId:uid,name:uid,isAdmin:uid==='admin',isModerator:uid==='admin'});
 const pay=uid=>service.pay(actor(uid),{postId:'target',expectedAmount:10000,operationId:'reservation_op_'+(++n)});
 const swap=(id,guest='A',offered='offered')=>{const b={postId:'target',offeredPostId:offered,proposedPostId:offered,requester_accountId:guest,host_accountId:'B',exchangeType:'RECIPROCAL',status:'PENDING'};db.docs.set('exchanges/'+id,b);return b;};
 const decide=(uid,id,status,extra={})=>updateExchange(service,actor(uid),{exchangeId:id,updates:{status,...extra}});
 return {db,service,actor,pay,swap,decide,advance:ms=>{now+=ms;},post};
}
test('simultaneous different guests produce one booking and only one wallet debit',async()=>{
 const h=fixture(),r=await Promise.allSettled([h.pay('A'),h.pay('C')]);assert.equal(r.filter(x=>x.status==='fulfilled').length,1);
 const b=r.find(x=>x.status==='fulfilled').value,p=h.db.docs.get('posts/target');assert.equal(p.status,'BOOKED');assert.equal(p.bookingKey,'exchanges/'+b.exchangeId);
 assert.deepEqual(new Set(p.bookingPartyIds),new Set([b.requesterUid,'B']));assert.equal(h.db.docs.get('wallets/A').available+h.db.docs.get('wallets/C').available,90000);assert.equal(h.db.docs.get('wallets/admin').held,10000);
});
test('a repeated wallet payment recovers the existing hold without charging again',async()=>{
 const h=fixture(),a=await h.pay('A');assert.equal((await h.pay('A')).exchangeId,a.exchangeId);assert.equal(h.db.docs.get('wallets/A').available,40000);await assert.rejects(h.service.quote(h.actor('C'),'target'),e=>e.status===409);
});
test('acceptance and completion keep a wallet post closed',async()=>{
 const h=fixture(),b=await h.pay('A');await h.service.action(h.actor('B'),{exchangeId:b.exchangeId,action:'ACCEPT'});assert.equal(h.db.docs.get('posts/target').bookingState,'CONFIRMED');h.advance(3*86400000);await h.service.settle(b.exchangeId);assert.equal(h.db.docs.get('posts/target').status,'BOOKED');assert.equal(h.db.docs.get('posts/target').bookingState,'COMPLETED');await assert.rejects(h.pay('C'));
});
test('rejection refunds fully and opens the post for another guest',async()=>{
 const h=fixture(),b=await h.pay('A');await h.service.action(h.actor('B'),{exchangeId:b.exchangeId,action:'REJECT'});assert.equal(h.db.docs.get('posts/target').status,'APPROVED');assert.equal(h.db.docs.get('posts/target').bookingKey,'');assert.equal(h.db.docs.get('wallets/A').available,50000);assert((await h.pay('C')).walletManaged);
});
test('wallet cancellation releases its post and keeps the existing fee policy',async()=>{
 const h=fixture(),b=await h.pay('A');await h.service.action(h.actor('A'),{exchangeId:b.exchangeId,action:'CANCEL'});assert.equal(h.db.docs.get('posts/target').status,'APPROVED');assert.equal(h.db.docs.get('wallets/A').available,48000);assert.equal(h.db.docs.get('wallets/admin').available,2000);
});
test('reciprocal acceptance atomically locks both posts for the same two parties',async()=>{
 const h=fixture();h.swap('swap');await h.decide('B','swap','ACCEPTED');for(const id of ['target','offered']){const p=h.db.docs.get('posts/'+id);assert.equal(p.bookingKey,'exchanges/swap');assert.equal(p.status,'BOOKED');assert.deepEqual(new Set(p.bookingPartyIds),new Set(['A','B']));}assert.equal([...h.db.docs.keys()].filter(x=>x.startsWith('notifications/')).length,2);assert.equal(h.db.docs.get('wallets/A').available,50000);
});
test('two acceptances cannot win the same target post',async()=>{
 const h=fixture();h.swap('first');h.swap('second','C','other');const r=await Promise.allSettled([h.decide('B','first','ACCEPTED'),h.decide('B','second','ACCEPTED')]);assert.equal(r.filter(x=>x.status==='fulfilled').length,1);assert.equal(h.db.docs.get('exchanges/second').status,'PENDING');assert.equal(h.db.docs.get('posts/other').status,'APPROVED');
});
test('wallet checkout and reciprocal acceptance share one lock',async()=>{
 const h=fixture();h.swap('swap');const r=await Promise.allSettled([h.pay('C'),h.decide('B','swap','ACCEPTED')]);assert.equal(r.filter(x=>x.status==='fulfilled').length,1);const paid=r[0].status==='fulfilled';assert.equal(h.db.docs.get('wallets/C').available,paid?40000:50000);assert.equal(h.db.docs.get('posts/offered').status,paid?'APPROVED':'BOOKED');
});
test('guest, outsider and admin cannot impersonate the host accepting a pending swap',async()=>{
 for(const uid of ['A','C','admin']){const h=fixture();h.swap('swap');await assert.rejects(h.decide(uid,'swap','ACCEPTED'),e=>e.status===403);assert.equal(h.db.docs.get('posts/target').status,'APPROVED');}
});
test('forged offered ownership and a one-post reciprocal exchange fail atomically',async()=>{
 for(const offered of ['other','target']){const h=fixture();h.swap('swap','A',offered);await assert.rejects(h.decide('B','swap','ACCEPTED'));assert.equal(h.db.docs.get('exchanges/swap').status,'PENDING');assert.equal(h.db.docs.get('posts/target').status,'APPROVED');}
});
test('cancellation releases both reciprocal posts but never an unrelated newer lock',async()=>{
 const h=fixture();h.swap('swap');await h.decide('B','swap','ACCEPTED');h.db.docs.get('posts/offered').bookingKey='exchanges/newer';await h.decide('A','swap','CANCELLED');assert.equal(h.db.docs.get('posts/target').status,'APPROVED');assert.equal(h.db.docs.get('posts/offered').bookingKey,'exchanges/newer');
});
test('reciprocal API cannot forge financial fields or cancel a paid wallet record',async()=>{
 const h=fixture();h.swap('swap');await assert.rejects(h.decide('B','swap','ACCEPTED',{amount:1}));h.db.docs.get('exchanges/swap').exchangeType='NON_RECIPROCAL';await assert.rejects(h.decide('B','swap','ACCEPTED'));const paid=await h.pay('A');await assert.rejects(h.decide('A',paid.exchangeId,'CANCELLED'));
});
test('pending offered post changes preserve ownership and cannot reopen ended requests',async()=>{
 const h=fixture();h.swap('swap');h.db.docs.set('posts/replacement',h.post('A'));await h.decide('A','swap','PENDING',{offeredPostId:'replacement',proposedPostId:'replacement'});await assert.rejects(h.decide('A','swap','PENDING',{offeredPostId:'other'}));await h.decide('A','swap','CANCELLED');await assert.rejects(h.decide('A','swap','PENDING'));
});
test('trusted account aliases resolve to real participant UIDs',async()=>{
 const h=fixture();h.db.docs.get('accounts/A').accountId='oldA';h.db.docs.get('accounts/B').accountId='oldB';h.db.docs.get('posts/target').accountId='oldB';h.db.docs.get('posts/offered').accountId='oldA';const b=h.swap('swap');b.requester_accountId='oldA';b.host_accountId='oldB';await h.decide('B','swap','ACCEPTED');assert.deepEqual(new Set(h.db.docs.get('posts/target').bookingPartyIds),new Set(['A','oldA','B','oldB']));
});
test('historical accepted records block new payments even before startup backfill',async()=>{
 const h=fixture();h.swap('old');h.db.docs.get('exchanges/old').status='ACCEPTED';await assert.rejects(h.pay('C'));assert.equal(h.db.docs.get('wallets/C').available,50000);
});
test('startup hides accepted and completed history without changing balances',async()=>{
 for(const status of ['ACCEPTED','COMPLETED']){const h=fixture();h.swap('old');h.db.docs.get('exchanges/old').status=status;const before=JSON.stringify([...h.db.docs].filter(([k])=>k.startsWith('wallet')));assert.equal((await h.service.reservations.synchronize()).locked,2);assert.equal((await h.service.reservations.synchronize()).locked,0);assert.equal(h.db.docs.get('posts/target').status,'BOOKED');assert.equal(JSON.stringify([...h.db.docs].filter(([k])=>k.startsWith('wallet'))),before);}
});
test('old double bookings are quarantined without choosing a winner or refunding money',async()=>{
 const h=fixture();h.swap('first');h.swap('second','C','other');for(const id of ['first','second'])h.db.docs.get('exchanges/'+id).status='ACCEPTED';const r=await h.service.reservations.synchronize();assert.deepEqual(r.conflicts,['target']);assert.equal(h.db.docs.get('posts/target').bookingState,'CONFLICT');assert.equal(h.db.docs.get('exchanges/first').status,'ACCEPTED');assert.equal(h.db.docs.get('exchanges/second').status,'ACCEPTED');await assert.rejects(h.pay('D'));
});
test('cancellation cannot republish a post hidden by moderation',async()=>{
 const h=fixture(),b=await h.pay('A');h.db.docs.get('posts/target').status='HIDDEN';await h.service.action(h.actor('A'),{exchangeId:b.exchangeId,action:'CANCEL'});assert.equal(h.db.docs.get('posts/target').status,'HIDDEN');
});
test('legacy exchangeRequests reserves both posts and keeps lower-case statuses',async()=>{
 const h=fixture();h.db.docs.set('exchangeRequests/old',{senderId:'A',recipientId:'B',targetPostId:'target',offeredPostId:'offered',status:'pending'});await updateExchange(h.service,h.actor('B'),{collection:'exchangeRequests',exchangeId:'old',updates:{status:'ACCEPTED'}});assert.equal(h.db.docs.get('exchangeRequests/old').status,'accepted');assert.equal(h.db.docs.get('posts/target').bookingKey,'exchangeRequests/old');
});
test('admin cancellation decisions recheck authority and release both post locks',async()=>{
 const h=fixture();h.swap('swap');await h.decide('B','swap','ACCEPTED');h.db.docs.get('exchanges/swap').cancelRequestStatus='PENDING';await assert.rejects(updateExchange(h.service,h.actor('C'),{exchangeId:'swap',cancelDecision:true}));await updateExchange(h.service,h.actor('admin'),{exchangeId:'swap',cancelDecision:true});assert.equal(h.db.docs.get('posts/target').status,'APPROVED');assert.equal(h.db.docs.get('posts/offered').status,'APPROVED');
});
test('startup tolerates malformed history without exposing unrelated posts to strangers',async()=>{
 const h=fixture();h.swap('unrelated','A','other');h.db.docs.get('exchanges/unrelated').status='ACCEPTED';h.db.docs.set('exchanges/malformed',{postId:'bad/path',requester_accountId:'A',host_accountId:'B',status:'ACCEPTED'});const r=await h.service.reservations.synchronize();assert.deepEqual(r.invalid,['exchanges/malformed']);assert(r.conflicts.includes('other'));assert.deepEqual(h.db.docs.get('posts/other').bookingPartyIds,[]);assert.equal(h.db.docs.get('wallets/A').available,50000);assert((await h.service.reservations.synchronize()).conflicts.includes('other'));
});
