'use strict';
const c=require('./core');
const {postIds,OPEN}=require('./post-reservations');
const upper=v=>String(v||'').toUpperCase();
const identity=b=>JSON.stringify([b.postId,b.targetPostId,b.requester_accountId,b.requesterId,b.senderId,b.host_accountId,b.hostId,b.recipientId]);
async function pendingRequestsForPost(service,tx,postId,acceptedKey){
  const found=new Map();
  for(const [collection,fields] of [['exchanges',['postId','targetPostId']],['exchangeRequests',['targetPostId']]]){
    for(const field of fields){
      const rows=await tx.get(service.db.collection(collection).where(field,'==',postId));
      for(const doc of rows.docs){
        const key=collection+'/'+doc.id,data=doc.data();
        if(key===acceptedKey||upper(data.status)!=='PENDING'||data.walletManaged===true
          ||upper(data.exchangeType||'RECIPROCAL')!=='RECIPROCAL')continue;
        found.set(key,{key,collection,ref:doc.ref,data,postId});
      }
    }
  }
  const pending=[];
  for(const entry of found.values()){
    let requesterUid=entry.data.requesterUid||'';
    try{requesterUid=(await service.reservations.parties(entry.data)).a.uid||requesterUid;}catch{}
    pending.push({...entry,requesterUid});
  }
  return pending;
}
async function updateExchange(service,actor,input){
  const collection=input.collection||'exchanges';c.must(['exchanges','exchangeRequests'].includes(collection),'Nguồn yêu cầu không hợp lệ.');
  const id=c.key(input.exchangeId),ref=service.ref(collection+'/'+id),initial=await ref.get();
  c.must(initial.exists,'Yêu cầu không còn tồn tại.',404);const original=initial.data();
  c.must(!original.walletManaged,'Giao dịch ví phải xử lý trong chi tiết thanh toán.',409);
  const parties=await service.reservations.parties(original),host=actor.uid===parties.h.uid,guest=actor.uid===parties.a.uid;
  c.must(host||guest||actor.isModerator,'Bạn không có quyền xử lý yêu cầu này.',403);
  const review=typeof input.cancelDecision==='boolean',updates=input.updates||{};
  c.must(updates&&typeof updates==='object'&&!Array.isArray(updates),'Thay đổi không hợp lệ.');
  c.must(Object.keys(updates).every(k=>['status','exchangeType','note','cancelReason','offeredPostId','proposedPostId'].includes(k)),
    'Không được thay đổi thông tin thanh toán hoặc chủ yêu cầu.');
  return service.db.runTransaction(async tx=>{
    const snap=await tx.get(ref);c.must(snap.exists,'Yêu cầu không còn tồn tại.',404);const b=snap.data();
    c.must(identity(b)===identity(original)&&!b.walletManaged,'Yêu cầu đã thay đổi. Vui lòng tải lại.',409);
    const before=upper(b.status),after=review?(input.cancelDecision?'CANCELLED':before):upper(updates.status||before);
    const type=upper(updates.exchangeType||b.exchangeType||'RECIPROCAL');let changes={...updates,updatedAt:service.time()};
    if(review){
      c.must(actor.isModerator,'Chỉ quản trị viên được duyệt yêu cầu hủy.',403);
      c.must(before!=='COMPLETED','Trao đổi đã hoàn tất; cần quản trị đối soát riêng.',409);
      const pending=upper(b.cancelRequestStatus||b.cancellationStatus||b.cancelStatus||(before==='CANCEL_REQUESTED'?'PENDING':''));
      if(pending===(input.cancelDecision?'ACCEPTED':'REJECTED'))return {...b,exchangeId:id};
      c.must(pending==='PENDING','Yêu cầu hủy đã được xử lý.',409);
      changes={cancelRequestStatus:input.cancelDecision?'ACCEPTED':'REJECTED',cancelReviewedAt:service.time(),updatedAt:service.time()};
      if(input.cancelDecision)changes.status='CANCELLED';
    }else{
      c.must(!['CANCELLED','REJECTED','COMPLETED'].includes(before)||after===before,'Yêu cầu đã kết thúc.',409);
      if(after!==before){
        if(after==='ACCEPTED')c.must(host&&before==='PENDING'&&type==='RECIPROCAL','Chỉ chủ nhà được chốt trao đổi hai chiều; thuê bằng tiền phải thanh toán qua ví.',403);
        else if(after==='REJECTED')c.must(host&&before==='PENDING','Chỉ chủ nhà được từ chối yêu cầu đang chờ.',403);
        else if(after==='CANCELLED')c.must(host||guest,'Chỉ hai bên được hủy trao đổi.',403);
        else c.must(false,'Không thể đổi sang trạng thái này.',409);
      }
      if(updates.exchangeType)c.must(host&&before==='PENDING'&&after==='PENDING'&&['RECIPROCAL','NON_RECIPROCAL'].includes(type),'Không thể đổi loại trao đổi lúc này.',409);
      if(updates.offeredPostId||updates.proposedPostId)c.must(guest&&before==='PENDING'&&after==='PENDING'&&type==='RECIPROCAL','Chỉ người gửi được đổi bài đề xuất khi đang chờ.',403);
      changes.status=collection==='exchangeRequests'?after.toLowerCase():after;
    }
    const next={...b,...changes};let posts=[],autoRejected=[];
    if(!review&&after==='ACCEPTED'){
      c.must(parties.a.uid!==parties.h.uid,'Không thể trao đổi với chính mình.',409);
      const ids=postIds(next);c.must(ids.length===2,'Trao đổi hai chiều cần hai bài đăng khác nhau.',409);
      for(let i=0;i<ids.length;i++){
        const p=await tx.get(service.ref('posts/'+c.key(ids[i]))),expected=i===0?parties.h:parties.a;
        c.must(p.exists&&[expected.uid,expected.accountId].includes(p.get('accountId')||p.get('ownerId')),'Chủ bài đăng không khớp hai bên trao đổi.',409);
      }
      posts=await service.reservations.plan(tx,next,collection+'/'+id,parties.ids,'CONFIRMED');
      const rejectedByKey=new Map();
      for(const acceptedPostId of postIds(next)){
        for(const rejected of await pendingRequestsForPost(service,tx,acceptedPostId,collection+'/'+id))
          rejectedByKey.set(rejected.key,rejected);
      }
      autoRejected=[...rejectedByKey.values()];
      changes.acceptedAt=b.acceptedAt||service.time();if(collection==='exchanges')Object.assign(changes,{requesterUid:parties.a.uid,hostUid:parties.h.uid});
    }else if(['CANCELLED','REJECTED'].includes(after))posts=await service.reservations.release(tx,b,collection+'/'+id);
    else if(updates.offeredPostId||updates.proposedPostId){
      const proposed=updates.offeredPostId||updates.proposedPostId,p=await tx.get(service.ref('posts/'+c.key(proposed)));
      c.must(p.exists&&[parties.a.uid,parties.a.accountId].includes(p.get('accountId')||p.get('ownerId'))&&OPEN.includes(upper(p.get('status')))&&!p.get('bookingKey'),'Bài đề xuất không còn sẵn sàng hoặc không thuộc về bạn.',409);
      c.must((await service.reservations.competing(tx,proposed,collection+'/'+id)).size===0,'Bài đề xuất đã có trao đổi.',409);
      changes.offeredPostId=proposed;changes.proposedPostId=proposed;
    }
    service.reservations.write(tx,posts);tx.update(ref,changes);
    for(const rejected of autoRejected){
      tx.update(rejected.ref,{status:rejected.collection==='exchangeRequests'?'rejected':'REJECTED',updatedAt:service.time()});
      if(rejected.requesterUid)tx.set(service.ref('notifications/exchange_auto_reject_'+c.hash(rejected.key)),
        {userId:rejected.requesterUid,recipientId:rejected.requesterUid,title:'Yêu cầu trao đổi bị từ chối',
          body:'Chủ nhà đã chấp nhận một yêu cầu khác cho bài đăng này.',message:'Chủ nhà đã chấp nhận một yêu cầu khác cho bài đăng này.',
          type:'exchange_request',exchangeId:rejected.key.split('/')[1],postId:rejected.postId,isRead:false,createdAt:service.time()});
    }
    if(before!==after||review){
      const title=review?(input.cancelDecision?'Yêu cầu hủy đã được chấp nhận':'Yêu cầu hủy bị từ chối'):
        after==='ACCEPTED'?'Trao đổi đã được chốt':after==='REJECTED'?'Đề nghị trao đổi bị từ chối':'Trao đổi đã được hủy';
      for(const uid of [...new Set([parties.a.uid,parties.h.uid])])tx.set(service.ref('notifications/exchange_'+c.hash(collection+'/'+id+'/'+after+'/'+(review?input.cancelDecision:'party')+'/'+uid)),
        {userId:uid,recipientId:uid,title,body:title,message:title,type:'exchange_request',exchangeId:id,postId:b.postId||b.targetPostId,isRead:false,createdAt:service.time()});
    }
    return {...b,...changes,exchangeId:id};
  });
}
module.exports={updateExchange};
