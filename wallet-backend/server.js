'use strict';
const http=require('node:http');
const c=require('./core');
const backendVersion=require('./package.json').version;
function createServer({service,authenticate,webhookKey,logger=console}){
  const limits=new Map();
  function limit(key){const now=Date.now(),old=limits.get(key);if(!old||old.until<now){limits.set(key,{until:now+60000,count:1});return;}c.must(++old.count<=100,'Bạn thao tác quá nhanh. Thử lại sau ít phút.',429);}
  const server=http.createServer(async(req,res)=>{
    res.setHeader('Content-Type','application/json; charset=utf-8');res.setHeader('Cache-Control','no-store');res.setHeader('X-Content-Type-Options','nosniff');
    const send=(status,data)=>{res.writeHead(status);res.end(JSON.stringify(data));};
    try{
      const path=new URL(req.url,'http://localhost').pathname;
      if(req.method==='GET'&&path==='/health')return send(200,{ok:true,apiVersion:3,postReservationVersion:1,app:'StayExchange Wallet',bank:service.bankInfo().code,backendVersion});
      c.must(req.method==='POST','Chỉ hỗ trợ POST.',405);
      const webhook=path==='/webhooks/sepay';
      if(webhook)c.must(c.authorization(req.headers.authorization,webhookKey),'Webhook chưa được xác thực.',401);
      else c.must(!req.headers.origin,'Không hỗ trợ truy cập từ website.',403);
      let user;
      if(!webhook){const header=req.headers.authorization||'';c.must(header.startsWith('Bearer '),'Vui lòng đăng nhập.',401);user=await authenticate(header.slice(7));c.must(user?.uid,'Phiên đăng nhập không hợp lệ.',401);limit(user.uid);}
      let size=0,text='';for await(const chunk of req){size+=chunk.length;c.must(size<=65536,'Nội dung yêu cầu quá lớn.',413);text+=chunk.toString('utf8');}
      let body;try{body=JSON.parse(text||'{}');}catch(_){throw new c.ApiError(400,'Dữ liệu JSON không hợp lệ.');}
      c.must(body && typeof body==='object'&&!Array.isArray(body),'Dữ liệu phải là một đối tượng.');
      if(webhook){const result=await service.bankEvent(body);logger.info?.('SePay webhook',JSON.stringify({eventId:body.id,result:result?.result||(result?.duplicate?'DUPLICATE':result?.ignored?'IGNORED':'ACCEPTED'),reason:result?.reason||''}));return send(200,{success:true});}
      const actor=await service.actor(user.uid);let data;
      if(path==='/v1/snapshot')data=await service.snapshot(actor);
      else if(path==='/v1/quote')data=await service.quote(actor,body.postId);
      else if(path==='/v1/topups')data=await service.createTopup(actor,body);
      else if(path==='/v1/topups/cancel')data=await service.cancelTopup(actor,body);
      else if(path==='/v1/bookings/pay')data=await service.pay(actor,body);
      else if(path==='/v1/bookings/action')data=await service.action(actor,body);
      else if(path==='/v1/exchanges/update')data=await require('./exchange-service').updateExchange(service,actor,body);
      else throw new c.ApiError(404,'Không tìm thấy chức năng.');
      return send(200,{ok:true,data:c.json(data)});
    }catch(error){
      const status=error.status||500;
      if(status===500)logger.error('Wallet request failed:',error.code||error.name||'internal');
      if(!res.headersSent)return send(status,{ok:false,message:status===500?'Server chưa xử lý được. Kiểm tra cấu hình Firebase hoặc nhật ký backend.':error.message});
      res.end();
    }
  });
  server.requestTimeout=25000;server.headersTimeout=10000;
  const gc=setInterval(()=>{const now=Date.now();for(const[k,v]of limits)if(v.until<now)limits.delete(k);},60000);gc.unref();server.on('close',()=>clearInterval(gc));return server;
}
async function main(){
  const {initializeApp,applicationDefault}=require('firebase-admin/app');
  const {getAuth}=require('firebase-admin/auth');
  const {getFirestore,Timestamp}=require('firebase-admin/firestore');
  const {WalletService}=require('./wallet-service');
  const env=process.env;
  for(const k of ['FIREBASE_PROJECT_ID','ADMIN_UID','BANK_CODE','BANK_ACCOUNT_NUMBER','RECEIVE_ACCOUNT_NUMBER','BANK_ACCOUNT_NAME','SEPAY_WEBHOOK_KEY'])c.must(env[k]&&!/YOUR_|REPLACE_|FIREBASE_AUTH_UID|THAY_BANG_/.test(env[k]),'Chưa cấu hình '+k+'.');
  const bank=c.bankSpec(env.BANK_CODE);c.must(bank,'BANK_CODE cần là MBBank hoặc VPBank.');
  c.must(/^\d{5,30}$/.test(env.BANK_ACCOUNT_NUMBER),'Số tài khoản ngân hàng chính không hợp lệ.');
  c.must(/^[A-Za-z0-9]{5,19}$/.test(env.RECEIVE_ACCOUNT_NUMBER),'Số nhận tiền/VA phải gồm 5 đến 19 ký tự chữ/số để tạo VietQR.');
  c.must(/^[\x20-\x7e]{1,80}$/.test(env.BANK_ACCOUNT_NAME)&&env.BANK_ACCOUNT_NAME.trim().length>0,'Tên chủ tài khoản cần không dấu, tối đa 80 ký tự.');
  c.must(env.SEPAY_WEBHOOK_KEY.length>=32,'Khóa webhook cần tối thiểu 32 ký tự.');
  const topupMinutes=Number(env.TOPUP_TTL_MINUTES||15);c.must(Number.isInteger(topupMinutes)&&topupMinutes>=1&&topupMinutes<=60,'TOPUP_TTL_MINUTES phải từ 1 đến 60.');
  initializeApp({credential:applicationDefault(),projectId:env.FIREBASE_PROJECT_ID});
  const service=new WalletService(getFirestore(),Timestamp,{adminUid:env.ADMIN_UID,bankCode:bank.code,bankAccount:env.BANK_ACCOUNT_NUMBER,receiveAccount:env.RECEIVE_ACCOUNT_NUMBER,
    bankSubAccount:env.BANK_SUB_ACCOUNT||'',accountName:env.BANK_ACCOUNT_NAME,topupMinutes});
  const admin=await service.actor(env.ADMIN_UID);c.must(admin.isAdmin,'Không tìm thấy tài khoản admin.');
  await service.ensureWallet(admin);
  console.log('Post reservations:',JSON.stringify(await service.reservations.synchronize()));
  const server=createServer({service,webhookKey:env.SEPAY_WEBHOOK_KEY,authenticate:async token=>{
    try{return await getAuth().verifyIdToken(token,true);}catch(_){throw new c.ApiError(401,'Phiên đăng nhập đã hết hạn hoặc không thuộc Firebase project này.');}
  }});
  const port=Number(env.PORT||8080);c.must(Number.isInteger(port)&&port>0&&port<=65535,'PORT không hợp lệ.');
  server.listen(port,env.HOST||'127.0.0.1',()=>console.log('StayExchange '+bank.code+' backend ready on local port '+port));
  let running=false;async function settle(){if(running)return;running=true;try{const n=await service.settleDue();if(n)console.log('Settled bookings:',n);}catch(e){console.error('Settlement job failed:',e.code||e.name);}finally{running=false;}}
  const seconds=Number(env.SETTLEMENT_INTERVAL_SECONDS||30);c.must(Number.isInteger(seconds)&&seconds>=10&&seconds<=3600,'Chu kỳ tất toán phải từ 10 đến 3.600 giây.');
  const job=setInterval(settle,seconds*1000);await settle();
  for(const signal of ['SIGINT','SIGTERM'])process.on(signal,()=>{clearInterval(job);server.close(()=>process.exit(0));});
}
if(require.main===module)main().catch(e=>{console.error('Không khởi động được backend:',e.status?e.message:(e.code||e.name));process.exitCode=1;});
module.exports={createServer};
