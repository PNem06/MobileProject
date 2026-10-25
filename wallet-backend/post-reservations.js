'use strict';
const c=require('./core');
const OPEN=['APPROVED','PUBLIC','ACTIVE'];
const upper=v=>String(v||'').toUpperCase();
function postIds(b){return [...new Set([b.postId||b.targetPostId,
  upper(b.exchangeType||'RECIPROCAL')==='RECIPROCAL'?(b.offeredPostId||b.proposedPostId):''].filter(Boolean))];}
function claimsPost(b){return !['REJECTED','CANCELLED'].includes(upper(b.status))
  && (['ACCEPTED','ONGOING','COMPLETED','CANCEL_REQUESTED'].includes(upper(b.status))
    || (b.walletManaged===true&&b.settlementState==='HELD'));}
/** One shared post lock for wallet checkout and reciprocal acceptance. */
class PostReservations {
  constructor(service){this.s=service;this.db=service.db;}
  async parties(b){
    const [a,h]=await Promise.all([
      this.s.owner(b.requester_accountId||b.requesterId||b.requesterUid||b.senderId),
      this.s.owner(b.host_accountId||b.hostId||b.hostUid||b.recipientId)]);
    return {a,h,ids:[...new Set([a.uid,a.accountId,h.uid,h.accountId])]};
  }
  async competing(tx,postId,key){
    const found=new Map();
    for(const [collection,fields] of [['exchanges',['postId','offeredPostId','proposedPostId']],['exchangeRequests',['targetPostId','offeredPostId']]]){
      for(const field of fields){const rows=await tx.get(this.db.collection(collection).where(field,'==',postId));
        for(const d of rows.docs){const other=collection+'/'+d.id;
          if(other!==key&&claimsPost(d.data())&&postIds(d.data()).includes(postId))found.set(other,d.data());}}
    }
    return found;
  }
  async plan(tx,b,key,ids,state){
    const changes=[];
    for(const id of postIds(b)){
      const ref=this.s.ref('posts/'+c.key(id)),snap=await tx.get(ref);c.must(snap.exists,'Bài đăng không còn tồn tại.',409);
      const p=snap.data();c.must(!p.bookingKey||p.bookingKey===key,'Bài đăng đã có trao đổi khác. Không thể đặt trùng.',409);
      c.must((await this.competing(tx,id,key)).size===0,'Bài đăng đã có trao đổi được chốt. Vui lòng chọn bài khác.',409);
      c.must(OPEN.includes(upper(p.status))||(upper(p.status)==='BOOKED'&&p.bookingKey===key),'Bài đăng chưa sẵn sàng để trao đổi.',409);
      changes.push({ref,data:{status:'BOOKED',bookingKey:key,bookingExchangeId:key.split('/')[1],bookingPartyIds:ids,
        bookingState:state,bookingPreviousStatus:p.bookingPreviousStatus||(OPEN.includes(upper(p.status))?p.status:'APPROVED'),updatedAt:this.s.time()}});
    }
    return changes;
  }
  async release(tx,b,key){
    const changes=[];
    for(const id of postIds(b)){
      const ref=this.s.ref('posts/'+c.key(id)),snap=await tx.get(ref);
      if(!snap.exists||snap.get('bookingKey')!==key)continue;
      changes.push({ref,data:{status:upper(snap.get('status'))==='BOOKED'?(snap.get('bookingPreviousStatus')||'APPROVED'):snap.get('status'),
        bookingKey:'',bookingExchangeId:'',bookingPartyIds:[],bookingState:'',bookingPreviousStatus:'',updatedAt:this.s.time()}});
    }
    return changes;
  }
  write(tx,changes){for(const row of changes)tx.update(row.ref,row.data);}
  /** Upgrade existing reservations only; never choose a winner or move money for conflicting history. */
  async synchronize(){
    const byPost=new Map(),invalid=[];
    for(const collection of ['exchanges','exchangeRequests']){
      const rows=await this.db.collection(collection).get();
      for(const doc of rows.docs){const b=doc.data();if(!claimsPost(b))continue;
        for(const id of postIds(b)){
          if(typeof id!=='string'||!/^[A-Za-z0-9_-]{1,128}$/.test(id)){invalid.push(collection+'/'+doc.id);continue;}
          if(!byPost.has(id))byPost.set(id,[]);byPost.get(id).push({key:collection+'/'+doc.id,b});}}
    }
    const result={locked:0,conflicts:[],missing:[],invalid};
    for(const [id,claims] of byPost){
      const parties=await Promise.all(claims.map(x=>this.parties(x.b).then(p=>p.ids).catch(()=>[])));
      const changed=await this.db.runTransaction(async tx=>{
        const ref=this.s.ref('posts/'+id),snap=await tx.get(ref);if(!snap.exists)return 'missing';
        const live=[];
        for(let i=0;i<claims.length;i++){const row=await tx.get(this.s.ref(claims[i].key));
          if(row.exists&&claimsPost(row.data())&&postIds(row.data()).includes(id))live.push({key:claims[i].key,b:row.data(),ids:parties[i]});}
        if(!live.length)return false;const p=snap.data();
        if(p.bookingKey==='conflict/'+id)return 'conflict';
        if(p.bookingKey&&!live.some(x=>x.key===p.bookingKey))return false;
        const badParty=live.some(x=>!x.ids.length||!x.ids.includes(p.accountId||p.ownerId));
        const conflict=live.length>1||badParty,ids=badParty?[]:[...new Set(live.flatMap(x=>x.ids))];
        const key=conflict?'conflict/'+id:live[0].key;
        const state=conflict?'CONFLICT':upper(live[0].b.status)==='COMPLETED'?'COMPLETED':upper(live[0].b.status)==='PENDING'?'HELD':'CONFIRMED';
        if(p.bookingKey===key&&p.bookingState===state)return false;
        tx.update(ref,{status:OPEN.includes(upper(p.status))||upper(p.status)==='BOOKED'?'BOOKED':(p.status||'BOOKED'),
          bookingKey:key,bookingExchangeId:conflict?'':key.split('/')[1],bookingPartyIds:ids,bookingState:state,
          bookingPreviousStatus:p.bookingPreviousStatus||(OPEN.includes(upper(p.status))?p.status:'APPROVED'),updatedAt:this.s.time()});
        return conflict?'conflict':true;
      });
      if(changed==='missing')result.missing.push(id);else if(changed==='conflict')result.conflicts.push(id);else if(changed)result.locked++;
    }
    return result;
  }
}
module.exports={PostReservations,postIds,claimsPost,OPEN};
