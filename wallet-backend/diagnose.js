'use strict';
// Read-only diagnostics: no set/update/create/delete or financial recovery.
const c=require('./core');
function time(value){
  const ms=c.milliseconds(value);if(!Number.isFinite(ms))return null;
  return {utc:new Date(ms).toISOString(),vietnam:new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Ho_Chi_Minh',dateStyle:'short',timeStyle:'medium'}).format(ms)};
}
const last4=value=>String(value||'').slice(-4);
async function report(db,env,{ids=[],recent=5}={},now=Date.now()){
  const spec=c.bankSpec(env.BANK_CODE),bank=spec?.code||'UNKNOWN';
  const expectedConfiguration=c.hash(JSON.stringify([bank,env.BANK_ACCOUNT_NUMBER,env.RECEIVE_ACCOUNT_NUMBER,env.BANK_SUB_ACCOUNT||'']));
  const docs=ids.length?await Promise.all(ids.map(id=>db.doc('walletTopups/'+id).get())):
    (await db.collection('walletTopups').orderBy('createdAt','desc').limit(recent).get()).docs;
  const rows=[];
  for(const doc of docs){
    if(!doc.exists){rows.push({topupId:doc.id,missing:true});continue;}
    const t=doc.data();
    const [events,ledger,wallet]=await Promise.all([
      db.collection('walletBankEvents').where('topupId','==',doc.id).get(),
      db.collection('walletLedger').where('topupId','==',doc.id).get(),
      db.doc('wallets/'+c.key(t.userId)).get()]);
    rows.push({topupId:doc.id,userId:t.userId,status:t.status,requestedAmount:t.amount,
      bank:t.bankCode||t.bank,receiverLast4:last4(t.accountNumber),
      matchesCurrentBank:t.bankConfigId?t.bankConfigId===expectedConfiguration:c.bankSpec(t.bankCode||t.bank)?.code===bank&&t.accountNumber===env.RECEIVE_ACCOUNT_NUMBER,
      createdAt:time(t.createdAt),expiresAt:time(t.expiresAt),paidAt:time(t.paidAt),cancelledAt:time(t.cancelledAt),
      reviewCode:t.reviewCode||null,reviewReason:t.reviewReason||null,reviewEventId:t.reviewEventId||null,
      wallet:wallet.exists?{available:wallet.get('available'),held:wallet.get('held')}:null,
      bankEvents:events.docs.map(e=>{const b=e.data();return {eventId:e.id,result:b.result,amount:b.amount,
        bank:b.bank,receiverLast4:last4(b.account),matchesConfiguredAccount:b.account===env.BANK_ACCOUNT_NUMBER,
        bankTime:time(b.time),receivedAt:time(b.createdAt),reference:b.reference||null,
        sameRequestedAmount:b.amount===t.amount,
        acceptedByTimePolicy:c.topupTimeCheck(t,{...b,time:c.milliseconds(b.time)},c.milliseconds(b.createdAt)).ok,
        mbMinutePrecisionApplied:c.topupTimeCheck(t,{...b,time:c.milliseconds(b.time)},c.milliseconds(b.createdAt)).minutePrecisionApplied===true,
        beforeRequest:Number.isFinite(c.milliseconds(b.time))&&c.milliseconds(b.time)<c.milliseconds(t.createdAt)-1000,
        afterExpiry:Number.isFinite(c.milliseconds(b.time))&&c.milliseconds(b.time)>c.milliseconds(t.expiresAt)};}),
      ledger:ledger.docs.map(e=>{const d=e.data();return {ledgerId:e.id,amount:d.amount,pocket:d.pocket,bankEventId:d.bankEventId||null,createdAt:time(d.createdAt)};})});
  }
  return {app:'StayExchange read-only topup diagnostics',apiVersion:3,generatedAt:time(now),projectId:env.FIREBASE_PROJECT_ID,
    configuredBank:bank,configuredReceiverLast4:last4(env.RECEIVE_ACCOUNT_NUMBER),topups:rows};
}
function options(args){
  const out={ids:[],recent:5};
  for(let i=0;i<args.length;i++){
    if(args[i]==='--topup'){const id=String(args[++i]||'').toUpperCase();c.must(/^NAP[A-F0-9]{16}$/.test(id),'Mã nạp cần là NAP + 16 ký tự.');out.ids.push(id);}
    else if(args[i]==='--recent'){const n=Number(args[++i]);c.must(Number.isInteger(n)&&n>=1&&n<=10,'--recent cần từ 1 đến 10.');out.recent=n;}
    else if(args[i]==='--output'){out.output=args[++i];c.must(typeof out.output==='string'&&out.output.length>0,'Thiếu đường dẫn --output.');}
    else throw new c.ApiError(400,'Dùng --topup NAP... hoặc --recent 5, thêm --output để lưu JSON UTF-8.');
  }
  c.must(out.ids.length<=10,'Chỉ kiểm tra tối đa 10 mã một lần.');return out;
}
async function main(){
  const config=options(process.argv.slice(2));
  const {initializeApp,applicationDefault,deleteApp}=require('firebase-admin/app');
  const {getFirestore}=require('firebase-admin/firestore');
  c.must(process.env.FIREBASE_PROJECT_ID,'Thiếu FIREBASE_PROJECT_ID.');
  const app=initializeApp({credential:applicationDefault(),projectId:process.env.FIREBASE_PROJECT_ID});
  try{const text=JSON.stringify(await report(getFirestore(app),process.env,config),null,2)+'\n';
    if(config.output){require('node:fs').writeFileSync(config.output,text,{encoding:'utf8',flag:'w'});console.log('Report saved.');}
    else process.stdout.write(text);}
  finally{await deleteApp(app);}
}
if(require.main===module)main().catch(error=>{console.error('Không đọc được báo cáo:',error.status?error.message:(error.code||error.name));process.exitCode=1;});
module.exports={report,options};
