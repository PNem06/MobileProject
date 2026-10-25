import {after,before,beforeEach,test} from 'node:test';
import {readFile} from 'node:fs/promises';
import {initializeTestEnvironment,assertSucceeds,assertFails} from '@firebase/rules-unit-testing';
import {doc,setDoc,getDoc,getDocs,collection,query,where,limit,updateDoc,deleteDoc,writeBatch,serverTimestamp} from 'firebase/firestore';
let env;const projectId='demo-stayexchange-rebuild';const db=uid=>env.authenticatedContext(uid).firestore();
const account=(uid,role='USER',status='ACTIVE')=>({accountId:uid,role,status});
const exchange=wallet=>({requester_accountId:'A',host_accountId:'B',requesterId:'A',hostId:'B',postId:'room',exchangeType:'NON_RECIPROCAL',status:'ACCEPTED',cancelRequestStatus:'PENDING',...(wallet?{walletManaged:true,paymentStatus:'PAID',amount:10000,settlementState:'HELD',requesterUid:'A',hostUid:'B'}:{})});
before(async()=>{env=await initializeTestEnvironment({projectId,firestore:{host:'127.0.0.1',port:8085,rules:await readFile(new URL('../firestore.wallet.rules',import.meta.url),'utf8')}});});
beforeEach(async()=>{await env.clearFirestore();await env.withSecurityRulesDisabled(async ctx=>{
 const store=ctx.firestore(),seed={
  'accounts/A':account('A'),'accounts/B':account('B'),'accounts/C':account('C'),'accounts/admin':account('admin','ADMIN'),'accounts/locked':account('locked','ADMIN','LOCKED'),
  'posts/room':{accountId:'B',status:'APPROVED',title:'Room',price:10000},'posts/offered':{accountId:'A',status:'APPROVED',title:'Offered',price:10000},
  'exchanges/wallet':exchange(true),'exchanges/legacy':exchange(false),'wallets/A':{available:30000,held:0},'wallets/B':{available:0,held:0},
  'payments/legacy':{exchangeId:'legacy',status:'PAID',amount:10000},'reports/report':{targetType:'POST',target_postId:'room',status:'PENDING'},
  'notifications/walletNotice':{userId:'A',walletManaged:true,isRead:false},'accounts/A/notifications/oldNotice':{accountId:'A',isRead:false}};
 await Promise.all(Object.entries(seed).map(([p,v])=>setDoc(doc(store,p),v)));
});});
after(async()=>{if(env)await env.cleanup();});
test('wallet owners can read but no Android client can modify balances or ledgers',async()=>{
 await assertSucceeds(getDoc(doc(db('A'),'wallets/A')));await assertFails(getDoc(doc(db('B'),'wallets/A')));await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(),'wallets/A')));
 for(const uid of ['A','admin']){await assertFails(updateDoc(doc(db(uid),'wallets/A'),{available:999999}));await assertFails(setDoc(doc(db(uid),'walletLedger/forged'),{userId:'A',amount:10000}));}
});
test('wallet exchange reads are limited to participants and active admin; writes are server-only',async()=>{
 for(const uid of ['A','B','admin']){await assertSucceeds(getDoc(doc(db(uid),'exchanges/wallet')));await assertFails(updateDoc(doc(db(uid),'exchanges/wallet'),{status:'CANCELLED'}));await assertFails(deleteDoc(doc(db(uid),'exchanges/wallet')));}await assertFails(getDoc(doc(db('C'),'exchanges/wallet')));
});
test('old admin cancellation batches cannot bypass atomic server post release',async()=>{
 const store=db('admin'),b=writeBatch(store);b.update(doc(store,'exchanges/legacy'),{status:'CANCELLED',cancelRequestStatus:'ACCEPTED',cancelReviewedAt:serverTimestamp()});b.set(doc(store,'notifications/cancelled'),{userId:'A',type:'EXCHANGE_CANCEL_ACCEPTED'});await assertFails(b.commit());
 await env.withSecurityRulesDisabled(async ctx=>{if((await getDoc(doc(ctx.firestore(),'notifications/cancelled'))).exists())throw Error('Rejected batch wrote a notice');});
});
test('legacy decisions and financial fields remain server-only even for admin clients',async()=>{
 await assertFails(updateDoc(doc(db('admin'),'exchanges/legacy'),{cancelRequestStatus:'REJECTED'}));await assertFails(updateDoc(doc(db('admin'),'exchanges/legacy'),{amount:1,status:'CANCELLED'}));await assertFails(updateDoc(doc(db('A'),'exchanges/legacy'),{cancelRequestStatus:'ACCEPTED'}));
});
test('wallet upgrade prevents a stale legacy admin cancellation batch',async()=>{
 const store=db('admin'),b=writeBatch(store);b.update(doc(store,'exchanges/wallet'),{status:'CANCELLED',cancelRequestStatus:'ACCEPTED'});b.set(doc(store,'notifications/wrongRefund'),{userId:'A',type:'EXCHANGE_CANCEL_ACCEPTED'});await assertFails(b.commit());
});
test('admin can list combined exchanges and old payments without financial write permission',async()=>{
 await assertSucceeds(getDocs(collection(db('admin'),'exchanges')));await assertSucceeds(getDocs(collection(db('admin'),'payments')));await assertFails(getDocs(collection(db('A'),'payments')));await assertFails(updateDoc(doc(db('admin'),'payments/legacy'),{amount:1}));
});
test('report moderation and old nested notifications stay compatible',async()=>{
 const store=db('admin'),b=writeBatch(store);b.update(doc(store,'posts/room'),{status:'HIDDEN'});b.update(doc(store,'reports/report'),{status:'RESOLVED'});b.set(doc(store,'accounts/B/notifications/hidden'),{accountId:'B',type:'POST_HIDDEN',isRead:false});b.set(doc(store,'notifications/hidden'),{userId:'B',recipientId:'B',type:'POST_HIDDEN',isRead:false});await assertSucceeds(b.commit());await assertSucceeds(getDoc(doc(db('B'),'accounts/B/notifications/hidden')));await assertSucceeds(getDocs(query(collection(db('B'),'notifications'),where('userId','==','B'))));await assertFails(getDoc(doc(db('A'),'accounts/B/notifications/hidden')));
});
test('post owner edits return a post for review and cannot approve or change its owner',async()=>{
 await assertSucceeds(updateDoc(doc(db('B'),'posts/room'),{title:'Edited',status:'PENDING'}));await assertFails(updateDoc(doc(db('B'),'posts/room'),{status:'APPROVED'}));await assertFails(updateDoc(doc(db('B'),'posts/room'),{accountId:'A'}));
});
test('wallet notices are created only by the server and recipients may mark them read',async()=>{
 await assertSucceeds(updateDoc(doc(db('A'),'notifications/walletNotice'),{isRead:true}));await assertFails(updateDoc(doc(db('A'),'notifications/walletNotice'),{amount:1}));await assertFails(setDoc(doc(db('admin'),'notifications/fake'),{userId:'A',walletManaged:true}));await assertFails(getDoc(doc(db('B'),'notifications/walletNotice')));
});
test('locked admins lose report and payment moderation access',async()=>{
 await assertFails(getDocs(collection(db('locked'),'payments')));await assertFails(updateDoc(doc(db('locked'),'reports/report'),{status:'RESOLVED'}));await assertFails(updateDoc(doc(db('locked'),'exchanges/legacy'),{status:'CANCELLED'}));
});
const pending={requester_accountId:'A',requesterId:'A',host_accountId:'B',hostId:'B',postId:'room',offeredPostId:'offered',status:'PENDING',exchangeType:'RECIPROCAL'};
test('pending reciprocal creation checks ownership and refuses paid or forged participant fields',async()=>{
 await assertSucceeds(setDoc(doc(db('A'),'exchanges/newSwap'),pending));await assertFails(setDoc(doc(db('A'),'exchanges/fakePaid'),{...pending,walletManaged:true,paymentStatus:'PAID'}));await assertFails(setDoc(doc(db('A'),'exchanges/fakeGuest'),{...pending,requesterId:'C'}));await assertFails(setDoc(doc(db('A'),'exchanges/fakeHost'),{...pending,hostId:'C'}));
});
test('host-filtered received queries work without exposing other hosts',async()=>{
 await assertSucceeds(getDocs(query(collection(db('B'),'exchanges'),where('postId','==','room'),where('host_accountId','==','B'))));await assertFails(getDocs(query(collection(db('C'),'exchanges'),where('postId','==','room'))));
});
test('profile identity, roles and funded accounts cannot be replaced by client writes',async()=>{
 await assertFails(updateDoc(doc(db('A'),'accounts/A'),{role:'ADMIN'}));await assertFails(updateDoc(doc(db('A'),'accounts/A'),{accountId:'B'}));await assertFails(deleteDoc(doc(db('admin'),'accounts/A')));await assertSucceeds(updateDoc(doc(db('admin'),'accounts/C'),{status:'LOCKED'}));
});
const participants={participant1Id:'A',participant2Id:'B'};
function messageBatch(store,id,sender,content='Hello'){const b=writeBatch(store);b.set(doc(store,'conversations/A_B'),{...participants,lastMessage:content,lastMessageSenderId:sender,lastMessageAt:serverTimestamp(),isRead:false},{merge:true});b.set(doc(store,'conversations/A_B/messages/'+id),{senderId:sender,content,sentAt:serverTimestamp()});return b;}
test('first chat message creates its conversation and message atomically',async()=>{
 await assertFails(setDoc(doc(db('A'),'conversations/A_B/messages/orphan'),{senderId:'A',content:'No parent'}));await assertSucceeds(messageBatch(db('A'),'first','A').commit());await assertSucceeds(getDoc(doc(db('B'),'conversations/A_B/messages/first')));await assertFails(getDoc(doc(db('C'),'conversations/A_B')));await assertFails(getDocs(collection(db('C'),'conversations/A_B/messages')));
});
test('chat members cannot forge sender IDs or replace participants',async()=>{
 await assertSucceeds(messageBatch(db('A'),'first','A').commit());await assertFails(messageBatch(db('B'),'forged','A').commit());await assertFails(updateDoc(doc(db('A'),'conversations/A_B'),{participant2Id:'C'}));await assertSucceeds(messageBatch(db('B'),'reply','B').commit());
});
test('block-status queries work before a block exists and only involved users can read blocks',async()=>{
 await assertSucceeds(getDocs(query(collection(db('A'),'blocks'),where('blockerId','==','A'),where('blockedUserId','==','B'))));await assertSucceeds(setDoc(doc(db('A'),'blocks/A_B'),{blockId:'A_B',blockerId:'A',blockedUserId:'B',createdAt:serverTimestamp()}));await assertSucceeds(getDocs(query(collection(db('B'),'blocks'),where('blockerId','==','A'),where('blockedUserId','==','B'))));await assertFails(getDoc(doc(db('C'),'blocks/A_B')));await assertFails(deleteDoc(doc(db('B'),'blocks/A_B')));await assertSucceeds(deleteDoc(doc(db('A'),'blocks/A_B')));
});
test('either-direction blocking stops sending but retains history and read receipts',async()=>{
 await assertSucceeds(messageBatch(db('A'),'first','A').commit());await assertSucceeds(setDoc(doc(db('B'),'blocks/B_A'),{blockId:'B_A',blockerId:'B',blockedUserId:'A',createdAt:serverTimestamp()}));await assertFails(messageBatch(db('A'),'blockedA','A').commit());await assertFails(messageBatch(db('B'),'blockedB','B').commit());await assertSucceeds(getDocs(collection(db('A'),'conversations/A_B/messages')));await assertSucceeds(updateDoc(doc(db('A'),'conversations/A_B'),{isRead:true}));await assertSucceeds(deleteDoc(doc(db('B'),'blocks/B_A')));await assertSucceeds(messageBatch(db('A'),'after','A').commit());
});
test('blocked pairs cannot start chat and users cannot create blocks for another account',async()=>{
 await assertSucceeds(setDoc(doc(db('A'),'blocks/A_B'),{blockerId:'A',blockedUserId:'B',createdAt:serverTimestamp()}));await assertFails(messageBatch(db('A'),'first','A').commit());await assertFails(setDoc(doc(db('C'),'blocks/A_C'),{blockerId:'A',blockedUserId:'C'}));await assertFails(setDoc(doc(db('A'),'blocks/A_C'),{blockId:'A_B',blockerId:'A',blockedUserId:'C'}));
});
test('legacy hostId queries support trusted accountId aliases',async()=>{
 await env.withSecurityRulesDisabled(async ctx=>{await updateDoc(doc(ctx.firestore(),'accounts/B'),{accountId:'legacyB'});await setDoc(doc(ctx.firestore(),'exchanges/alias'),{requesterId:'A',hostId:'legacyB',postId:'room',status:'PENDING',exchangeType:'RECIPROCAL'});});await assertSucceeds(getDocs(query(collection(db('B'),'exchanges'),where('hostId','==','legacyB'))));await assertFails(getDocs(query(collection(db('C'),'exchanges'),where('hostId','==','legacyB'))));
});
test('legacy exchangeRequests recipient queries remain available to their participants',async()=>{
 await env.withSecurityRulesDisabled(async ctx=>{await setDoc(doc(ctx.firestore(),'exchangeRequests/old'),{senderId:'A',recipientId:'B',targetPostId:'room',offeredPostId:'offered',status:'pending'});});await assertSucceeds(getDocs(query(collection(db('B'),'exchangeRequests'),where('recipientId','==','B'))));await assertFails(getDocs(query(collection(db('C'),'exchangeRequests'),where('recipientId','==','B'))));
});
async function lockedPost(){await env.withSecurityRulesDisabled(async ctx=>{const store=ctx.firestore();await setDoc(doc(store,'posts/room'),{accountId:'B',status:'BOOKED',title:'Reserved',bookingKey:'exchanges/wallet',bookingExchangeId:'wallet',bookingPartyIds:['A','B'],bookingState:'CONFIRMED',bookingPreviousStatus:'APPROVED'});for(const source of ['images','postImages'])await setDoc(doc(store,source+'/cover'),{postId:'room',imageUrl:'https://example.com/room.jpg'});});}
test('only the booked parties and active admin can read the hidden post and image documents',async()=>{
 await lockedPost();for(const uid of ['A','B','admin']){await assertSucceeds(getDoc(doc(db(uid),'posts/room')));await assertSucceeds(getDocs(query(collection(db(uid),'images'),where('postId','==','room'))));await assertSucceeds(getDoc(doc(db(uid),'postImages/cover')));}for(const uid of ['C','locked']){await assertFails(getDoc(doc(db(uid),'posts/room')));await assertFails(getDoc(doc(db(uid),'images/cover')));await assertFails(getDocs(query(collection(db(uid),'images'),where('postId','==','room'))));}
});
test('public search excludes booked posts while owner and participant queries keep working',async()=>{
 await lockedPost();const rows=await assertSucceeds(getDocs(query(collection(db('C'),'posts'),where('status','==','APPROVED'))));if(rows.docs.some(d=>d.id==='room'))throw Error('Booked post leaked to search');await assertSucceeds(getDocs(query(collection(db('A'),'posts'),where('bookingPartyIds','array-contains','A'))));await assertSucceeds(getDocs(query(collection(db('B'),'posts'),where('accountId','==','B'))));await assertSucceeds(getDocs(query(collection(db('B'),'posts'),where('ownerId','==','B'))));await assertSucceeds(getDocs(collection(db('admin'),'posts')));await assertFails(getDocs(collection(db('C'),'posts')));
});
test('clients cannot remove locks, republish, edit images or delete reserved posts',async()=>{
 await lockedPost();for(const uid of ['A','B','admin']){await assertFails(updateDoc(doc(db(uid),'posts/room'),{bookingKey:'',bookingPartyIds:[]}));await assertFails(updateDoc(doc(db(uid),'posts/room'),{status:'APPROVED'}));await assertFails(deleteDoc(doc(db(uid),'posts/room')));}await assertFails(updateDoc(doc(db('B'),'posts/room'),{title:'Changed'}));await assertFails(deleteDoc(doc(db('B'),'images/cover')));await assertFails(updateDoc(doc(db('B'),'images/cover'),{imageUrl:'https://example.com/other.jpg'}));await assertSucceeds(updateDoc(doc(db('admin'),'posts/room'),{status:'HIDDEN'}));
});
test('forged booking fields and the old direct acceptance path are denied',async()=>{
 await assertFails(setDoc(doc(db('A'),'posts/forged'),{accountId:'A',status:'PENDING',bookingKey:'exchanges/fake'}));await assertFails(setDoc(doc(db('A'),'posts/forgedBooked'),{accountId:'A',status:'BOOKED'}));await assertSucceeds(setDoc(doc(db('A'),'exchanges/pending'),pending));for(const uid of ['A','B','admin'])await assertFails(updateDoc(doc(db(uid),'exchanges/pending'),{status:'ACCEPTED'}));
});
test('new reciprocal and money requests cannot target a reserved post',async()=>{
 await lockedPost();for(const exchangeType of ['RECIPROCAL','NON_RECIPROCAL'])await assertFails(setDoc(doc(db('A'),'exchanges/another'+exchangeType),{...pending,exchangeType}));await assertFails(setDoc(doc(db('A'),'exchangeRequests/another'),{senderId:'A',recipientId:'B',targetPostId:'room',offeredPostId:'offered',status:'pending',createdAt:serverTimestamp()}));
});
async function emulatorService(t){
 const {createRequire}=await import('node:module'),require=createRequire(import.meta.url);const {initializeApp,deleteApp}=require('firebase-admin/app');const {getFirestore,Timestamp}=require('firebase-admin/firestore');const {WalletService}=require('../wallet-service');const {updateExchange}=require('../exchange-service');
 const app=initializeApp({projectId},'reservations-'+Date.now());t.after(()=>deleteApp(app));const store=getFirestore(app),now=Date.now(),batch=store.batch();const post=accountId=>({accountId,status:'APPROVED',title:'Race',price:10000,startDate:Timestamp.fromMillis(now+86400000),endDate:Timestamp.fromMillis(now+2*86400000)});
 batch.set(store.doc('posts/raceRoom'),post('B'));batch.set(store.doc('posts/raceOffer'),post('A'));for(const uid of ['A','C'])batch.set(store.doc('wallets/'+uid),{userId:uid,available:30000,held:0});batch.set(store.doc('wallets/admin'),{userId:'admin',available:0,held:0});await batch.commit();
 const service=new WalletService(store,Timestamp,{adminUid:'admin',bankCode:'MBBank',bankAccount:'123456789',receiveAccount:'123456789',accountName:'ADMIN',topupMinutes:15},()=>now);const actor=uid=>({uid,accountId:uid,name:uid,isAdmin:false,isModerator:false});const pay=uid=>service.pay(actor(uid),{postId:'raceRoom',expectedAmount:10000,operationId:'emulator_pay_'+uid+'_0001'});return {store,service,actor,pay,updateExchange};
}
test('real Firestore transactions: two guests produce exactly one booking and one debit',async t=>{
 const h=await emulatorService(t),results=await Promise.allSettled([h.pay('A'),h.pay('C')]);if(results.filter(x=>x.status==='fulfilled').length!==1)throw Error('Expected one successful booking: '+JSON.stringify(results));const [a,c,admin,post]=await Promise.all(['wallets/A','wallets/C','wallets/admin','posts/raceRoom'].map(p=>h.store.doc(p).get()));if(a.get('available')+c.get('available')!==50000||admin.get('held')!==10000||post.get('status')!=='BOOKED')throw Error('Reservation and wallet were not atomic');await assertFails(getDoc(doc(db('locked'),'posts/raceRoom')));
});
test('real Firestore transactions: wallet payment and reciprocal acceptance cannot both win',async t=>{
 const h=await emulatorService(t);await h.store.doc('exchanges/raceSwap').set({requester_accountId:'A',host_accountId:'B',postId:'raceRoom',offeredPostId:'raceOffer',status:'PENDING',exchangeType:'RECIPROCAL'});const results=await Promise.allSettled([h.pay('C'),h.updateExchange(h.service,h.actor('B'),{exchangeId:'raceSwap',updates:{status:'ACCEPTED'}})]);if(results.filter(x=>x.status==='fulfilled').length!==1)throw Error('Expected one winning flow: '+JSON.stringify(results));const post=await h.store.doc('posts/raceRoom').get(),offer=await h.store.doc('posts/raceOffer').get(),guest=await h.store.doc('wallets/C').get(),paid=results[0].status==='fulfilled';if(offer.get('status')!==(paid?'APPROVED':'BOOKED')||post.get('status')!=='BOOKED'||guest.get('available')!==(paid?20000:30000))throw Error('Losing flow changed a post or charged the guest');
});

