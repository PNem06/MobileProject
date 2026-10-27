'use strict';
// Offline operator tool. Preview is read-only; apply copies first and switches one transaction gate.
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const c = require('./core');
const {CONTROL_PATH, LEGACY_COLLECTIONS, physicalPath, writeData, rentalPayment, storageState} = require('./payment-storage');
const FORMAT = 'stayexchange-payments-v2';

function encode(value) {
  if (value === null || typeof value === 'string' || typeof value === 'boolean') return value;
  if (typeof value === 'number') { c.must(Number.isFinite(value), 'Dữ liệu chứa số không hữu hạn.', 409); return value; }
  if (value && typeof value.toMillis === 'function') {
    const ms = value.toMillis();
    const seconds = value.seconds === undefined ? Math.floor(ms / 1000) : value.seconds;
    const nanoseconds = value.nanoseconds === undefined ? Math.round((ms - seconds * 1000) * 1000000) : value.nanoseconds;
    return {$type: 'timestamp', seconds, nanoseconds};
  }
  if (value instanceof Date) return {$type: 'date', value: value.toISOString()};
  if (Buffer.isBuffer(value) || value instanceof Uint8Array) return {$type: 'bytes', value: Buffer.from(value).toString('base64')};
  if (Array.isArray(value)) return value.map(encode);
  c.must(value && (Object.getPrototypeOf(value) === Object.prototype || Object.getPrototypeOf(value) === null),
    'Có kiểu dữ liệu chưa hỗ trợ sao lưu; chưa chuyển cấu trúc.', 409);
  const output = {};
  for (const key of Object.keys(value).sort()) output[key] = encode(value[key]);
  return output;
}
function hash(value) { return crypto.createHash('sha256').update(JSON.stringify(encode(value))).digest('hex'); }
function withoutMigration(data) { const {_migration, ...values} = data; return values; }
async function scan(db, name) {
  const result = []; let cursor;
  do {
    let query = db.collection(name).orderBy('__name__').limit(200);
    if (cursor) query = query.startAfter(cursor);
    const page = await query.get(); result.push(...page.docs);
    cursor = page.docs.length === 200 ? page.docs[page.docs.length - 1] : null;
  } while (cursor);
  return result;
}
async function sources(db, reader = query => query.get()) {
  const legacy = [];
  for (const name of LEGACY_COLLECTIONS) {
    const rows = await reader(db.collection(name));
    for (const doc of rows.docs) legacy.push({path: name + '/' + doc.id, data: doc.data()});
  }
  const wallets = (await reader(db.collection('wallets'))).docs.map(doc => ({path: 'wallets/' + doc.id, data: doc.data()}));
  const rentals = (await reader(db.collection('exchanges').where('walletManaged', '==', true))).docs
    .map(doc => ({path: 'exchanges/' + doc.id, data: {...doc.data(), exchangeId: doc.id}}));
  return {legacy, wallets, rentals};
}
function plan(snapshot) {
  for (const wallet of snapshot.wallets)
    c.must(Number.isSafeInteger(wallet.data.available) && wallet.data.available >= 0
      && Number.isSafeInteger(wallet.data.held) && wallet.data.held >= 0,
    'Có ví chứa số dư không hợp lệ. Cần đối soát trước khi chuyển.', 409);
  const entries = [];
  for (const source of snapshot.legacy) {
    if (source.path.startsWith('walletTopups/')) {
      c.key(source.data.userId); c.amount(source.data.amount, 1000);
      c.must(/^NAP[A-F0-9]{16}$/.test(source.path.split('/')[1]), 'Có mã nạp cũ không đúng định dạng.', 409);
    }
    if (source.path.startsWith('walletLedger/'))
      c.must(typeof source.data.userId === 'string' && Number.isSafeInteger(source.data.amount)
        && ['available','held'].includes(source.data.pocket), 'Có bút toán chưa hợp lệ; cần kiểm tra.', 409);
    const targetData = writeData(source.path, source.data, 2);
    entries.push({...source, targetPath: physicalPath(source.path, 2), targetData,
      sourceHash: hash(source.data), targetHash: hash(targetData)});
  }
  for (const source of snapshot.rentals) {
    c.amount(source.data.amount);
    for (const key of ['requesterUid','hostUid','adminUid']) c.key(source.data[key]);
    const targetData = rentalPayment(source.data);
    entries.push({...source, targetPath: 'payments/rental_' + source.data.exchangeId, targetData,
      sourceHash: hash(source.data), targetHash: hash(targetData)});
  }
  entries.sort((a,b) => a.path.localeCompare(b.path));
  c.must(new Set(entries.map(entry => entry.targetPath)).size === entries.length, 'Có mã thanh toán đích bị trùng.', 409);
  const walletProof = snapshot.wallets.map(row => [row.path, hash(row.data)]).sort((a,b) => a[0].localeCompare(b[0]));
  const manifestHash = hash({entries: entries.map(row => [row.path,row.targetPath,row.sourceHash,row.targetHash]), wallets: walletProof});
  return {entries, walletProof, manifestHash};
}
function compatible(target, entry, inactive) {
  if (!target.exists || hash(withoutMigration(target.data())) === entry.targetHash) return true;
  return inactive && target.get('_migration')?.sourcePath === entry.path;
}
function summary(snapshot, planned) {
  const counts = {};
  for (const name of LEGACY_COLLECTIONS) counts[name] = snapshot.legacy.filter(row => row.path.startsWith(name + '/')).length;
  return {storageSchema: 2, legacyCounts: counts, rentalPayments: snapshot.rentals.length,
    walletCount: snapshot.wallets.length, availableTotal: snapshot.wallets.reduce((sum,row) => sum + row.data.available, 0),
    heldTotal: snapshot.wallets.reduce((sum,row) => sum + row.data.held, 0), documentsToCopy: planned.entries.length};
}
async function inspect(db) {
  const state = storageState(await db.doc(CONTROL_PATH).get());
  c.must(state.mode === 'READY', 'Đang có lượt chuyển dữ liệu khác.', 409);
  if (state.storageSchema === 2) return {mode: 'ALREADY_ACTIVE', storageSchema: 2};
  const snapshot = await sources(db), planned = plan(snapshot);
  for (const entry of planned.entries) c.must(compatible(await db.doc(entry.targetPath).get(), entry, true),
    'Đường dẫn đích đã có dữ liệu khác: ' + entry.targetPath, 409);
  return {mode: 'PREVIEW', ...summary(snapshot,planned), manifestHash: planned.manifestHash};
}
function backupPayload(projectId, migrationId, snapshot, planned, oldPayments) {
  const payload = {format: FORMAT, projectId, migrationId, createdAt: new Date().toISOString(),
    manifestHash: planned.manifestHash,
    documents: [...snapshot.legacy,...snapshot.wallets,...snapshot.rentals,...oldPayments].map(row => ({path: row.path, data: encode(row.data)})),
    entries: planned.entries.map(row => ({sourcePath: row.path,targetPath: row.targetPath,sourceHash: row.sourceHash,targetHash: row.targetHash})),
    walletProof: planned.walletProof};
  return {...payload, integrity: hash(payload)};
}
function verifyBackup(backup, projectId) {
  c.must(backup && backup.format === FORMAT && backup.projectId === projectId,
    'Tệp sao lưu không đúng định dạng hoặc Firebase project.', 409);
  const {integrity, ...payload} = backup;
  c.must(hash(payload) === integrity, 'Tệp sao lưu đã thay đổi hoặc bị hỏng.', 409);
  const expectedManifest = hash({entries: backup.entries.map(row => [row.sourcePath,row.targetPath,row.sourceHash,row.targetHash]), wallets: backup.walletProof});
  c.must(expectedManifest === backup.manifestHash, 'Danh sách chuyển đổi không khớp tệp sao lưu.', 409);
  return backup;
}
async function applyMigration(db, {projectId, saveBackup}) {
  c.must(typeof projectId === 'string' && projectId && typeof saveBackup === 'function', 'Cần project và nơi lưu bản sao lưu.');
  const migrationId = 'MIG' + crypto.randomBytes(12).toString('hex');
  const acquired = await db.runTransaction(async tx => {
    const ref = db.doc(CONTROL_PATH), current = storageState(await tx.get(ref));
    c.must(current.mode === 'READY', 'Có lượt chuyển dữ liệu đang chạy; không tạo lượt thứ hai.', 409);
    if (current.storageSchema === 2) return false;
    tx.set(ref, {recordType: 'WALLET_SYSTEM',storageSchema: 1,mode: 'MIGRATING',migrationId,startedAt: new Date().toISOString()});
    return true;
  });
  if (!acquired) return {mode: 'ALREADY_ACTIVE', storageSchema: 2};
  try {
    const snapshot = await sources(db), planned = plan(snapshot);
    for (const entry of planned.entries) c.must(compatible(await db.doc(entry.targetPath).get(),entry,true),
      'Đường dẫn đích đã có dữ liệu khác: ' + entry.targetPath, 409);
    const oldPayments = (await scan(db,'payments')).filter(doc => doc.id !== '_system').map(doc => ({path: 'payments/' + doc.id,data: doc.data()}));
    const backup = backupPayload(projectId,migrationId,snapshot,planned,oldPayments);
    // saveBackup must finish and flush to disk before any destination document is written.
    await saveBackup(backup);
    for (let at = 0; at < planned.entries.length; at += 100) {
      const chunk = planned.entries.slice(at,at+100);
      await db.runTransaction(async tx => {
        const state = storageState(await tx.get(db.doc(CONTROL_PATH)));
        c.must(state.mode === 'MIGRATING' && state.migrationId === migrationId && state.storageSchema === 1,
          'Lượt chuyển dữ liệu đã bị thay đổi.', 409);
        const rows = [];
        for (const entry of chunk) {
          const original = await tx.get(db.doc(entry.path));
          const sourceData = original.exists ? original.data() : null;
          if (sourceData && entry.path.startsWith('exchanges/')) sourceData.exchangeId = original.id;
          c.must(original.exists && hash(sourceData) === entry.sourceHash, 'Dữ liệu nguồn đang thay đổi; chưa chuyển cấu trúc.', 409);
          const target = await tx.get(db.doc(entry.targetPath));
          c.must(compatible(target,entry,true), 'Dữ liệu đích bị thay đổi: ' + entry.targetPath, 409);
          rows.push({entry,target});
        }
        for (const {entry,target} of rows) {
          if (target.exists && hash(withoutMigration(target.data())) === entry.targetHash) continue;
          tx.set(db.doc(entry.targetPath), {...entry.targetData,
            _migration: {migrationId,sourcePath: entry.path,sourceHash: entry.sourceHash}});
        }
      });
    }
    await db.runTransaction(async tx => {
      const ref = db.doc(CONTROL_PATH), state = storageState(await tx.get(ref));
      c.must(state.storageSchema === 1 && state.mode === 'MIGRATING' && state.migrationId === migrationId,
        'Lượt chuyển dữ liệu đã bị thay đổi.', 409);
      const fresh = plan(await sources(db, query => tx.get(query)));
      c.must(fresh.manifestHash === planned.manifestHash, 'Nguồn hoặc số dư thay đổi trong khi chuyển; chưa kích hoạt.', 409);
      for (const entry of planned.entries) {
        const target = await tx.get(db.doc(entry.targetPath));
        c.must(target.exists && hash(withoutMigration(target.data())) === entry.targetHash,
          'Bản sao đích chưa khớp; chưa kích hoạt.', 409);
      }
      tx.set(ref, {recordType: 'WALLET_SYSTEM',storageSchema: 2,mode: 'READY',migrationId,
        manifestHash: planned.manifestHash,activatedAt: new Date().toISOString(),legacyPruned: false});
    });
    return {mode: 'APPLIED',...summary(snapshot,planned),manifestHash: planned.manifestHash,balancesChanged: false,legacyDeleted: false};
  } catch (error) {
    // An incomplete copy remains inactive. Never move money or overwrite live destinations.
    await db.runTransaction(async tx => {
      const ref = db.doc(CONTROL_PATH), state = storageState(await tx.get(ref));
      if (state.storageSchema === 1 && state.migrationId === migrationId)
        tx.set(ref,{recordType: 'WALLET_SYSTEM',storageSchema: 1,mode: 'READY',migrationId,lastMigration: 'FAILED'});
    }).catch(() => {});
    throw error;
  }
}
async function pruneLegacy(db, {projectId, backup}) {
  verifyBackup(backup,projectId);
  const state = storageState(await db.doc(CONTROL_PATH).get());
  c.must(state.mode === 'READY' && state.storageSchema === 2 && state.manifestHash === backup.manifestHash
    && state.migrationId === backup.migrationId, 'Chưa kích hoạt đúng bản chuyển đổi của tệp sao lưu này.', 409);
  const entries = backup.entries.filter(row => LEGACY_COLLECTIONS.includes(row.sourcePath.split('/')[0]));
  const originals = new Map(backup.documents.map(row => [row.path,row.data]));
  function targetIntact(target, entry) {
    if (!target.exists) return false;
    const collection=entry.sourcePath.split('/')[0], expected=originals.get(entry.sourcePath), value=target.data();
    if (['walletLedger','walletOperations'].includes(collection))
      return hash(withoutMigration(value))===entry.targetHash;
    const fields=collection==='walletTopups'?['userId','amount','currency','content']:
      collection==='walletBankEvents'?['id','amount','topupId','bank','account','direction','reference','time']:['userId'];
    if (collection==='walletTopups' && value.recordType!=='TOPUP')return false;
    return fields.every(key=>!(key in expected)
      || (value[key]!==undefined && hash(encode(value[key]))===hash(expected[key])));
  }
  for (const entry of entries) {
    const source = await db.doc(entry.sourcePath).get(), target = await db.doc(entry.targetPath).get();
    c.must(!source.exists || hash(source.data()) === entry.sourceHash,
      'Nguồn cũ còn đang được ghi; dừng backend cũ trước khi dọn.', 409);
    c.must(targetIntact(target,entry), 'Bản đích thiếu hoặc không còn khớp; giữ nguyên dữ liệu cũ.', 409);
  }
  let deleted = 0;
  for (let at = 0; at < entries.length; at += 100) {
    deleted += await db.runTransaction(async tx => {
      const current = storageState(await tx.get(db.doc(CONTROL_PATH)));
      c.must(current.storageSchema === 2 && current.mode === 'READY' && current.manifestHash === backup.manifestHash,
        'Cấu hình thanh toán đã đổi; ngừng dọn.', 409);
      const refs = [];
      for (const entry of entries.slice(at,at+100)) {
        const source = await tx.get(db.doc(entry.sourcePath)), target = await tx.get(db.doc(entry.targetPath));
        c.must(!source.exists || hash(source.data()) === entry.sourceHash, 'Nguồn cũ đang bị ghi thêm; ngừng dọn.', 409);
        c.must(targetIntact(target,entry), 'Bản đích thiếu hoặc không còn khớp; ngừng dọn.', 409);
        if (source.exists) refs.push(source.ref);
      }
      for (const ref of refs) tx.delete(ref);
      return refs.length;
    });
  }
  let remaining = 0;
  for (const name of LEGACY_COLLECTIONS) remaining += (await scan(db,name)).length;
  await db.runTransaction(async tx => {
    const ref=db.doc(CONTROL_PATH), current=storageState(await tx.get(ref));
    c.must(current.storageSchema===2 && current.manifestHash===backup.manifestHash,'Cấu hình đã đổi.',409);
    tx.set(ref,{...current,legacyPruned: remaining===0,prunedAt: new Date().toISOString()});
  });
  return {mode: 'PRUNED',deleted,legacyRemaining: remaining,balancesChanged: false};
}
async function cancelMigration(db) {
  return db.runTransaction(async tx => {
    const ref=db.doc(CONTROL_PATH), state=storageState(await tx.get(ref));
    c.must(state.storageSchema===1,'Cấu trúc mới đã kích hoạt; không chuyển ngược về bản cũ.',409);
    if(state.mode==='READY')return {mode:'ALREADY_READY',storageSchema:1};
    tx.set(ref,{...state,mode:'READY',lastMigration:'CANCELLED'});
    return {mode:'CANCELLED',storageSchema:1,balancesChanged:false};
  });
}
function options(args) {
  const out={mode:'PREVIEW'};
  for(let i=0;i<args.length;i++) {
    const flag=args[i];
    if(['--apply','--prune-legacy','--cancel-migration'].includes(flag)) {
      c.must(out.mode==='PREVIEW','Chỉ chọn một thao tác.');
      out.mode=({'--apply':'APPLY','--prune-legacy':'PRUNE','--cancel-migration':'CANCEL'})[flag];
    } else if(['--project','--backup','--backend-url','--output'].includes(flag)) {
      const key=({'--project':'projectId','--backup':'backupFile','--backend-url':'backendUrl','--output':'output'})[flag];
      c.must(!out[key] && args[i+1] && !args[i+1].startsWith('--'),'Thiếu hoặc lặp '+flag+'.');out[key]=args[++i];
    } else throw new c.ApiError(400,'Dùng --project PROJECT; mặc định xem trước. --apply hoặc --prune-legacy cần --backup FILE.');
  }
  c.must(out.projectId,'Cần --project để xác nhận đúng Firebase project.');
  if(['APPLY','PRUNE'].includes(out.mode))c.must(out.backupFile,'Cần --backup FILE.');
  return out;
}
async function checkBackend(input) {
  if(process.env.FIRESTORE_EMULATOR_HOST){c.must(input.projectId.startsWith('demo-'),'Emulator chỉ dùng project demo-.');return;}
  c.must(input.backendUrl,'Cần --backend-url URL của backend mới để kiểm tra trước khi chuyển.');
  const url=new URL(input.backendUrl);c.must(url.protocol==='https:' && url.pathname==='/' && !url.search && !url.username && !url.password,'Dùng URL HTTPS gốc của backend.');
  const response=await fetch(new URL('/health',url),{signal:AbortSignal.timeout(120000)});
  c.must(response.ok,'Backend chưa hoạt động.');const health=await response.json();
  c.must(health.ok===true && health.storageAdapterVersion===1 && health.projectId===input.projectId,
    'Backend chưa có bộ chuyển đổi thanh toán hoặc đang dùng Firebase project khác.',409);
}
function saveBackupFile(filename,backup) {
  const full=path.resolve(filename);fs.mkdirSync(path.dirname(full),{recursive:true});
  const fd=fs.openSync(full,'wx',0o600);
  try{fs.writeFileSync(fd,JSON.stringify(backup,null,2)+'\n','utf8');fs.fsyncSync(fd);}finally{fs.closeSync(fd);}
}
async function main() {
  const input=options(process.argv.slice(2));
  c.must(process.env.FIREBASE_PROJECT_ID===input.projectId,'--project phải khớp FIREBASE_PROJECT_ID.');
  if(input.mode!=='PREVIEW')await checkBackend(input);
  const {initializeApp,applicationDefault,deleteApp}=require('firebase-admin/app');
  const {getFirestore}=require('firebase-admin/firestore');
  const app=initializeApp({projectId:input.projectId,...(process.env.FIRESTORE_EMULATOR_HOST?{}:{credential:applicationDefault()})});
  try {
    const db=getFirestore(app);let result;
    if(input.mode==='APPLY')result=await applyMigration(db,{projectId:input.projectId,saveBackup:backup=>saveBackupFile(input.backupFile,backup)});
    else if(input.mode==='PRUNE')result=await pruneLegacy(db,{projectId:input.projectId,backup:JSON.parse(fs.readFileSync(input.backupFile,'utf8'))});
    else if(input.mode==='CANCEL')result=await cancelMigration(db);
    else result=await inspect(db);
    const text=JSON.stringify(result,null,2)+'\n';
    if(input.output)fs.writeFileSync(input.output,text,{encoding:'utf8',mode:0o600});
    process.stdout.write(text);
  } finally {await deleteApp(app);}
}
if(require.main===module)main().catch(error=>{console.error('Chưa chuyển dữ liệu:',error.status?error.message:(error.code||error.name));process.exitCode=1;});
module.exports={encode,hash,inspect,applyMigration,pruneLegacy,cancelMigration,options,verifyBackup,FORMAT};
