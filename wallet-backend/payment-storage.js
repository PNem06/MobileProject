'use strict';
const c = require('./core');

const CONTROL_PATH = 'payments/_system';
const INTERNAL = Object.freeze({
  walletLedger: 'ledger', walletBankEvents: 'bankEvents', walletOperations: 'operations',
  walletTopupLocks: 'topupLocks', walletBookingLocks: 'bookingLocks'
});
const LEGACY_COLLECTIONS = Object.freeze(['walletTopups', ...Object.keys(INTERNAL)]);

function storageState(snapshot) {
  if (!snapshot.exists) return {storageSchema: 1, mode: 'READY'};
  const data = snapshot.data();
  c.must(data.recordType === 'WALLET_SYSTEM' && [1, 2].includes(data.storageSchema)
    && ['READY', 'MIGRATING'].includes(data.mode), 'Cấu hình lưu trữ thanh toán cần được kiểm tra.', 503);
  return data;
}
function available(state) {
  c.must(state.mode === 'READY', 'Ví đang chuyển dữ liệu. Vui lòng thử lại sau ít phút.', 503);
}
function collectionPath(name, schema) {
  if (schema !== 2) return name;
  if (name === 'walletTopups') return 'payments';
  return INTERNAL[name] ? CONTROL_PATH + '/' + INTERNAL[name] : name;
}
function physicalPath(path, schema) {
  const slash = path.lastIndexOf('/');
  return slash < 0 ? collectionPath(path, schema)
    : collectionPath(path.slice(0, slash), schema) + path.slice(slash);
}
function writeData(path, value, schema) {
  return schema === 2 && path.startsWith('walletTopups/')
    ? {...value, recordType: 'TOPUP', storageSchema: 2} : value;
}
function rentalPayment(booking) {
  const result = {recordType: 'RENTAL', storageSchema: 2,
    paymentId: 'rental_' + booking.exchangeId, exchangeId: booking.exchangeId,
    userId: booking.requesterUid, requesterUid: booking.requesterUid, hostUid: booking.hostUid,
    adminUid: booking.adminUid, postId: booking.postId, amount: booking.amount,
    currency: booking.currency || 'VND', paymentSource: 'WALLET', status: booking.paymentStatus,
    settlementState: booking.settlementState};
  for (const key of ['createdAt', 'updatedAt', 'paidAt', 'cancelUntil', 'releaseAt', 'acceptedAt',
    'releasedAt', 'refundedAt', 'refundedAmount', 'cancellationFee']) {
    if (booking[key] !== undefined) result[key] = booking[key];
  }
  return result;
}

/** Logical names keep API IDs stable. The transaction reads the schema gate before any write. */
class PaymentDatabase {
  constructor(raw) { this.raw = raw; }
  static from(db) { return db instanceof PaymentDatabase ? db : new PaymentDatabase(db); }
  async state() { return storageState(await this.raw.doc(CONTROL_PATH).get()); }
  doc(path) { return new PaymentReference(this, path); }
  collection(name) { return new PaymentQuery(this, name); }
  async runTransaction(operation) {
    return this.raw.runTransaction(async rawTx => {
      const state = storageState(await rawTx.get(this.raw.doc(CONTROL_PATH)));
      available(state);
      const schema = state.storageSchema;
      const target = reference => {
        c.must(reference instanceof PaymentReference || reference instanceof PaymentQuery,
          'Tham chiếu giao dịch không hợp lệ.', 500);
        return reference.resolve(schema);
      };
      const tx = {storageSchema: schema,
        get: async reference => reference.wrap(await rawTx.get(target(reference))),
        create: (reference, data) => rawTx.create(target(reference), writeData(reference.path, data, schema)),
        set: (reference, data, options) => options
          ? rawTx.set(target(reference), writeData(reference.path, data, schema), options)
          : rawTx.set(target(reference), writeData(reference.path, data, schema)),
        update: (reference, data) => rawTx.update(target(reference), writeData(reference.path, data, schema))};
      return operation(tx);
    });
  }
}
class PaymentReference {
  constructor(db, path) { this.db = db; this.path = path; this.id = path.split('/').pop(); }
  resolve(schema) { return this.db.raw.doc(physicalPath(this.path, schema)); }
  wrap(snapshot) {
    return {id: this.id, ref: this, exists: snapshot.exists,
      data: () => snapshot.data(), get: field => snapshot.get(field)};
  }
  async get() {
    const state = await this.db.state(); available(state);
    const snapshot = await this.resolve(state.storageSchema).get();
    if (state.storageSchema === 2 && this.path.startsWith('walletTopups/') && snapshot.exists)
      c.must(snapshot.get('recordType') === 'TOPUP', 'Mã nạp trùng với dữ liệu thanh toán khác.', 409);
    return this.wrap(snapshot);
  }
}
class PaymentQuery {
  constructor(db, name, steps = []) { this.db = db; this.name = name; this.steps = steps; }
  next(method, args) { return new PaymentQuery(this.db, this.name, [...this.steps, [method, args]]); }
  where(...args) { return this.next('where', args); }
  orderBy(...args) { return this.next('orderBy', args); }
  limit(...args) { return this.next('limit', args); }
  startAfter(snapshot) { return this.next('startAfter', [snapshot]); }
  resolve(schema) {
    let query = this.db.raw.collection(collectionPath(this.name, schema));
    if (schema === 2 && this.name === 'walletTopups') query = query.where('recordType', '==', 'TOPUP');
    for (const [method, args] of this.steps) {
      if (method === 'startAfter') {
        const snapshot = args[0];
        // The document ID is the order cursor used by paged backend jobs.
        query = query.startAfter(snapshot.id);
      } else query = query[method](...args);
    }
    return query;
  }
  wrap(snapshot) {
    const docs = snapshot.docs.map(doc => new PaymentReference(this.db, this.name + '/' + doc.id).wrap(doc));
    return {docs, size: docs.length, empty: docs.length === 0};
  }
  async get() {
    const state = await this.db.state(); available(state);
    return this.wrap(await this.resolve(state.storageSchema).get());
  }
}
module.exports = {PaymentDatabase, CONTROL_PATH, INTERNAL, LEGACY_COLLECTIONS,
  collectionPath, physicalPath, writeData, rentalPayment, storageState};
