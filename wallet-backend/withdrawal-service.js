'use strict';
const crypto = require('node:crypto');
const c = require('./core');

const ACTIVE = new Set(['PENDING', 'PROCESSING']);
const paymentPath = id => 'payments/withdrawal_' + c.key(id);
const lockPath = uid => 'payments/_system/withdrawalLocks/' + c.key(uid);

function recipient(input) {
  const clean = (value, label, max) => {
    c.must(typeof value === 'string', 'Vui lòng nhập ' + label + '.');
    const text = value.trim().replace(/ +/g, ' ');
    c.must(text.length > 0 && text.length <= max && !/[\p{C}]/u.test(text), label + ' không hợp lệ.');
    return text;
  };
  const bankName = clean(input.bankName, 'tên ngân hàng', 100);
  const accountNumber = clean(input.accountNumber, 'số tài khoản', 30);
  c.must(/^[0-9]{5,30}$/.test(accountNumber), 'Số tài khoản phải gồm 5 đến 30 chữ số.');
  const accountName = clean(input.accountName, 'tên chủ tài khoản', 100);
  c.must(/^[\p{L}\p{M} .'-]+$/u.test(accountName), 'Tên chủ tài khoản không hợp lệ.');
  return {bankName, accountNumber, accountName};
}

/** Money leaves the internal ledger only after the claiming admin confirms a manual bank transfer. */
class Withdrawals {
  constructor(service) { this.s = service; }
  async get(actor, id) {
    const doc = await this.s.ref(paymentPath(id)).get();
    c.must(doc.exists && doc.get('recordType') === 'WITHDRAWAL', 'Không tìm thấy yêu cầu rút tiền.', 404);
    c.must(actor.isAdmin || doc.get('userId') === actor.uid, 'Bạn không có quyền xem yêu cầu rút này.', 403);
    return doc.data();
  }
  async list(actor) {
    let query = this.s.db.collection('payments').where('recordType', '==', 'WITHDRAWAL');
    if (!actor.isAdmin) query = query.where('userId', '==', actor.uid);
    const rows = (await query.get()).docs.map(doc => doc.data());
    rows.sort((a, b) => Number(ACTIVE.has(b.status)) - Number(ACTIVE.has(a.status))
      || c.milliseconds(b.createdAt) - c.milliseconds(a.createdAt));
    // Never hide an open financial request behind the history display limit.
    const active = rows.filter(row => ACTIVE.has(row.status));
    return {withdrawals: [...active, ...rows.filter(row => !ACTIVE.has(row.status)).slice(0, 100)],
      withdrawalPendingCount: rows.filter(row => ACTIVE.has(row.status)).length,
      withdrawalPendingAmount: rows.filter(row => ACTIVE.has(row.status)).reduce((sum, row) => sum + row.amount, 0)};
  }
  async create(actor, input) {
    c.must(!actor.isAdmin, 'Ví quản trị không dùng để tạo yêu cầu rút cá nhân.', 403);
    const s = this.s, amount = c.amount(input.amount, 1000), bank = recipient(input);
    const op = c.operation(input.operationId);
    const fingerprint = c.hash(JSON.stringify({amount, ...bank}));
    const opRef = s.ref('walletOperations/' + c.hash(actor.uid + '/withdraw/' + op));
    const lockRef = s.ref(lockPath(actor.uid));
    const id = 'RUT' + crypto.randomBytes(8).toString('hex').toUpperCase();
    return s.db.runTransaction(async tx => {
      const previousOp = await tx.get(opRef);
      if (previousOp.exists) {
        c.must(previousOp.get('fingerprint') === fingerprint, 'Mã thao tác đã dùng với thông tin rút khác.', 409);
        const previous = await tx.get(s.ref(paymentPath(previousOp.get('withdrawalId'))));
        c.must(previous.exists && previous.get('userId') === actor.uid && previous.get('recordType') === 'WITHDRAWAL',
          'Yêu cầu rút cũ cần được đối soát.', 409);
        return previous.data();
      }
      const lock = await tx.get(lockRef);
      if (lock.exists && lock.get('withdrawalId')) {
        const pending = await tx.get(s.ref(paymentPath(lock.get('withdrawalId'))));
        c.must(pending.exists && pending.get('userId') === actor.uid, 'Khóa rút tiền cần được đối soát.', 409);
        if (ACTIVE.has(pending.get('status'))) {
          c.must(pending.get('fingerprint') === fingerprint, 'Bạn đang có yêu cầu rút chờ xử lý. Hãy mở yêu cầu đó trước.', 409);
          tx.create(opRef, {userId: actor.uid, withdrawalId: pending.get('withdrawalId'), fingerprint, createdAt: s.time()});
          return pending.data();
        }
      }
      const balances = await s.balances(tx, [actor.uid]);
      c.must(balances.get(actor.uid).value.available >= amount, 'Số dư khả dụng không đủ để rút.', 409);
      const withdrawal = {recordType: 'WITHDRAWAL', storageSchema: tx.storageSchema,
        paymentId: 'withdrawal_' + id, withdrawalId: id, userId: actor.uid,
        accountId: actor.accountId, userName: actor.name, amount, currency: 'VND', ...bank,
        status: 'PENDING', fingerprint, createdAt: s.time(), updatedAt: s.time()};
      s.move(tx, balances, actor.uid, 'available', -amount, 'Giữ tiền cho yêu cầu rút', id + '_reserve_available', {withdrawalId: id});
      s.move(tx, balances, actor.uid, 'held', amount, 'Tiền rút đang chờ xử lý', id + '_reserve_held', {withdrawalId: id});
      s.saveBalances(tx, balances);
      tx.create(s.ref(paymentPath(id)), withdrawal);
      tx.set(lockRef, {userId: actor.uid, withdrawalId: id, updatedAt: s.time()});
      tx.create(opRef, {userId: actor.uid, withdrawalId: id, fingerprint, createdAt: s.time()});
      s.notify(tx, actor.uid, id + '_requested_user', 'Đã gửi yêu cầu rút tiền',
        amount.toLocaleString('vi-VN') + 'đ được giữ để chờ quản trị chuyển khoản.', withdrawal);
      s.notify(tx, s.config.adminUid, id + '_requested_admin', 'Có yêu cầu rút tiền mới',
        actor.name + ' yêu cầu rút ' + amount.toLocaleString('vi-VN') + 'đ.', withdrawal);
      return withdrawal;
    });
  }
  async action(actor, input) {
    c.must(actor.isAdmin, 'Chỉ quản trị viên được xử lý yêu cầu rút.', 403);
    const s = this.s, id = c.key(input.withdrawalId), action = input.action;
    c.must(['CLAIM', 'PAID', 'REJECT'].includes(action), 'Thao tác rút tiền không hợp lệ.');
    const op = c.operation(input.operationId);
    if (action === 'PAID') c.must(input.confirmTransferred === true, 'Chỉ xác nhận sau khi đã chuyển khoản thành công.');
    let reason = '';
    if (action === 'REJECT') {
      c.must(input.confirmUnpaid === true, 'Chỉ từ chối khi chưa chuyển khoản cho người dùng.');
      c.must(typeof input.reason === 'string', 'Vui lòng nhập lý do từ chối.');
      reason = input.reason.trim();
      c.must(reason.length >= 3 && reason.length <= 300 && !/[\p{C}]/u.test(reason), 'Lý do từ chối cần từ 3 đến 300 ký tự.');
    }
    const opRef = s.ref('walletOperations/' + c.hash(actor.uid + '/withdraw-action/' + op));
    return s.db.runTransaction(async tx => {
      const oldOp = await tx.get(opRef), ref = s.ref(paymentPath(id)), doc = await tx.get(ref);
      c.must(doc.exists && doc.get('recordType') === 'WITHDRAWAL', 'Không tìm thấy yêu cầu rút tiền.', 404);
      const row = doc.data();
      if (oldOp.exists) {
        c.must(oldOp.get('withdrawalId') === id && oldOp.get('action') === action,
          'Mã thao tác đã dùng cho quyết định khác.', 409);
        return row;
      }
      // A response can be lost after commit; repeating a successful click never pays or refunds twice.
      if ((action === 'PAID' && row.status === 'PAID' && row.paidBy === actor.uid)
          || (action === 'REJECT' && row.status === 'REJECTED' && row.rejectedBy === actor.uid)
          || (action === 'CLAIM' && row.status === 'PROCESSING' && row.processingAdminUid === actor.uid)) return row;
      c.must(ACTIVE.has(row.status), 'Yêu cầu rút đã được xử lý.', 409);
      c.amount(row.amount, 1000); c.key(row.userId);
      const lockRef = s.ref(lockPath(row.userId)), lock = await tx.get(lockRef);
      c.must(lock.exists && lock.get('withdrawalId') === id, 'Khóa yêu cầu rút cần được đối soát.', 409);
      if (action === 'CLAIM') {
        c.must(row.status === 'PENDING', 'Một quản trị viên khác đã nhận xử lý yêu cầu này.', 409);
        const update = {status: 'PROCESSING', processingAdminUid: actor.uid,
          processingAdminName: actor.name, processingAt: s.time(), updatedAt: s.time()};
        tx.update(ref, update);
        tx.create(opRef, {withdrawalId: id, action, userId: actor.uid, createdAt: s.time()});
        s.notify(tx, row.userId, id + '_processing', 'Yêu cầu rút đang được xử lý',
          'Quản trị đã tiếp nhận yêu cầu rút ' + row.amount.toLocaleString('vi-VN') + 'đ.', row);
        return {...row, ...update};
      }
      if (action === 'PAID') c.must(row.status === 'PROCESSING', 'Cần nhận xử lý trước khi chuyển khoản và xác nhận.', 409);
      if (row.status === 'PROCESSING') c.must(row.processingAdminUid === actor.uid,
        'Chỉ quản trị viên đã nhận xử lý được xác nhận hoặc từ chối yêu cầu này.', 403);
      const balances = await s.balances(tx, [row.userId]);
      c.must(balances.get(row.userId).value.held >= row.amount, 'Tiền giữ của yêu cầu rút cần được đối soát.', 409);
      const paid = action === 'PAID';
      s.move(tx, balances, row.userId, 'held', -row.amount,
        paid ? 'Đã chuyển khoản rút tiền' : 'Giải phóng tiền rút bị từ chối', id + '_close_held', {withdrawalId: id});
      if (!paid) s.move(tx, balances, row.userId, 'available', row.amount,
        'Hoàn số dư do từ chối rút tiền', id + '_reject_available', {withdrawalId: id});
      s.saveBalances(tx, balances);
      const update = paid ? {status: 'PAID', paidAt: s.time(), paidBy: actor.uid}
        : {status: 'REJECTED', rejectedAt: s.time(), rejectedBy: actor.uid, rejectionReason: reason};
      update.updatedAt = s.time();
      tx.update(ref, update);
      tx.set(lockRef, {userId: row.userId, withdrawalId: '', updatedAt: s.time()});
      tx.create(opRef, {withdrawalId: id, action, userId: actor.uid, createdAt: s.time()});
      const title = paid ? 'Rút tiền thành công' : 'Yêu cầu rút bị từ chối';
      const body = paid ? 'Quản trị xác nhận đã chuyển ' + row.amount.toLocaleString('vi-VN') + 'đ về tài khoản ngân hàng của bạn.'
        : row.amount.toLocaleString('vi-VN') + 'đ đã được trả lại số dư khả dụng. Lý do: ' + reason;
      s.notify(tx, row.userId, id + '_closed_user', title, body, row);
      s.notify(tx, s.config.adminUid, id + '_closed_admin', title, row.userName + ': ' + body, row);
      return {...row, ...update};
    });
  }
}
module.exports = {Withdrawals, recipient};
