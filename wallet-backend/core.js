'use strict';
const crypto = require('node:crypto');
class ApiError extends Error { constructor(status, message) { super(message); this.status = status; } }
const must = (condition, message, status=400) => { if (!condition) throw new ApiError(status,message); };
const hash = text => crypto.createHash('sha256').update(text).digest('hex');
function bankSpec(value) {
  const code=String(value||'').trim().toUpperCase();
  if(['MB','MBBANK','970422'].includes(code))return {code:'MBBank',name:'MBBank',appPackage:'com.mbmobile'};
  if(['VPB','VPBANK','970432'].includes(code))return {code:'VPBank',name:'VPBank',appPackage:'com.vnpay.vpbankonline'};
  return null;
}
function key(value) { must(typeof value === 'string' && /^[A-Za-z0-9_-]{1,128}$/.test(value),'Mã dữ liệu không hợp lệ.'); return value; }
function operation(value) { must(typeof value === 'string' && /^[A-Za-z0-9_-]{12,100}$/.test(value),'Thiếu mã chống gửi lặp.'); return value; }
function amount(value,min=1) { must(Number.isSafeInteger(value) && value>=min && value<=50000000,'Số tiền phải là VND nguyên, tối đa 50.000.000đ.'); return value; }
function milliseconds(value) { return value && typeof value.toMillis==='function' ? value.toMillis() : value instanceof Date ? value.getTime() : typeof value==='number' ? value : NaN; }
function authorization(header, secret) {
  if(typeof secret!=='string'||secret.length<32)return false;
  const a=Buffer.from(header||''),b=Buffer.from('Apikey '+secret);
  return a.length===b.length&&crypto.timingSafeEqual(a,b);
}
function parseEvent(body) {
  must(body && Number.isSafeInteger(body.id) && body.id>0,'ID giao dịch ngân hàng không hợp lệ.');
  must(Number.isSafeInteger(body.transferAmount)&&body.transferAmount>0,'Số tiền giao dịch ngân hàng không hợp lệ.');
  must(typeof body.accountNumber==='string' && body.accountNumber.length<=100,'Thiếu tài khoản nhận.');
  const text=(String(body.content||'')+' '+String(body.code||'')).toUpperCase();
  const codes=[...new Set(text.match(/\bNAP[A-F0-9]{16}\b/g)||[])];
  const raw=String(body.transactionDate||'');
  const time=/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(raw)?Date.parse(raw.replace(' ','T')+'+07:00'):NaN;
  return {id:String(body.id),bank:String(body.gateway||'').toUpperCase(),account:body.accountNumber.trim(),
    subAccount:String(body.subAccount||''),direction:body.transferType,amount:body.transferAmount,
    topupId:codes.length===1?codes[0]:null,time,reference:String(body.referenceCode||'').slice(0,128)};
}
function topupTimeCheck(t,event,now) {
  const created=milliseconds(t.createdAt),expires=milliseconds(t.expiresAt);
  if(!Number.isFinite(event.time))return {ok:false,code:'INVALID_BANK_TIME'};
  if(!Number.isFinite(created)||!Number.isFinite(expires)||expires<=created)return {ok:false,code:'INVALID_REQUEST_TIME'};
  // The MB events observed in this project have :00 seconds. For an exact
  // minute timestamp only, accept the minute containing creation. Never admit
  // a previous minute, a non-MB event, or an arbitrary 60-second clock skew.
  const minuteTimestamp=bankSpec(event.bank)?.code==='MBBank'&&event.time%60000===0;
  const earliest=minuteTimestamp?Math.floor(created/60000)*60000:created-1000;
  if(event.time<earliest)return {ok:false,code:'BEFORE_REQUEST'};
  if(event.time>expires)return {ok:false,code:'EXPIRED_TOPUP'};
  if(!Number.isFinite(now)||event.time>now+60000)return {ok:false,code:'CLOCK_MISMATCH'};
  return {ok:true,code:'OK',minutePrecisionApplied:minuteTimestamp&&event.time<created-1000};
}
function topupResult(t,event,now) {
  if(t.status==='PAID')return 'EXTRA_PAYMENT';
  if(t.status!=='PENDING'||event.amount!==t.amount||!topupTimeCheck(t,event,now).ok)return 'REVIEW';
  return 'PAID';
}
function topupReview(t,event,now) {
  if(t.status==='CANCELLED'||Number.isFinite(milliseconds(t.cancelledAt)))return {code:'CANCELLED_TOPUP',reason:'Ngân hàng báo tiền cho yêu cầu đã hủy; cần đối soát, không dùng lại QR cũ.'};
  if(t.status==='REVIEW')return {code:t.reviewCode||'ALREADY_REVIEW',reason:t.reviewReason||'Yêu cầu đã có khoản cần đối soát.'};
  if(t.status!=='PENDING')return {code:'INVALID_TOPUP_STATE',reason:'Trạng thái yêu cầu nạp cần được đối soát.'};
  if(event.amount!==t.amount)return {code:'AMOUNT_MISMATCH',reason:'Số tiền ngân hàng báo '+event.amount.toLocaleString('vi-VN')+'đ khác số tiền yêu cầu '+t.amount.toLocaleString('vi-VN')+'đ.'};
  const check=topupTimeCheck(t,event,now);
  const reasons={INVALID_BANK_TIME:'Chưa đọc được thời điểm chuyển tiền từ ngân hàng.',
    INVALID_REQUEST_TIME:'Thời gian tạo hoặc hết hạn yêu cầu không hợp lệ; cần đối soát.',
    BEFORE_REQUEST:'Thời điểm chuyển tiền sớm hơn lúc tạo yêu cầu; cần kiểm tra mã nạp và giờ trên máy chủ.',
    EXPIRED_TOPUP:'Thời điểm chuyển tiền nằm sau hạn của yêu cầu nạp.',
    CLOCK_MISMATCH:'Thời gian ngân hàng và máy chủ đang lệch; cần kiểm tra giờ Windows.'};
  if(!check.ok)return {code:check.code,reason:reasons[check.code]};
  return {code:'REVIEW',reason:'Khoản nạp cần đối soát.'};
}
function cancelTerms(booking,now) {
  const until=milliseconds(booking.cancelUntil);
  return {allowed:['PENDING','ACCEPTED','ONGOING'].includes(booking.status)&&booking.settlementState==='HELD'&&now<until,
    fee:Math.round(booking.amount*20/100),refund:booking.amount-Math.round(booking.amount*20/100),cancelUntil:until};
}
function json(value) {
  if(value && typeof value.toMillis==='function')return value.toMillis();
  if(value instanceof Date)return value.getTime();
  if(Array.isArray(value))return value.map(json);
  if(value && typeof value==='object')return Object.fromEntries(Object.entries(value).map(([k,v])=>[k,json(v)]));
  return value;
}
module.exports={ApiError,must,key,operation,amount,milliseconds,hash,bankSpec,authorization,parseEvent,topupTimeCheck,topupResult,topupReview,cancelTerms,json};
