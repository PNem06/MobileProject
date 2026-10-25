'use strict';
const crypto=require('node:crypto');
const c=require('./core');
class WalletService {
  constructor(db,Timestamp,config,clock=Date.now){this.db=db;this.T=Timestamp;this.config=config;this.clock=clock;}
  ref(path){return this.db.doc(path);}
  time(ms=this.clock()){return this.T.fromMillis(ms);}
  get bank(){const spec=c.bankSpec(this.config.bankCode);c.must(spec,'BANK_CODE cần là MBBank hoặc VPBank.');return spec;}
  bankInfo(){return {...this.bank,accountNumber:this.config.receiveAccount,
    configurationId:c.hash(JSON.stringify([this.bank.code,this.config.bankAccount,this.config.receiveAccount,this.config.bankSubAccount||'']))};}
  topupMatchesBank(t){
    if(t.bankConfigId)return t.bankConfigId===this.bankInfo().configurationId;
    // Legacy VPBank deposits did not store a configuration ID.
    return c.bankSpec(t.bankCode||t.bank)?.code===this.bank.code&&t.accountNumber===this.config.receiveAccount;
  }
  async actor(uid){
    c.key(uid);
    let doc=await this.ref('accounts/'+uid).get();
    if(!doc.exists){const found=await this.db.collection('accounts').where('googleUid','==',uid).limit(2).get();
      c.must(found.docs.length===1,'Chưa tìm thấy hồ sơ duy nhất cho tài khoản đăng nhập.',403);doc=found.docs[0];}
    const data=doc.data();
    c.must(String(data.status||'ACTIVE').toUpperCase()==='ACTIVE','Tài khoản đang bị khóa.',403);
    return {uid,accountId:data.accountId||doc.id,name:data.fullName||'Thành viên',isAdmin:uid===this.config.adminUid};
  }
  async owner(accountId){
    c.key(accountId);
    // Auth UID docs created by the current app are the canonical identity.
    let doc=await this.ref('accounts/'+accountId).get();
    if(!doc.exists){const found=await this.db.collection('accounts').where('accountId','==',accountId).limit(2).get();
      c.must(found.docs.length===1,'Không xác định được tài khoản chủ nhà.',409);doc=found.docs[0];}
    const p=doc.data();
    // Legacy accounts can bind a Firebase UID explicitly, administered server-side.
    const uid=p.authUid||p.googleUid||doc.id;
    c.key(uid);return {uid,accountId:p.accountId||accountId,name:p.fullName||'Chủ nhà'};
  }
  post(data,actor){
    c.must(data,'Bài đăng không tồn tại.',404);
    c.must(String(data.status).toUpperCase()==='APPROVED','Bài đăng chưa được duyệt.',409);
    const ownerId=data.accountId||data.ownerId;
    c.must(ownerId && ownerId!==actor.accountId && ownerId!==actor.uid,'Bạn không thể thuê bài đăng của chính mình.',409);
    c.amount(data.price);const start=c.milliseconds(data.startDate),end=c.milliseconds(data.endDate);
    c.must(Number.isFinite(start)&&Number.isFinite(end)&&end>start&&start>this.clock(),'Ngày bắt đầu/kết thúc của bài đăng cần nằm trong tương lai.',409);
    return {ownerId,start,end,amount:data.price,title:data.title||'Chỗ ở StayExchange',address:data.address||data.addressText||''};
  }
  async quote(actor,postId){
    c.key(postId);const doc=await this.ref('posts/'+postId).get();const p=this.post(doc.exists?doc.data():null,actor);
    const host=await this.owner(p.ownerId);
    c.must(host.uid!==actor.uid,'Bạn không thể thuê bài đăng của chính mình.',409);
    const d=doc.data();
    return {...p,postId,hostUid:host.uid,hostId:host.accountId,hostName:host.name,serverNow:this.clock(),
      postImageUrl:typeof d.imageUrl==='string'&&d.imageUrl.startsWith('https://')?d.imageUrl:'',currency:'VND'};
  }
  zero(uid){return {userId:uid,available:0,held:0,currency:'VND',updatedAt:this.time()};}
  async balances(tx,uids){
    const map=new Map();for(const uid of new Set(uids)){const ref=this.ref('wallets/'+uid),snap=await tx.get(ref);const value=snap.exists?snap.data():this.zero(uid);
      c.must(Number.isSafeInteger(value.available)&&value.available>=0&&Number.isSafeInteger(value.held)&&value.held>=0,'Số dư cần được quản trị đối soát.',409);
      map.set(uid,{ref,value:{...value}});}return map;
  }
  move(tx,balances,uid,pocket,delta,title,entryId,detail={}){
    const row=balances.get(uid);c.must(row,'Thiếu ví trong giao dịch.',500);
    row.value[pocket]+=delta;
    c.must(Number.isSafeInteger(row.value[pocket])&&row.value[pocket]>=0,'Số dư không đủ hoặc khoản giữ không hợp lệ.',409);
    tx.create(this.ref('walletLedger/'+entryId),{userId:uid,pocket,amount:delta,title,...detail,createdAt:this.time()});
  }
  saveBalances(tx,balances){for(const {ref,value} of balances.values())tx.set(ref,{...value,updatedAt:this.time()});}
  notify(tx,uid,id,title,body,booking){
    tx.create(this.ref('notifications/'+id),{userId:uid,recipientId:uid,title,body,message:body,
      type:'wallet',exchangeId:booking?.exchangeId||'',postId:booking?.postId||'',isRead:false,walletManaged:true,createdAt:this.time()});
  }
  async ensureWallet(actor){await this.db.runTransaction(async tx=>{const ref=this.ref('wallets/'+actor.uid);const old=await tx.get(ref);if(!old.exists)tx.create(ref,this.zero(actor.uid));});}
  async createTopup(actor,input){
    const amount=c.amount(input.amount,1000),op=c.operation(input.operationId);
    const opRef=this.ref('walletOperations/'+c.hash(actor.uid+'/topup/'+op));
    const lockRef=this.ref('walletTopupLocks/'+actor.uid);
    return this.db.runTransaction(async tx=>{
      const oldOp=await tx.get(opRef);if(oldOp.exists){const t=await tx.get(this.ref('walletTopups/'+oldOp.get('topupId')));
        c.must(t.exists,'Không tìm thấy yêu cầu nạp cũ.',409);
        c.must(t.get('amount')===amount,'Mã thao tác đã dùng với số tiền khác. Mở yêu cầu đang chờ để kiểm tra trước khi tạo mới.',409);
        c.must(t.get('status')==='PAID'||this.topupMatchesBank(t.data()),'Ngân hàng nhận đã thay đổi. Kiểm tra khoản nạp cũ trước khi tạo yêu cầu mới.',409);
        return {...t.data(),topupId:t.id};}
      const lock=await tx.get(lockRef);
      if(lock.exists){const previous=await tx.get(this.ref('walletTopups/'+lock.get('topupId')));
        if(previous.exists&&previous.get('status')==='PENDING'&&this.topupMatchesBank(previous.data())&&c.milliseconds(previous.get('expiresAt'))>this.clock()){
          c.must(previous.get('amount')===amount,'Bạn đang có yêu cầu nạp '+Number(previous.get('amount')).toLocaleString('vi-VN')+'đ còn chờ. Mở yêu cầu đó; nếu chưa chuyển tiền, hãy hủy rồi nhập số tiền mới.',409);
          tx.create(opRef,{userId:actor.uid,topupId:previous.id,createdAt:this.time()});return {...previous.data(),topupId:previous.id};}}
      const topupId='NAP'+crypto.randomBytes(8).toString('hex').toUpperCase();
      const t={topupId,userId:actor.uid,accountId:actor.accountId,amount,currency:'VND',status:'PENDING',content:topupId,
        bank:this.bank.name,bankCode:this.bank.code,bankConfigId:this.bankInfo().configurationId,
        accountNumber:this.config.receiveAccount,accountName:this.config.accountName,
        createdAt:this.time(),expiresAt:this.time(this.clock()+this.config.topupMinutes*60000)};
      const qr=new URL('https://vietqr.app/img');qr.search=new URLSearchParams({acc:t.accountNumber,bank:this.bank.code,amount:String(amount),des:topupId,holder:t.accountName,template:'compact',showinfo:'false'}).toString();t.qrUrl=qr.toString();
      tx.create(this.ref('walletTopups/'+topupId),t);tx.set(lockRef,{topupId,userId:actor.uid});
      tx.create(opRef,{userId:actor.uid,topupId,createdAt:this.time()});return t;
    });
  }
  async cancelTopup(actor,input){
    const topupId=c.key(input.topupId);
    c.must(input.confirmUnpaid===true,'Chỉ hủy yêu cầu nếu bạn xác nhận chưa chuyển tiền.');
    return this.db.runTransaction(async tx=>{
      const ref=this.ref('walletTopups/'+topupId),doc=await tx.get(ref);
      c.must(doc.exists,'Không tìm thấy yêu cầu nạp.',404);const t=doc.data();
      c.must(t.userId===actor.uid,'Bạn không có quyền hủy yêu cầu nạp này.',403);
      if(t.status==='CANCELLED')return {...t,topupId};
      c.must(t.status==='PENDING','Khoản nạp đã thanh toán hoặc cần đối soát; không thể hủy như yêu cầu chưa chuyển tiền.',409);
      const update={status:'CANCELLED',cancelledAt:this.time(),cancelledBy:actor.uid,cancellationReason:'USER_CONFIRMED_UNPAID'};
      tx.update(ref,update);
      return {...t,...update,topupId};
    });
  }
  async bankEvent(raw){
    const event=c.parseEvent(raw),cfg=this.config;
    if(event.direction!=='in')return {ignored:true,reason:'NOT_INCOMING'};
    if(c.bankSpec(event.bank)?.code!==this.bank.code)return {ignored:true,reason:'BANK_MISMATCH'};
    if(event.account!==cfg.bankAccount)return {ignored:true,reason:'ACCOUNT_MISMATCH'};
    if(cfg.bankSubAccount&&event.subAccount!==cfg.bankSubAccount)return {ignored:true,reason:'VA_MISMATCH'};
    return this.db.runTransaction(async tx=>{
      const eventRef=this.ref('walletBankEvents/'+event.id);const old=await tx.get(eventRef);if(old.exists)return {duplicate:true};
      const topupRef=event.topupId?this.ref('walletTopups/'+event.topupId):null;
      const topup=topupRef?await tx.get(topupRef):null;
      const data=topup?.exists?topup.data():null;
      const result=data?(this.topupMatchesBank(data)?c.topupResult(data,event,this.clock()):'BANK_MISMATCH'):'UNMATCHED';
      const balances=result==='PAID'?await this.balances(tx,[data.userId]):null;
      const minutePrecisionApplied=result==='PAID'&&c.topupTimeCheck(data,event,this.clock()).minutePrecisionApplied===true;
      tx.create(eventRef,{...event,time:Number.isFinite(event.time)?event.time:null,result,createdAt:this.time(),
        timePolicy:'MB_SAME_MINUTE_V1',minutePrecisionApplied});
      if(result==='PAID'){
        this.move(tx,balances,data.userId,'available',data.amount,'Nạp tiền qua '+this.bank.name,'topup_'+event.id,{topupId:event.topupId,bankEventId:event.id});
        this.saveBalances(tx,balances);tx.update(topupRef,{status:'PAID',paidAt:this.time(event.time),creditedAt:this.time(),bankEventId:event.id,minutePrecisionApplied});
        this.notify(tx,data.userId,'topup_'+event.id,'Nạp tiền thành công',data.amount.toLocaleString('vi-VN')+'đ đã được tự động cộng vào ví.');
      }else if(result==='REVIEW'){
        const review=c.topupReview(data,event,this.clock());
        tx.update(topupRef,{status:'REVIEW',reviewEventId:event.id,firstReviewEventId:data.firstReviewEventId||data.reviewEventId||event.id,
          reviewCode:review.code,reviewReason:review.reason,reviewAmount:event.amount,
          reviewBankTime:Number.isFinite(event.time)?this.time(event.time):null,reviewedAt:this.time()});
      }
      return {result};
    });
  }
  async pay(actor,input){
    const postId=c.key(input.postId),op=c.operation(input.operationId);
    const saved=await this.ref('walletOperations/'+c.hash(actor.uid+'/pay/'+op)).get();
    if(saved.exists){const b=await this.ref('exchanges/'+saved.get('exchangeId')).get();return {...b.data(),exchangeId:b.id};}
    const expected=c.amount(input.expectedAmount);
    const q=await this.quote(actor,postId);c.must(expected===q.amount,'Giá đã thay đổi. Tải lại thông tin trước khi xác nhận.',409);
    if(input.startDate!==undefined)c.must(input.startDate===q.start&&input.endDate===q.end,'Khoảng ngày đã thay đổi; hãy tải lại.',409);
    const existingId=input.exchangeId?c.key(input.exchangeId):'';
    const opRef=this.ref('walletOperations/'+c.hash(actor.uid+'/pay/'+op));
    const lockRef=this.ref('walletBookingLocks/'+c.hash(actor.uid+'/'+postId+'/'+q.start+'/'+q.end));
    return this.db.runTransaction(async tx=>{
      const oldOp=await tx.get(opRef);
      if(oldOp.exists){const old=await tx.get(this.ref('exchanges/'+oldOp.get('exchangeId')));return {...old.data(),exchangeId:old.id};}
      const post=await tx.get(this.ref('posts/'+postId));const current=this.post(post.exists?post.data():null,actor);
      c.must(current.amount===expected&&current.start===q.start&&current.end===q.end&&current.ownerId===q.ownerId,'Bài đăng đã thay đổi. Vui lòng tải lại.',409);
      const lock=await tx.get(lockRef);
      if(lock.exists){const old=await tx.get(this.ref('exchanges/'+lock.get('exchangeId')));
        if(old.exists&& !['REJECTED','CANCELLED'].includes(old.get('status'))){
          tx.create(opRef,{userId:actor.uid,exchangeId:old.id,createdAt:this.time()});return {...old.data(),exchangeId:old.id};}}
      const exchangeId=existingId||'WX'+crypto.randomBytes(12).toString('hex').toUpperCase();const ref=this.ref('exchanges/'+exchangeId);
      const existing=existingId?await tx.get(ref):null;
      if(existingId){c.must(existing.exists,'Yêu cầu cũ không tồn tại.',404);const old=existing.data();
        c.must((old.requester_accountId||old.requesterId)===actor.accountId && old.postId===postId,'Yêu cầu không thuộc về bạn.',403);
        c.must(!old.walletManaged&&old.status==='PENDING'&&['NON_RECIPROCAL','MONEY'].includes(old.exchangeType),'Không thể chuyển thanh toán yêu cầu này.',409);}
      const balances=await this.balances(tx,[actor.uid,this.config.adminUid]);
      const paidAt=this.clock();
      const b={exchangeId,postId,postTitle:q.title,postAddress:q.address,postImageUrl:q.postImageUrl,hostName:q.hostName,
        requester_accountId:actor.accountId,host_accountId:q.hostId,requesterId:actor.uid,hostId:q.hostUid,
        requesterUid:actor.uid,hostUid:q.hostUid,adminUid:this.config.adminUid,requesterName:actor.name,
        exchangeType:'NON_RECIPROCAL',walletManaged:true,paymentSource:'WALLET',paymentStatus:'PAID',paymentId:exchangeId,
        amount:expected,currency:'VND',status:'PENDING',settlementState:'HELD',
        startDate:this.time(q.start),endDate:this.time(q.end),paidAt:this.time(paidAt),cancelUntil:this.time(paidAt+86400000),
        releaseAt:this.time(Math.max(q.end,paidAt+86400000)),createdAt:existing?.get('createdAt')||this.time(paidAt),updatedAt:this.time(paidAt)};
      this.move(tx,balances,actor.uid,'available',-expected,'Thanh toán thuê phòng',exchangeId+'_pay_A',{exchangeId,postId});
      this.move(tx,balances,this.config.adminUid,'held',expected,'Giữ tiền cho yêu cầu',exchangeId+'_pay_admin',{exchangeId,postId});
      this.saveBalances(tx,balances);
      if(existingId)tx.update(ref,b);else tx.create(ref,b);
      tx.set(lockRef,{exchangeId,userId:actor.uid});tx.create(opRef,{userId:actor.uid,exchangeId,createdAt:this.time()});
      this.notify(tx,b.hostUid,exchangeId+'_paid_B','Khách đã thanh toán','Khách đã thanh toán '+expected.toLocaleString('vi-VN')+'đ và gửi yêu cầu thuê phòng.',b);
      this.notify(tx,b.adminUid,exchangeId+'_paid_admin','Đã giữ tiền cho yêu cầu','Đã giữ '+expected.toLocaleString('vi-VN')+'đ cho '+q.title+'.',b);
      return b;
    });
  }
  async action(actor,input){
    const exchangeId=c.key(input.exchangeId),action=input.action;
    c.must(['ACCEPT','REJECT','CANCEL'].includes(action),'Thao tác không hợp lệ.');
    return this.db.runTransaction(async tx=>{
      const ref=this.ref('exchanges/'+exchangeId),doc=await tx.get(ref);
      c.must(doc.exists,'Không tìm thấy yêu cầu.',404);const b=doc.data();
      c.must(b.walletManaged===true,'Yêu cầu này không thuộc luồng ví.',409);
      c.must((action==='CANCEL'?b.requesterUid:b.hostUid)===actor.uid,'Bạn không có quyền thực hiện thao tác này.',403);
      const target={ACCEPT:'ACCEPTED',REJECT:'REJECTED',CANCEL:'CANCELLED'}[action];
      if(b.status===target)return {...b,exchangeId};
      c.must(b.settlementState==='HELD','Giao dịch đã được hoàn tiền hoặc tất toán.',409);
      if(action==='ACCEPT'){
        c.must(b.status==='PENDING','Yêu cầu không còn chờ phản hồi.',409);
        c.must(this.clock()<c.milliseconds(b.endDate),'Chuyến đi đã qua thời điểm kết thúc. Hãy từ chối để hoàn tiền.',409);
        const update={status:'ACCEPTED',acceptedAt:this.time(),updatedAt:this.time()};
        tx.update(ref,update);
        this.notify(tx,b.requesterUid,exchangeId+'_accepted_A','Chủ nhà đã chấp nhận','Chủ nhà đã chấp nhận yêu cầu. Tiền được giữ đến khi kết thúc chuyến.',b);
        this.notify(tx,b.adminUid,exchangeId+'_accepted_admin','Yêu cầu đã được chấp nhận','Chủ nhà đã chấp nhận giao dịch '+exchangeId+'.',b);
        return {...b,...update,exchangeId};
      }
      const terms=c.cancelTerms(b,this.clock());
      if(action==='CANCEL')c.must(terms.allowed,'Đã hết thời hạn hủy 24 giờ tính từ lúc thanh toán.',409);
      if(action==='REJECT')c.must(b.status==='PENDING','Chỉ có thể từ chối yêu cầu đang chờ.',409);
      const refund=action==='REJECT'?b.amount:terms.refund,fee=b.amount-refund;
      const balances=await this.balances(tx,[b.adminUid,b.requesterUid]);
      this.move(tx,balances,b.adminUid,'held',-b.amount,'Giải phóng tiền giữ',exchangeId+'_refund_held',{exchangeId});
      this.move(tx,balances,b.requesterUid,'available',refund,action==='REJECT'?'Hoàn tiền do chủ nhà từ chối':'Hoàn tiền sau khi hủy',exchangeId+'_refund_A',{exchangeId});
      if(fee)this.move(tx,balances,b.adminUid,'available',fee,'Phí hủy 20%',exchangeId+'_fee_admin',{exchangeId});
      this.saveBalances(tx,balances);
      const update={status:target,settlementState:'REFUNDED',paymentStatus:fee?'REFUNDED_PARTIAL':'REFUNDED',
        refundedAmount:refund,cancellationFee:fee,refundedAt:this.time(),updatedAt:this.time()};tx.update(ref,update);
      this.notify(tx,b.requesterUid,exchangeId+'_refund_notice_A','Đã hoàn tiền vào ví',refund.toLocaleString('vi-VN')+'đ đã được hoàn vào ví của bạn.'+(fee?' Phí hủy: '+fee.toLocaleString('vi-VN')+'đ.':''),b);
      this.notify(tx,b.adminUid,exchangeId+'_refund_notice_admin','Đã tự động hoàn tiền','Hoàn '+refund.toLocaleString('vi-VN')+'đ cho A; phí hủy '+fee.toLocaleString('vi-VN')+'đ.',b);
      if(action==='CANCEL')this.notify(tx,b.hostUid,exchangeId+'_cancel_notice_B','Khách đã hủy yêu cầu','Yêu cầu đã hủy trong thời hạn 24 giờ.',b);
      return {...b,...update,exchangeId};
    });
  }
  async settle(exchangeId){
    return this.db.runTransaction(async tx=>{
      const ref=this.ref('exchanges/'+exchangeId),doc=await tx.get(ref);if(!doc.exists)return false;const b=doc.data();
      if(!b.walletManaged||b.settlementState!=='HELD'||!['ACCEPTED','ONGOING'].includes(b.status)||!Number.isFinite(c.milliseconds(b.releaseAt))||c.milliseconds(b.releaseAt)>this.clock())return false;
      const balances=await this.balances(tx,[b.adminUid,b.hostUid]);
      this.move(tx,balances,b.adminUid,'held',-b.amount,'Trả tiền sau chuyến đi',exchangeId+'_settle_admin',{exchangeId});
      this.move(tx,balances,b.hostUid,'available',b.amount,'Nhận tiền thuê phòng',exchangeId+'_settle_B',{exchangeId});
      this.saveBalances(tx,balances);tx.update(ref,{status:'COMPLETED',settlementState:'RELEASED',paymentStatus:'RELEASED',releasedAt:this.time(),updatedAt:this.time()});
      for(const [uid,suffix,title,body] of [[b.hostUid,'B','Đã nhận tiền vào ví','Chuyến đi đã hoàn tất. '+b.amount.toLocaleString('vi-VN')+'đ đã được trả vào ví.'],[b.requesterUid,'A','Chuyến đi đã hoàn tất','Hệ thống đã chuyển tiền đang giữ sang ví chủ nhà.'],[b.adminUid,'admin','Đã tự động trả tiền','Giao dịch '+exchangeId+' đã được tất toán.']])this.notify(tx,uid,exchangeId+'_settled_'+suffix,title,body,b);
      return true;
    });
  }
  async settleDue(){
    let cursor=null,count=0;
    do{let q=this.db.collection('exchanges').where('settlementState','==','HELD').orderBy('__name__').limit(100);
      if(cursor)q=q.startAfter(cursor);
      const result=await q.get();if(!result.docs.length)break;
      for(const doc of result.docs){try{if(await this.settle(doc.id))count++;}catch(error){console.error("Settlement needs review:",doc.id,error.status||error.code||"internal");}}
      cursor=result.docs.length===100?result.docs[result.docs.length-1]:null;
    }while(cursor);return count;
  }
  async snapshot(actor){
    await this.ensureWallet(actor);
    const [wallet,topups,ledger,sent,received]=await Promise.all([
      this.ref('wallets/'+actor.uid).get(),this.db.collection('walletTopups').where('userId','==',actor.uid).get(),
      this.db.collection('walletLedger').where('userId','==',actor.uid).get(),
      this.db.collection('exchanges').where('requesterUid','==',actor.uid).get(),
      this.db.collection('exchanges').where('hostUid','==',actor.uid).get()]);
    const map=new Map();for(const d of [...sent.docs,...received.docs])if(d.get('walletManaged'))map.set(d.id,{...d.data(),exchangeId:d.id});
    let bookings=[...map.values()],events=[];
    if(actor.isAdmin){const [all,bank]=await Promise.all([this.db.collection('exchanges').where('walletManaged','==',true).get(),this.db.collection('walletBankEvents').orderBy('createdAt','desc').limit(20).get()]);
      bookings=all.docs.map(d=>({...d.data(),exchangeId:d.id}));events=bank.docs.map(d=>({...d.data(),eventId:d.id}));}
    const sort=(a,b)=>c.milliseconds(b.createdAt)-c.milliseconds(a.createdAt);
    const notifications=await this.db.collection('notifications').where('userId','==',actor.uid).get();
    return c.json({apiVersion:3,userId:actor.uid,accountId:actor.accountId,name:actor.name,isAdmin:actor.isAdmin,bank:this.bankInfo(),serverNow:this.clock(),wallet:wallet.data(),
      topups:topups.docs.map(d=>({...d.data(),topupId:d.id})).sort(sort).slice(0,30),
      ledger:ledger.docs.map(d=>({...d.data(),ledgerId:d.id})).sort(sort).slice(0,60),bookings:bookings.sort(sort).slice(0,100),bankEvents:events,
      notifications:notifications.docs.filter(d=>d.get('walletManaged')).map(d=>({...d.data(),notificationId:d.id})).sort(sort).slice(0,30)});
  }
}
module.exports={WalletService};
