'use strict';
// Local backend maintenance only. Never exposed as an HTTP endpoint.
// No writes by default; --apply uses stored authenticated bank events, not
// amounts, account numbers or event payloads supplied on the command line.
const c=require('./core');
const POLICY='MB_SAME_MINUTE_V1';

function validateIds(ids){
  c.must(Array.isArray(ids)&&ids.length>0&&ids.length<=10,'Cần từ 1 đến 10 mã nạp.');
  c.must(new Set(ids).size===ids.length,'Không lặp mã nạp.');
  for(const id of ids)c.must(typeof id==='string'&&/^NAP[A-F0-9]{16}$/.test(id),'Mã nạp cần là NAP + 16 ký tự.');
}

async function reconcile(service,ids,{apply=false}={}){
  validateIds(ids);c.must(typeof apply==='boolean','apply phải là boolean.');
  c.must(service.bank.code==='MBBank','Công cụ này chỉ xử lý lỗi thời gian MBBank.');
  return service.db.runTransaction(async tx=>{
    const rows=[];
    for(const id of ids){
      const topupRef=service.ref('walletTopups/'+id),topup=await tx.get(topupRef);
      c.must(topup.exists,'Không tìm thấy yêu cầu '+id,404);const t=topup.data();
      c.key(t.userId);c.amount(t.amount,1000);
      const ledger=await tx.get(service.db.collection('walletLedger').where('topupId','==',id));
      if(t.status==='PAID'){
        // A repeated run can only skip a complete, internally consistent credit.
        c.must(typeof t.bankEventId==='string','Khoản PAID thiếu sự kiện; cần kiểm tra riêng: '+id,409);
        const bank=await tx.get(service.ref('walletBankEvents/'+c.key(t.bankEventId)));
        const entry=ledger.docs.find(d=>d.id==='topup_'+t.bankEventId);
        c.must(bank.exists&&bank.get('result')==='PAID'&&bank.get('topupId')===id&&bank.get('amount')===t.amount&&
          entry&&entry.get('userId')===t.userId&&entry.get('amount')===t.amount&&entry.get('bankEventId')===t.bankEventId,
          'Khoản PAID chưa khớp sổ giao dịch; cần kiểm tra riêng: '+id,409);
        rows.push({topupId:id,userId:t.userId,amount:t.amount,eventId:t.bankEventId,status:'ALREADY_PAID'});continue;
      }
      c.must(t.status==='REVIEW'&&!t.cancelledAt,'Chỉ xử lý REVIEW chưa từng bị hủy: '+id,409);
      c.must(!t.paidAt&&!t.bankEventId,'Yêu cầu đã có dấu vết ghi nhận tiền: '+id,409);
      c.must(service.topupMatchesBank(t),'Cấu hình ngân hàng đã thay đổi: '+id,409);
      c.must(!t.reviewCode||['BEFORE_REQUEST','ALREADY_REVIEW'].includes(t.reviewCode),
        'Lý do đối soát khác lỗi thời gian đến phút: '+id,409);
      c.must(ledger.empty,'Đã có bút toán cho yêu cầu; không cộng lại: '+id,409);
      const events=await tx.get(service.db.collection('walletBankEvents').where('topupId','==',id));
      c.must(events.docs.length===1,'Cần đúng một sự kiện ngân hàng cho mỗi yêu cầu: '+id,409);
      const doc=events.docs[0],e=doc.data();
      c.must(/^[1-9][0-9]*$/.test(doc.id)&&e.id===doc.id&&t.reviewEventId===doc.id&&
        (!t.firstReviewEventId||t.firstReviewEventId===doc.id),'Sự kiện không khớp lịch sử REVIEW: '+id,409);
      c.must(e.result==='REVIEW'&&e.topupId===id&&e.amount===t.amount&&e.direction==='in',
        'Số tiền, chiều tiền hoặc trạng thái không khớp: '+id,409);
      c.must(c.bankSpec(e.bank)?.code==='MBBank'&&e.account===service.config.bankAccount&&
        (!service.config.bankSubAccount||e.subAccount===service.config.bankSubAccount),
        'Sự kiện không thuộc tài khoản MBBank hiện tại: '+id,409);
      c.must(typeof e.reference==='string'&&e.reference.trim().length>0,'Thiếu mã tham chiếu ngân hàng: '+id,409);
      const created=c.milliseconds(t.createdAt),received=c.milliseconds(e.createdAt),bankTime=c.milliseconds(e.time);
      const check=c.topupTimeCheck(t,{...e,time:bankTime},received);
      c.must(Number.isFinite(received)&&received>=created&&received<=service.clock()+60000&&
        check.ok&&check.minutePrecisionApplied===true,
        'Không thuộc trường hợp MBBank ghi cùng phút với lúc tạo yêu cầu: '+id,409);
      const canonicalLedger=await tx.get(service.ref('walletLedger/topup_'+doc.id));
      c.must(!canonicalLedger.exists,'Mã bút toán ngân hàng đã được dùng: '+id,409);
      const notificationRef=service.ref('notifications/topup_'+doc.id);
      const notification=await tx.get(notificationRef);
      c.must(!notification.exists,'Đã có thông báo ghi nhận tiền; cần kiểm tra riêng: '+id,409);
      rows.push({topupId:id,userId:t.userId,amount:t.amount,eventId:doc.id,status:'ELIGIBLE',
        earlierByMilliseconds:created-bankTime,bankTime,receivedAt:received,topupRef,eventRef:doc.ref,notificationRef});
    }
    const eligible=rows.filter(r=>r.status==='ELIGIBLE');
    const balances=await service.balances(tx,eligible.map(r=>r.userId));
    const wallets=[...balances].map(([uid,b])=>({userId:uid,availableBefore:b.value.available,
      availableAfter:b.value.available+eligible.filter(r=>r.userId===uid).reduce((sum,r)=>sum+r.amount,0),held:b.value.held}));
    c.must(wallets.every(w=>Number.isSafeInteger(w.availableAfter)),'Số dư sau đối soát vượt giới hạn số nguyên; cần kiểm tra riêng.',409);
    if(apply){
      // All queries and document reads above complete before the first write.
      // The deterministic ledger ID is the same one used by the webhook.
      for(const r of eligible){
        service.move(tx,balances,r.userId,'available',r.amount,'Nạp tiền qua MBBank — đối soát thời gian','topup_'+r.eventId,
          {topupId:r.topupId,bankEventId:r.eventId,resolution:POLICY});
        tx.update(r.topupRef,{status:'PAID',bankEventId:r.eventId,paidAt:service.time(r.bankTime),creditedAt:service.time(),
          reviewResolvedAt:service.time(),resolution:POLICY,minutePrecisionApplied:true});
        tx.update(r.eventRef,{result:'PAID',originalResult:'REVIEW',resolvedAt:service.time(),resolution:POLICY,minutePrecisionApplied:true});
        service.notify(tx,r.userId,'topup_'+r.eventId,'Đã đối soát khoản nạp',
          r.amount.toLocaleString('vi-VN')+'đ đã được cộng vào ví sau khi đối soát thời gian MBBank.');
      }
      service.saveBalances(tx,balances);
    }
    return {mode:apply?'APPLY':'DRY_RUN',policy:POLICY,creditTotal:eligible.reduce((sum,r)=>sum+r.amount,0),
      applied:apply,topups:rows.map(({topupRef,eventRef,notificationRef,...r})=>({...r,status:apply&&r.status==='ELIGIBLE'?'CREDITED':r.status})),wallets};
  });
}