test('Console compatibility: anonymous username lookup keeps its limit-one access',async()=>{
 await env.withSecurityRulesDisabled(async c=>updateDoc(doc(c.firestore(),'accounts/A'),{username:'alice'}));
 const anonymous=env.unauthenticatedContext().firestore();
 const found=await assertSucceeds(getDocs(query(collection(anonymous,'accounts'),where('username','==','alice'),limit(1))));
 if(found.size!==1)throw Error('The pre-login username lookup did not find its profile');
 await assertFails(getDocs(collection(anonymous,'accounts')));await assertFails(getDocs(query(collection(anonymous,'accounts'),limit(2))));await assertFails(getDoc(doc(anonymous,'accounts/A')));
});
test('Console compatibility: registration rejects forged roles, balances and identity bindings',async()=>{
 const profile={accountId:'D',username:'d',fullName:'D',role:'USER',status:'ACTIVE',rating:0,totalExchanges:0};
 for(const bad of [{role:'ADMIN'},{accountId:'A'},{authUid:'A'},{walletBalance:1},{balance:1},{googleUid:'A'},{rating:5}])await assertFails(setDoc(doc(db('D'),'accounts/D'),{...profile,...bad}));
 await assertSucceeds(setDoc(doc(db('D'),'accounts/D'),profile));
 await assertSucceeds(updateDoc(doc(db('D'),'accounts/D'),{fullName:'Updated',googleUid:'D'}));
 await assertFails(updateDoc(doc(db('D'),'accounts/D'),{googleUid:'C'}));await assertFails(updateDoc(doc(db('admin'),'accounts/D'),{walletBalance:123}));await assertFails(deleteDoc(doc(db('D'),'accounts/D')));
});
test('Console compatibility: public listings and amenities remain public while pending and booked posts are private',async()=>{
 await env.withSecurityRulesDisabled(async c=>{await setDoc(doc(c.firestore(),'posts/draft'),{accountId:'A',status:'PENDING'});await setDoc(doc(c.firestore(),'amenities/wifi'),{name:'WiFi'});await setDoc(doc(c.firestore(),'images/publicCover'),{postId:'room',imgUrl:'public'});});
 const anonymous=env.unauthenticatedContext().firestore();
 await assertSucceeds(getDoc(doc(anonymous,'posts/room')));await assertSucceeds(getDocs(query(collection(anonymous,'posts'),where('status','==','APPROVED'))));await assertSucceeds(getDoc(doc(anonymous,'images/publicCover')));await assertSucceeds(getDocs(collection(anonymous,'amenities')));
 await assertFails(getDoc(doc(anonymous,'posts/draft')));await assertFails(getDoc(doc(db('C'),'posts/draft')));await assertSucceeds(getDoc(doc(db('A'),'posts/draft')));
 await lockedPost();await assertFails(getDoc(doc(anonymous,'posts/room')));await assertFails(getDoc(doc(anonymous,'images/cover')));
});
test('New_Post can create its parent and both image schemas in one batch',async()=>{
 const store=db('A'),batch=writeBatch(store);
 batch.set(doc(store,'posts/newRoom'),{accountId:'A',ownerId:'A',status:'PENDING',title:'New'});
 for(const source of ['images','postImages'])batch.set(doc(store,source+'/newCover'),{postId:'newRoom',imgUrl:'encoded',orderIndex:0});
 await assertSucceeds(batch.commit());await assertSucceeds(getDoc(doc(store,'images/newCover')));await assertFails(getDoc(doc(db('C'),'images/newCover')));
 await assertFails(setDoc(doc(db('C'),'images/injected'),{postId:'newRoom',imgUrl:'forged'}));
 await assertFails(setDoc(doc(store,'posts/forgedOwner'),{accountId:'A',ownerId:'B',status:'PENDING'}));
});
test('a post with many photos stays within Rules document-access limits in one batch',async()=>{
 const store=db('A'),batch=writeBatch(store);batch.set(doc(store,'posts/manyPhotos'),{accountId:'A',status:'PENDING'});
 for(let i=0;i<20;i++)batch.set(doc(store,'images/photo'+i),{postId:'manyPhotos',imgUrl:'image'+i});
 await assertSucceeds(batch.commit());const photos=await assertSucceeds(getDocs(query(collection(store,'images'),where('postId','==','manyPhotos'))));if(photos.size!==20)throw Error('Missing uploaded photos');
});
test('Detail atomically deletes the unreserved post and its image documents',async()=>{
 await env.withSecurityRulesDisabled(async c=>{for(const source of ['images','postImages'])await setDoc(doc(c.firestore(),source+'/deletable'),{postId:'room',imgUrl:'photo'});});
 const store=db('B'),images=await getDocs(query(collection(store,'images'),where('postId','==','room'))),old=await getDocs(query(collection(store,'postImages'),where('postId','==','room'))),batch=writeBatch(store);
 for(const image of [...images.docs,...old.docs])batch.delete(image.ref);batch.delete(doc(store,'posts/room'));await assertSucceeds(batch.commit());
 await env.withSecurityRulesDisabled(async c=>{for(const p of ['posts/room','images/deletable','postImages/deletable'])if((await getDoc(doc(c.firestore(),p))).exists())throw Error('Atomic delete left '+p);});
});
test('a stale Detail delete cannot remove any image after a concurrent booking',async()=>{
 await env.withSecurityRulesDisabled(async c=>setDoc(doc(c.firestore(),'images/stale'),{postId:'room',imgUrl:'keep'}));
 const store=db('B'),photos=await getDocs(query(collection(store,'images'),where('postId','==','room')));await lockedPost();
 const batch=writeBatch(store);for(const image of photos.docs)batch.delete(image.ref);batch.delete(doc(store,'posts/room'));await assertFails(batch.commit());
 await env.withSecurityRulesDisabled(async c=>{if(!(await getDoc(doc(c.firestore(),'images/stale'))).exists())throw Error('Rejected delete removed a reserved photo');});
});
test('orphan photos do not become public after their parent disappears',async()=>{
 await env.withSecurityRulesDisabled(async c=>setDoc(doc(c.firestore(),'images/orphan'),{postId:'missingParent',imgUrl:'private'}));
 for(const store of [db('A'),env.unauthenticatedContext().firestore()])await assertFails(getDoc(doc(store,'images/orphan')));
 await assertSucceeds(getDoc(doc(db('admin'),'images/orphan')));
});
test('Console compatibility: missing favorite subscriptions and owner toggles still work',async()=>{
 const store=db('A');await assertSucceeds(getDoc(doc(store,'favorites/A_room')));await assertSucceeds(setDoc(doc(store,'favorites/A_room'),{userId:'A',postId:'room'}));
 await assertSucceeds(getDocs(query(collection(store,'favorites'),where('userId','==','A'))));await assertFails(getDoc(doc(db('B'),'favorites/A_room')));await assertFails(setDoc(doc(db('B'),'favorites/A_other'),{userId:'A',postId:'other'}));await assertFails(updateDoc(doc(store,'favorites/A_room'),{postId:'offered'}));await assertSucceeds(deleteDoc(doc(store,'favorites/A_room')));
});
test('Console compatibility: nested notices preserve recipient actions and reject fake wallet notices',async()=>{
 const admin=db('admin');await assertSucceeds(setDoc(doc(admin,'accounts/B/notifications/moderation'),{accountId:'B',type:'POST_HIDDEN',isRead:false}));
 for(const extra of [{walletManaged:false},{paymentId:'fake'},{paymentStatus:'PAID'}])await assertFails(setDoc(doc(admin,'accounts/B/notifications/forged'),{accountId:'B',...extra}));
 await assertSucceeds(updateDoc(doc(db('B'),'accounts/B/notifications/moderation'),{isRead:true}));await assertFails(deleteDoc(doc(admin,'accounts/B/notifications/moderation')));await assertSucceeds(deleteDoc(doc(db('B'),'accounts/B/notifications/moderation')));
});
test('Rebuilt exchange-request notifications validate their sender and actual host',async()=>{
 await assertSucceeds(setDoc(doc(db('A'),'exchanges/pending'),pending));
 const notice={userId:'B',senderId:'A',type:'exchange_request',requestId:'pending',exchangeId:'pending',isRead:false};
 await assertSucceeds(setDoc(doc(db('A'),'notifications/validExchange'),notice));
 await assertFails(setDoc(doc(db('C'),'notifications/spoofSender'),notice));await assertFails(setDoc(doc(db('A'),'notifications/spoofHost'),{...notice,userId:'C'}));await assertFails(setDoc(doc(db('A'),'notifications/mismatchedRequest'),{...notice,requestId:'different'}));
});
test('legacy request creation and its notification can share one atomic batch',async()=>{
 const store=db('A'),batch=writeBatch(store);batch.set(doc(store,'exchangeRequests/oldNew'),{senderId:'A',recipientId:'B',targetPostId:'room',offeredPostId:'offered',status:'pending',createdAt:serverTimestamp()});batch.set(doc(store,'notifications/oldNew'),{senderId:'A',userId:'B',type:'exchange_request',requestId:'oldNew'});await assertSucceeds(batch.commit());
 await assertSucceeds(getDoc(doc(db('admin'),'exchangeRequests/oldNew')));for(const uid of ['A','B','admin'])await assertFails(updateDoc(doc(db(uid),'exchangeRequests/oldNew'),{status:'accepted'}));
});
test('Chat notifications require the actual two participants and obey blocking',async()=>{
 await assertSucceeds(messageBatch(db('A'),'first','A').commit());const notice={userId:'B',senderId:'A',type:'chat_message',conversationId:'A_B',body:'Hello'};
 await assertSucceeds(setDoc(doc(db('A'),'notifications/chat'),notice));await assertFails(setDoc(doc(db('C'),'notifications/outsider'),notice));await assertFails(setDoc(doc(db('A'),'notifications/wrongChatRecipient'),{...notice,userId:'C'}));
 await assertSucceeds(setDoc(doc(db('B'),'blocks/B_A'),{blockerId:'B',blockedUserId:'A'}));await assertFails(setDoc(doc(db('A'),'notifications/blockedChat'),notice));
});
test('root notices cannot impersonate financial events and preserve admin moderation types',async()=>{
 for(const uid of ['A','admin'])for(const extra of [{paymentId:'fake'},{paymentStatus:'PAID'},{walletManaged:false},{amount:10000},{settlementState:'HELD'}])await assertFails(setDoc(doc(db(uid),'notifications/forged'),{userId:'B',senderId:uid,type:'POST_HIDDEN',...extra}));
 for(const type of ['post_approved','post_rejected','POST_HIDDEN','ACCOUNT_LOCKED'])await assertSucceeds(setDoc(doc(db('admin'),'notifications/'+type),{userId:'B',type,isRead:false}));
 await assertFails(setDoc(doc(db('A'),'notifications/forgedAdmin'),{userId:'B',senderId:'A',type:'POST_HIDDEN'}));
});
test('pending exchanges reject extra financial fields and inconsistent aliases',async()=>{
 for(const extra of [{currency:'VND'},{acceptedAt:serverTimestamp()},{bankEventId:'fake'},{senderId:'C'},{recipientId:'C'},{targetPostId:'offered'},{proposedPostId:'room'}])await assertFails(setDoc(doc(db('A'),'exchanges/forged'),{...pending,...extra}));
 await assertFails(setDoc(doc(db('B'),'exchanges/self'),{...pending,requesterId:'B',requester_accountId:'B'}));
});
test('message read receipts remain available after blocking without allowing content edits',async()=>{
 await assertSucceeds(messageBatch(db('A'),'receipt','A').commit());await assertSucceeds(setDoc(doc(db('B'),'blocks/B_A'),{blockerId:'B',blockedUserId:'A'}));
 await assertSucceeds(updateDoc(doc(db('B'),'conversations/A_B/messages/receipt'),{isRead:true,readAt:serverTimestamp()}));
 await assertFails(updateDoc(doc(db('A'),'conversations/A_B/messages/receipt'),{content:'Changed'}));await assertFails(updateDoc(doc(db('B'),'conversations/A_B/messages/receipt'),{content:'Changed'}));await assertFails(updateDoc(doc(db('C'),'conversations/A_B/messages/receipt'),{isRead:true}));
});
test('conversations require two distinct string participants and a truthful sender',async()=>{
 await assertFails(setDoc(doc(db('A'),'conversations/self'),{participant1Id:'A',participant2Id:'A'}));await assertFails(setDoc(doc(db('A'),'conversations/number'),{participant1Id:'A',participant2Id:42}));await assertFails(setDoc(doc(db('A'),'conversations/forgedSender'),{...participants,lastMessageSenderId:'B'}));
 await assertSucceeds(messageBatch(db('A'),'truthful','A').commit());await assertFails(updateDoc(doc(db('A'),'conversations/A_B'),{lastMessage:'Forged',lastMessageSenderId:'B'}));
});
test('reports and reviews retain Console author and rating validation',async()=>{
 await assertSucceeds(setDoc(doc(db('A'),'reports/new'),{reporterId:'A',target_postId:'room'}));await assertFails(setDoc(doc(db('A'),'reports/forged'),{reporterId:'B'}));
 await assertSucceeds(setDoc(doc(db('A'),'reviews/new'),{reviewerId:'A',rating:4,comment:'Good'}));await assertSucceeds(updateDoc(doc(db('A'),'reviews/new'),{rating:5,comment:'Updated'}));await assertFails(setDoc(doc(db('A'),'reviews/forged'),{reviewerId:'B',rating:5}));await assertFails(updateDoc(doc(db('A'),'reviews/new'),{rating:6}));await assertFails(updateDoc(doc(db('B'),'reviews/new'),{comment:'Replaced'}));
});
test('booked posts honor trusted legacy account aliases without granting outsiders access',async()=>{
 await env.withSecurityRulesDisabled(async c=>{const s=c.firestore();await updateDoc(doc(s,'accounts/A'),{accountId:'legacyA'});await updateDoc(doc(s,'accounts/B'),{accountId:'legacyB'});await setDoc(doc(s,'posts/aliasRoom'),{accountId:'legacyB',status:'BOOKED',bookingKey:'exchanges/alias',bookingPartyIds:['legacyA','legacyB']});});
 for(const uid of ['A','B','admin'])await assertSucceeds(getDoc(doc(db(uid),'posts/aliasRoom')));await assertFails(getDoc(doc(db('C'),'posts/aliasRoom')));await assertFails(updateDoc(doc(db('C'),'accounts/C'),{accountId:'legacyA'}));
});
test('old conflict locks stay closed until server-side review resolves the history',async()=>{
 await env.withSecurityRulesDisabled(async c=>setDoc(doc(c.firestore(),'posts/conflict'),{accountId:'B',status:'BOOKED',bookingKey:'conflict/conflict',bookingState:'CONFLICT',bookingPartyIds:[]}));
 for(const uid of ['B','admin'])await assertSucceeds(getDoc(doc(db(uid),'posts/conflict')));await assertFails(getDoc(doc(db('C'),'posts/conflict')));await assertFails(updateDoc(doc(db('admin'),'posts/conflict'),{bookingKey:'',bookingPartyIds:['A','B'],status:'APPROVED'}));await assertFails(setDoc(doc(db('A'),'exchanges/conflictRequest'),{...pending,postId:'conflict'}));
});
