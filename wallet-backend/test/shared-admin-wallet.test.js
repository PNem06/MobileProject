'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const {WalletService}=require('../wallet-service');
const {createServer}=require('../server');
const {FakeFirestore,Timestamp}=require('./fake-firestore');

function setup(schema=1){
  const db=new FakeFirestore();
  const config={adminUid:'treasury',bankCode:'MBBank',bankAccount:'123456789',receiveAccount:'123456789',accountName:'TEAM',topupMinutes:15};
  for(const uid of ['treasury','admin2','customer','other'])db.docs.set('accounts/'+uid,
    {accountId:uid,fullName:uid,role:['treasury','admin2'].includes(uid)?'ADMIN':'USER',status:'ACTIVE'});
  if(schema===2)db.docs.set('payments/_system',{recordType:'WALLET_SYSTEM',storageSchema:2,mode:'READY'});
  const internal=name=>schema===2?'payments/_system/'+name:{ledger:'walletLedger',bankEvents:'walletBankEvents'}[name];
  db.docs.set('wallets/treasury',{userId:'treasury',available:2000,held:12000,currency:'VND'});
  db.docs.set('wallets/admin2',{userId:'admin2',available:99000,held:3000,currency:'VND'});
  db.docs.set('wallets/customer',{userId:'customer',available:50000,held:0,currency:'VND'});
  db.docs.set(internal('ledger')+'/fee',{userId:'treasury',amount:2000,pocket:'available',title:'Phí hủy',createdAt:Timestamp.fromMillis(100)});
  db.docs.set(internal('ledger')+'/private',{userId:'customer',amount:50000,pocket:'available',createdAt:Timestamp.fromMillis(90)});
  db.docs.set(internal('bankEvents')+'/unmatched',{id:'bank-event',result:'UNMATCHED',createdAt:Timestamp.fromMillis(110)});
  db.docs.set('exchanges/shared',{walletManaged:true,requesterUid:'customer',hostUid:'other',adminUid:'treasury',amount:12000,settlementState:'HELD',createdAt:Timestamp.fromMillis(100)});
  db.docs.set('exchanges/unrelated',{walletManaged:true,requesterUid:'other',hostUid:'third',amount:1000,createdAt:Timestamp.fromMillis(99)});
  db.docs.set('notifications/treasury-notice',{userId:'treasury',walletManaged:true,title:'Tiền đang giữ',createdAt:Timestamp.fromMillis(100)});
  db.docs.set('notifications/private-notice',{userId:'admin2',walletManaged:false,title:'Hồ sơ riêng',createdAt:Timestamp.fromMillis(100)});
  const service=new WalletService(db,Timestamp,config,()=>1000);
  const snapshot=async uid=>service.snapshot(await service.actor(uid));
  return {db,service,config,snapshot};
}

for(const schema of [1,2]){
  test('schema '+schema+': every active admin sees the same treasury without merging or duplicating funds',async()=>{
    const h=setup(schema),before=new Map(h.db.docs);
    const [first,second]=await Promise.all([h.snapshot('treasury'),h.snapshot('admin2')]);
    assert.equal(first.userId,'treasury');assert.equal(second.userId,'admin2');
    for(const s of [first,second]){
      assert.equal(s.isAdmin,true);assert.equal(s.walletScope,'SYSTEM');assert.equal(s.walletOwnerId,'treasury');
      assert.equal(s.sharedAdminWalletVersion,1);assert.equal(s.wallet.available,2000);assert.equal(s.wallet.held,12000);
    }
    for(const key of ['wallet','ledger','bookings','bankEvents','notifications','topups','bank'])assert.deepEqual(first[key],second[key],key);
    assert.deepEqual(h.db.docs,before);assert.equal(h.db.docs.get('wallets/admin2').available,99000);
  });
  test('schema '+schema+': regular users retain personal balances and cannot see shared bank events or unrelated requests',async()=>{
    const h=setup(schema),s=await h.snapshot('customer');
    assert.equal(s.isAdmin,false);assert.equal(s.walletScope,'PERSONAL');assert.equal(s.walletOwnerId,'customer');
    assert.equal(s.wallet.available,50000);assert.deepEqual(s.ledger.map(x=>x.ledgerId),['private']);
    assert.deepEqual(s.bookings.map(x=>x.exchangeId),['shared']);assert.deepEqual(s.bankEvents,[]);assert.deepEqual(s.notifications,[]);
  });
}