function options(args){
  const out={ids:[],apply:false};
  for(let i=0;i<args.length;i++){
    if(args[i]==='--topup')out.ids.push(String(args[++i]||'').toUpperCase());
    else if(args[i]==='--apply'){c.must(!out.apply,'Không lặp --apply.');out.apply=true;}
    else if(args[i]==='--output'){out.output=args[++i];c.must(typeof out.output==='string'&&out.output.length>0,'Thiếu đường dẫn --output.');}
    else throw new c.ApiError(400,'Dùng --topup NAP...; mặc định chỉ xem trước, thêm --apply để xử lý.');
  }
  validateIds(out.ids);return out;
}

async function main(){
  const input=options(process.argv.slice(2)),env=process.env;
  for(const k of ['FIREBASE_PROJECT_ID','ADMIN_UID','BANK_CODE','BANK_ACCOUNT_NUMBER','RECEIVE_ACCOUNT_NUMBER'])
    c.must(env[k],'Thiếu cấu hình '+k+'.');
  const {initializeApp,applicationDefault,deleteApp}=require('firebase-admin/app');
  const {getFirestore,Timestamp}=require('firebase-admin/firestore');const {WalletService}=require('./wallet-service');
  const app=initializeApp({credential:applicationDefault(),projectId:env.FIREBASE_PROJECT_ID});
  try{
    const service=new WalletService(getFirestore(app),Timestamp,{adminUid:env.ADMIN_UID,bankCode:env.BANK_CODE,
      bankAccount:env.BANK_ACCOUNT_NUMBER,receiveAccount:env.RECEIVE_ACCOUNT_NUMBER,bankSubAccount:env.BANK_SUB_ACCOUNT||''});
    await service.actor(env.ADMIN_UID);
    const result=await reconcile(service,input.ids,{apply:input.apply});
    const text=JSON.stringify(result,null,2)+'\n';
    if(input.output){require('node:fs').writeFileSync(input.output,text,'utf8');console.log('Reconciliation report saved.');}
    else process.stdout.write(text);
  }finally{await deleteApp(app);}
}
if(require.main===module)main().catch(error=>{console.error('Reconciliation failed:',error.status?error.message:(error.code||error.name));process.exitCode=1;});
module.exports={reconcile,options,POLICY};
