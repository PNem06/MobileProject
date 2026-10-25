import {after,before,beforeEach,test} from 'node:test';
import {readFile} from 'node:fs/promises';
import {initializeTestEnvironment,assertSucceeds,assertFails} from '@firebase/rules-unit-testing';
import {doc,setDoc,getDoc,getDocs,collection,query,where,updateDoc,deleteDoc,writeBatch,serverTimestamp} from 'firebase/firestore';
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