test('a new admin gets the shared wallet without creating an empty personal wallet',async()=>{
  const h=setup();h.db.docs.delete('wallets/admin2');const s=await h.snapshot('admin2');
  assert.equal(s.wallet.held,12000);assert(!h.db.docs.has('wallets/admin2'));
});
test('subsequent refreshes use the same current balance and ledger for both admins',async()=>{
  const h=setup();await h.snapshot('admin2');h.db.docs.get('wallets/treasury').available=4000;
  h.db.docs.set('walletLedger/new-fee',{userId:'treasury',amount:2000,createdAt:Timestamp.fromMillis(101)});
  const [a,b]=await Promise.all([h.snapshot('treasury'),h.snapshot('admin2')]);
  assert.equal(a.wallet.available,4000);assert.deepEqual(a.wallet,b.wallet);assert.deepEqual(a.ledger,b.ledger);
});
test('locked admins, a non-admin treasury login and missing profiles cannot open the shared wallet',async()=>{
  const h=setup();h.db.docs.get('accounts/admin2').status='LOCKED';
  await assert.rejects(h.snapshot('admin2'),e=>e.status===403);
  h.db.docs.get('accounts/treasury').role='USER';await assert.rejects(h.snapshot('treasury'),e=>e.status===403);
  await assert.rejects(h.snapshot('missing'),e=>e.status===403);
});
test('admin privileges follow the current server profile, including a legacy linked Google UID',async()=>{
  const h=setup();h.db.docs.set('accounts/legacy-admin',{accountId:'legacy-admin',googleUid:'legacy-login',role:'ADMIN',status:'ACTIVE'});
  assert.equal((await h.snapshot('legacy-login')).walletOwnerId,'treasury');
  h.db.docs.get('accounts/admin2').role='USER';const s=await h.snapshot('admin2');
  assert.equal(s.isAdmin,false);assert.equal(s.walletOwnerId,'admin2');assert.equal(s.wallet.available,99000);
});
test('admin clients cannot spend or top up the system wallet through personal finance endpoints',async()=>{
  const h=setup(),before=new Map(h.db.docs);
  for(const uid of ['treasury','admin2']){
    const actor=await h.service.actor(uid);
    await assert.rejects(h.service.createTopup(actor,{amount:10000,operationId:'admin_topup_00000001'}),e=>e.status===403);
    await assert.rejects(h.service.pay(actor,{postId:'room',expectedAmount:10000,operationId:'admin_pay_000000001'}),e=>e.status===403);
    await assert.rejects(h.service.action(actor,{exchangeId:'shared',action:'CANCEL'}),e=>e.status===403);
  }
  assert.deepEqual(h.db.docs,before);
});
test('HTTP snapshots authorize by verified identity and profile, ignoring forged wallet owner or role in JSON',async t=>{
  const h=setup();const server=createServer({service:h.service,webhookKey:'k'.repeat(40),authenticate:async token=>
    ['treasury','admin2','customer'].includes(token)?{uid:token}:null,logger:{error(){}}});
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));t.after(()=>new Promise(resolve=>server.close(resolve)));
  const base='http://127.0.0.1:'+server.address().port;
  const request=uid=>fetch(base+'/v1/snapshot',{method:'POST',headers:{'Content-Type':'application/json',...(uid?{Authorization:'Bearer '+uid}:{})},
    body:JSON.stringify({userId:'treasury',walletOwnerId:'treasury',role:'ADMIN',isAdmin:true})});
  assert.equal((await request('')).status,401);
  const admin=await(await request('admin2')).json();assert.equal(admin.data.userId,'admin2');assert.equal(admin.data.walletOwnerId,'treasury');
  const user=await(await request('customer')).json();assert.equal(user.data.isAdmin,false);assert.equal(user.data.walletOwnerId,'customer');
  assert.equal(user.data.wallet.available,50000);assert.deepEqual(user.data.bankEvents,[]);
  h.db.docs.get('accounts/admin2').status='LOCKED';assert.equal((await request('admin2')).status,403);
  const health=await(await fetch(base+'/health')).json();assert.equal(health.sharedAdminWalletVersion,1);assert.equal(health.backendVersion,'1.4.1');
});
