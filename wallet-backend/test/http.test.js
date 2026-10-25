'use strict';
const {test}=require('node:test');const assert=require('node:assert/strict');const {createServer}=require('../server');
test('HTTP integration: verifies bearer/secret, rejects browser origins, returns webhook acknowledgement',async t=>{
 let bankCalls=0,quoteCalls=0;const service={bankInfo:()=>({code:'MBBank'}),actor:async uid=>({uid}),snapshot:async a=>({userId:a.uid}),quote:async a=>{quoteCalls++;return {amount:100000};},bankEvent:async()=>{bankCalls++;}};
 const server=createServer({service,webhookKey:'k'.repeat(40),authenticate:async token=>token==='verified-token'?{uid:'A'}:null,logger:{error(){}}});
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));t.after(()=>new Promise(resolve=>server.close(resolve)));
 const base='http://127.0.0.1:'+server.address().port;
 const post=(path,headers={},body={})=>fetch(base+path,{method:'POST',headers:{'Content-Type':'application/json',...headers},body:JSON.stringify(body)});
 const health=await fetch(base+'/health');assert.equal(health.status,200);const healthData=await health.json();
 assert.equal(healthData.bank,'MBBank');assert.equal(healthData.backendVersion,require('../package.json').version);
 assert.equal((await post('/v1/quote')).status,401);assert.equal(quoteCalls,0);
 assert.equal((await post('/v1/quote',{Authorization:'Bearer fake'})).status,401);
 assert.equal((await post('/v1/quote',{Authorization:'Bearer verified-token',Origin:'https://unknown.example'})).status,403);
 let res=await post('/v1/quote',{Authorization:'Bearer verified-token'});assert.equal(res.status,200);assert.equal((await res.json()).data.amount,100000);
 assert.equal((await post('/webhooks/sepay',{Authorization:'Apikey wrong'})).status,401);assert.equal(bankCalls,0);
 res=await post('/webhooks/sepay',{Authorization:'Apikey '+'k'.repeat(40)});assert.equal(res.status,200);assert.equal((await res.json()).success,true);assert.equal(bankCalls,1);
});
test('topup cancel endpoint verifies user authentication and forwards resource confirmation',async t=>{
 let cancelled=0;const service={bankInfo:()=>({code:'MBBank'}),actor:async uid=>({uid}),cancelTopup:async(actor,body)=>{assert.equal(actor.uid,'A');assert.equal(body.confirmUnpaid,true);cancelled++;return {topupId:body.topupId,status:'CANCELLED'};}};
 const server=createServer({service,webhookKey:'k'.repeat(40),authenticate:async token=>token==='verified-token'?{uid:'A'}:null,logger:{error(){}}});
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));t.after(()=>new Promise(resolve=>server.close(resolve)));
 const base='http://127.0.0.1:'+server.address().port;
 const post=(auth)=>fetch(base+'/v1/topups/cancel',{method:'POST',headers:{'Content-Type':'application/json',...(auth?{Authorization:auth}:{})},body:JSON.stringify({topupId:'NAP0123456789ABCDEF',confirmUnpaid:true})});
 assert.equal((await post()).status,401);assert.equal(cancelled,0);
 const response=await post('Bearer verified-token');assert.equal(response.status,200);assert.equal((await response.json()).data.status,'CANCELLED');assert.equal(cancelled,1);
 assert.equal((await (await fetch(base+'/health')).json()).apiVersion,3);
});

test('exchange endpoint authenticates the actual host before locking both posts',async t=>{
 const {WalletService}=require('../wallet-service');const {FakeFirestore,Timestamp}=require('./fake-firestore');const db=new FakeFirestore();
 for(const uid of ['A','B','C'])db.docs.set('accounts/'+uid,{accountId:uid,status:'ACTIVE',role:'USER'});
 db.docs.set('posts/target',{accountId:'B',status:'APPROVED'});db.docs.set('posts/offered',{accountId:'A',status:'APPROVED'});db.docs.set('exchanges/swap',{requester_accountId:'A',host_accountId:'B',postId:'target',offeredPostId:'offered',exchangeType:'RECIPROCAL',status:'PENDING'});
 const service=new WalletService(db,Timestamp,{adminUid:'admin',bankCode:'MBBank'}),server=createServer({service,webhookKey:'k'.repeat(40),authenticate:async token=>['A','B','C'].includes(token)?{uid:token}:null,logger:{error(){}}});
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));t.after(()=>new Promise(resolve=>server.close(resolve)));const base='http://127.0.0.1:'+server.address().port;
 const decide=uid=>fetch(base+'/v1/exchanges/update',{method:'POST',headers:{'Content-Type':'application/json',...(uid?{Authorization:'Bearer '+uid}:{})},body:JSON.stringify({exchangeId:'swap',updates:{status:'ACCEPTED'}})});
 assert.equal((await decide()).status,401);assert.equal((await decide('A')).status,403);assert.equal((await decide('C')).status,403);const accepted=await decide('B');assert.equal(accepted.status,200);assert.equal((await accepted.json()).data.status,'ACCEPTED');assert.equal(db.docs.get('posts/target').bookingKey,'exchanges/swap');assert.equal(db.docs.get('posts/offered').bookingKey,'exchanges/swap');assert.equal((await(await fetch(base+'/health')).json()).postReservationVersion,1);
});
