'use strict';

const crypto = require('node:crypto');
const { getApps, initializeApp } = require('firebase-admin/app');
const { getAuth } = require('firebase-admin/auth');
const { getDatabase } = require('firebase-admin/database');
const { onRequest } = require('firebase-functions/v2/https');
const { setGlobalOptions } = require('firebase-functions/v2');
const { defineSecret, defineString } = require('firebase-functions/params');
const { SEAT_PRICE_VND, BOOKING_FEE_VND, SEAT_PATTERN, ORDER_PATTERN, amountForSeats } = require('./domain');

if (getApps().length === 0) initializeApp();
setGlobalOptions({ region: 'asia-southeast1', maxInstances: 10 });

const db = getDatabase();
const HASH_SECRET = defineSecret('VNPAY_HASH_SECRET');
const ADMIN_BOOTSTRAP_CODE = defineSecret('ADMIN_BOOTSTRAP_CODE');
const TMN_CODE = defineString('VNPAY_TMN_CODE', { default: 'GW923J6D' });
const API_BASE_URL = defineString('VNPAY_API_BASE_URL', { default: '' });

const VNPAY_PAY_URL = 'https://sandbox.vnpayment.vn/paymentv2/vpcpay.html';
const PAYMENT_TTL_MS = 15 * 60 * 1000;

function text(value, maxLength = 160) {
  return typeof value === 'string' ? value.trim().slice(0, maxLength) : '';
}

function fail(message, statusCode = 400) {
  const error = new Error(message);
  error.statusCode = statusCode;
  throw error;
}

function required(value, label, maxLength) {
  const result = text(value, maxLength);
  if (!result) fail('Thiếu thông tin ' + label);
  return result;
}

function vietnamLocalDate(timestamp = Date.now()) {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(new Date(timestamp));
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${values.year}-${values.month}-${values.day}`;
}

function validateBookingDate(value) {
  const date = text(value, 10) || vietnamLocalDate();
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) fail('Ngày chiếu không hợp lệ.');
  const [year, month, day] = date.split('-').map(Number);
  const parsed = new Date(Date.UTC(year, month - 1, day));
  if (parsed.toISOString().slice(0, 10) !== date) fail('Ngày chiếu không hợp lệ.');
  const today = vietnamLocalDate();
  const [todayYear, todayMonth, todayDay] = today.split('-').map(Number);
  const latest = new Date(Date.UTC(todayYear, todayMonth - 1, todayDay));
  latest.setUTCDate(latest.getUTCDate() + 6);
  if (date < today || date > latest.toISOString().slice(0, 10)) fail('Chỉ có thể đặt vé trong 7 ngày tới.');
  return date;
}

async function authenticate(request) {
  const authorization = request.get('authorization') || '';
  const match = authorization.match(/^Bearer\s+(.+)$/i);
  if (!match) fail('Vui lòng đăng nhập để tiếp tục.', 401);
  try {
    return await getAuth().verifyIdToken(match[1], true);
  } catch (_) {
    fail('Phiên đăng nhập không hợp lệ hoặc đã hết hạn.', 401);
  }
}

function requireStaff(identity, adminOnly = false) {
  if (adminOnly ? identity.admin !== true : identity.staff !== true && identity.admin !== true) {
    fail('Tài khoản chưa được cấp quyền cho thao tác này.', 403);
  }
}

function normalizeBooking(body, identity) {
  const inputSeats = Array.isArray(body.seats)
    ? body.seats
    : text(body.seats || body.ghe, 500).split(',');
  const seats = [...new Set(inputSeats.map((seat) => text(String(seat), 4).toUpperCase()).filter(Boolean))];
  if (seats.length === 0 || seats.length > 30 || seats.some((seat) => !SEAT_PATTERN.test(seat))) {
    fail('Danh sách ghế không hợp lệ.');
  }
  if (Array.isArray(inputSeats) && seats.length !== inputSeats.length) fail('Danh sách ghế bị trùng.');

  const email = text(identity.email, 160).toLowerCase();
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) fail('Tài khoản cần có email hợp lệ.', 401);

  return {
    ownerUid: identity.uid,
    title: required(body.title, 'phim', 160),
    bookingDate: validateBookingDate(body.bookingDate),
    cinema: required(body.cinema, 'rạp', 120),
    showtime: required(body.showtime, 'suất chiếu', 80),
    seats,
    province: text(body.province, 100),
    email,
    customerName: text(body.customerName, 120) || email.split('@')[0] || 'Khách hàng',
    posterUrl: text(body.posterUrl, 1000),
  };
}

function bookingFingerprint(booking, method) {
  return JSON.stringify({
    ownerUid: booking.ownerUid,
    title: booking.title,
    bookingDate: booking.bookingDate,
    cinema: booking.cinema,
    showtime: booking.showtime,
    seats: [...booking.seats].sort(),
    province: booking.province,
    email: booking.email,
    customerName: booking.customerName,
    paymentMethod: method,
  });
}

function encodeVnp(value) {
  return encodeURIComponent(String(value))
    .replace(/[!'()~]/g, (character) => '%' + character.charCodeAt(0).toString(16).toUpperCase())
    .replace(/%20/g, '+');
}

function hashData(params) {
  return Object.keys(params)
    .filter((key) => key !== 'vnp_SecureHash' && key !== 'vnp_SecureHashType' && params[key] !== '')
    .sort()
    .map((key) => encodeVnp(key) + '=' + encodeVnp(params[key]))
    .join('&');
}

function secureHash(params) {
  return crypto.createHmac('sha512', HASH_SECRET.value()).update(hashData(params), 'utf8').digest('hex');
}

function verifySecureHash(params) {
  const received = params.vnp_SecureHash;
  if (typeof received !== 'string' || !/^[a-fA-F0-9]{128}$/.test(received)) return false;
  const expectedBytes = Buffer.from(secureHash(params), 'hex');
  const receivedBytes = Buffer.from(received, 'hex');
  return expectedBytes.length === receivedBytes.length && crypto.timingSafeEqual(expectedBytes, receivedBytes);
}

function signatureMatches(received, expected) {
  if (typeof received !== 'string' || !/^[a-fA-F0-9]{128}$/.test(received)) return false;
  const expectedBytes = Buffer.from(expected, 'hex');
  const receivedBytes = Buffer.from(received, 'hex');
  return expectedBytes.length === receivedBytes.length && crypto.timingSafeEqual(expectedBytes, receivedBytes);
}

function stateSignature(domain, payment) {
  const payload = domain === 'ORDER'
    ? [
      payment.paymentId, payment.ownerUid, payment.title, payment.cinema, payment.showtime,
      payment.seats, payment.province, payment.bookingDate, payment.email, payment.customerName,
      payment.posterUrl, payment.amount, payment.paymentMethod, payment.createdAt, payment.expiresAt,
      payment.lockExpiresAt, payment.lockKey, payment.fingerprint, payment.paymentUrl || '',
    ]
    : [
      payment.paymentId, payment.status, payment.amount, payment.paymentMethod, payment.createdAt,
      payment.expiresAt, payment.paidAt || '', payment.failedAt || '', payment.expiredAt || '',
      payment.ticketIssuedAt || '', payment.ticketCode || '', payment.vnpTransactionNo || '',
      payment.confirmedByUid || '', payment.orderSignature,
    ];
  return crypto.createHmac('sha512', HASH_SECRET.value())
    .update('TTTT_' + domain + '|' + JSON.stringify(payload), 'utf8')
    .digest('hex');
}

function hasValidOrderSignature(payment) {
  return Boolean(payment && signatureMatches(payment.orderSignature, stateSignature('ORDER', payment)));
}

function hasValidStatusSignature(payment) {
  return Boolean(payment && signatureMatches(payment.statusSignature, stateSignature('STATUS', payment)));
}

function buildPaymentUrl(params) {
  const query = Object.keys(params)
    .sort()
    .map((key) => encodeVnp(key) + '=' + encodeVnp(params[key]))
    .join('&');
  return VNPAY_PAY_URL + '?' + query + '&vnp_SecureHash=' + secureHash(params);
}

function formatVnpDate(timestamp) {
  const values = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Ho_Chi_Minh',
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
  }).formatToParts(new Date(timestamp));
  const parts = Object.fromEntries(values.map((part) => [part.type, part.value]));
  return parts.year + parts.month + parts.day + parts.hour + parts.minute + parts.second;
}

function apiBaseUrl() {
  const configured = API_BASE_URL.value().replace(/\/$/, '');
  if (configured) return configured;
  const projectId = process.env.GCLOUD_PROJECT || process.env.GCP_PROJECT || admin.app().options.projectId;
  return `https://asia-southeast1-${projectId}.cloudfunctions.net/vnpayApi`;
}

function vnpayParams(payment, request, now) {
  return {
    vnp_Version: '2.1.0',
    vnp_Command: 'pay',
    vnp_TmnCode: TMN_CODE.value(),
    vnp_Amount: String(payment.amount * 100),
    vnp_CurrCode: 'VND',
    vnp_TxnRef: payment.paymentId,
    vnp_OrderInfo: 'Thanh toan ve xem phim ' + payment.paymentId,
    vnp_OrderType: 'billpayment',
    vnp_Locale: 'vn',
    vnp_ReturnUrl: apiBaseUrl() + '/api/payments/vnpay/return',
    vnp_IpAddr: text(request.ip, 45).replace(/^::ffff:/, '') || '127.0.0.1',
    vnp_CreateDate: formatVnpDate(now),
    vnp_ExpireDate: formatVnpDate(now + PAYMENT_TTL_MS),
  };
}

function scheduleLockKey(booking) {
  return crypto.createHash('sha256')
    .update([booking.title, booking.bookingDate, booking.cinema, booking.showtime].join('\u0000'))
    .digest('hex');
}

function splitSeats(value) {
  return typeof value === 'string'
    ? value.split(',').map((seat) => seat.trim().toUpperCase()).filter(Boolean)
    : Array.isArray(value) ? value.map((seat) => text(seat, 4).toUpperCase()).filter(Boolean) : [];
}

function isCancelledTicket(ticket) {
  const status = text(ticket.status || ticket.tt, 60).toLowerCase();
  return ['cancelled', 'failed', 'expired', 'hủy', 'huỷ', 'đã hủy', 'đã huỷ', 'thanh toán thất bại'].includes(status);
}

function ticketIsPaid(ticket) {
  if (isCancelledTicket(ticket)) return false;
  return ticket.status === 'PAID' || ticket.tt === 'Đã xác nhận' || ticket.paymentId;
}

async function bookedSeats(booking) {
  const snapshot = await db.ref('Ticker').once('value');
  const booked = new Set();
  snapshot.forEach((ticketSnapshot) => {
    const ticket = ticketSnapshot.val() || {};
    const title = ticket.title || ticket.phim;
    const cinema = ticket.cinema || ticket.rap;
    const showtime = ticket.showtime || ticket.lich;
    if (!ticketIsPaid(ticket) || title !== booking.title || ticket.bookingDate !== booking.bookingDate || cinema !== booking.cinema || showtime !== booking.showtime) return;
    splitSeats(ticket.seats || ticket.ghe).forEach((seat) => booked.add(seat));
  });
  return booked;
}

async function ensureSeatsAvailable(booking) {
  const booked = await bookedSeats(booking);
  const selected = booking.seats.find((seat) => booked.has(seat));
  if (selected) fail(`Ghế ${selected} đã được đặt. Vui lòng chọn ghế khác.`, 409);
}

async function reserveSeats(paymentId, booking, expiresAt) {
  const lockRef = db.ref('SeatLocks/' + scheduleLockKey(booking));
  const now = Date.now();
  const result = await lockRef.transaction((current) => {
    const locks = current && typeof current === 'object' ? { ...current } : {};
    for (const [seat, lock] of Object.entries(locks)) {
      if (!lock || Number(lock.expiresAt) <= now) delete locks[seat];
    }
    for (const seat of booking.seats) {
      if (locks[seat] && locks[seat].paymentId !== paymentId) return;
    }
    for (const seat of booking.seats) locks[seat] = { paymentId, expiresAt };
    return locks;
  }, undefined, false);
  if (!result.committed) fail('Một hoặc nhiều ghế đang được giữ trong đơn khác. Vui lòng chọn lại.', 409);
}

async function releaseSeats(payment) {
  if (!payment || !payment.lockKey || !Array.isArray(payment.seats)) return;
  const lockRef = db.ref('SeatLocks/' + payment.lockKey);
  await lockRef.transaction((current) => {
    if (!current || typeof current !== 'object') return current;
    const locks = { ...current };
    for (const seat of payment.seats) {
      if (locks[seat] && locks[seat].paymentId === payment.paymentId) delete locks[seat];
    }
    return Object.keys(locks).length ? locks : null;
  }, undefined, false);
}

function ticketQrPayload(payment, ticketCode) {
  const signature = crypto.createHmac('sha256', HASH_SECRET.value())
    .update(['TTTT_TICKET', payment.ownerUid, payment.paymentId, ticketCode].join('|'), 'utf8')
    .digest('hex').slice(0, 32);
  return `TTTT1|${payment.paymentId}|${ticketCode}|${signature}`;
}

function ticketFromPayment(payment, ticketCode) {
  return {
    ownerUid: payment.ownerUid,
    email: payment.email,
    customerName: payment.customerName,
    title: payment.title,
    cinema: payment.cinema,
    showtime: payment.showtime,
    seats: payment.seats.join(', '),
    province: payment.province || '',
    bookingDate: payment.bookingDate,
    amount: payment.amount,
    paymentMethod: payment.paymentMethod,
    status: 'PAID',
    paymentId: payment.paymentId,
    ticketCode,
    ticketQr: ticketQrPayload(payment, ticketCode),
    posterUrl: payment.posterUrl || '',
    issuedAt: Date.now(),
    vnpTransactionNo: payment.vnpTransactionNo || '',
  };
}

async function issueTicket(paymentRef, payment) {
  const candidateCode = payment.ticketCode || 'TTTT-' + crypto.randomBytes(5).toString('hex').toUpperCase();
  const ticketRef = db.ref('Ticker/' + payment.paymentId);
  const transaction = await ticketRef.transaction((current) => current || ticketFromPayment(payment, candidateCode), undefined, false);
  const ticket = transaction.snapshot.val();
  if (!ticket || ticket.paymentId !== payment.paymentId || ticket.ownerUid !== payment.ownerUid || ticket.status !== 'PAID') {
    fail('Không thể phát hành vé cho đơn này.', 500);
  }
  await paymentRef.transaction((current) => {
    if (!current || current.status !== 'PAID' || !hasValidOrderSignature(current)) return current;
    const updated = {
      ...current,
      ticketCode: ticket.ticketCode,
      ticketIssuedAt: current.ticketIssuedAt || Date.now(),
    };
    updated.statusSignature = stateSignature('STATUS', updated);
    return updated;
  }, undefined, false);
  return ticket;
}

function clientOrder(payment, ticket) {
  return {
    success: true,
    orderId: payment.paymentId,
    status: payment.status,
    amount: payment.amount,
    paymentUrl: payment.paymentUrl || '',
    ticketCode: ticket?.ticketCode || payment.ticketCode || '',
    ticketQr: ticket?.ticketQr || '',
    title: payment.title,
    cinema: payment.cinema,
    showtime: payment.showtime,
    bookingDate: payment.bookingDate,
    seats: payment.seats.join(', '),
    expiresAt: payment.expiresAt,
  };
}

async function createOrder(request, response, identity) {
  const body = request.body && typeof request.body === 'object' ? request.body : {};
  const paymentId = text(body.orderId, 40).toUpperCase();
  if (!ORDER_PATTERN.test(paymentId)) fail('Mã đơn hàng không hợp lệ.');
  const method = text(body.paymentMethod, 20).toUpperCase();
  if (method !== 'VNPAY') fail('Đơn đặt vé chỉ hỗ trợ thanh toán VNPAY.');
  const booking = normalizeBooking(body, identity);
  const fingerprint = bookingFingerprint(booking, method);
  const paymentRef = db.ref('PaymentOrders/' + paymentId);
  const existing = (await paymentRef.once('value')).val();
  if (existing) {
    if (!hasValidOrderSignature(existing) || existing.ownerUid !== identity.uid || existing.fingerprint !== fingerprint) {
      fail('Mã đơn đã được dùng cho giao dịch khác.', 409);
    }
    if (existing.status === 'PAID' && hasValidStatusSignature(existing)) {
      const ticket = await issueTicket(paymentRef, existing);
      response.json(clientOrder(existing, ticket));
      return;
    }
    if (['PENDING_VNPAY', 'PENDING_COUNTER'].includes(existing.status) && Number(existing.expiresAt) > Date.now()) {
      response.json(clientOrder(existing));
      return;
    }
    fail('Đơn này đã kết thúc hoặc hết hạn.', 409);
  }

  await ensureSeatsAvailable(booking);
  const now = Date.now();
  const ttl = PAYMENT_TTL_MS;
  const payment = {
    paymentId,
    ...booking,
    amount: amountForSeats(booking.seats),
    paymentMethod: method,
    status: 'PENDING_VNPAY',
    createdAt: now,
    expiresAt: now + ttl,
    lockExpiresAt: now + ttl,
    lockKey: scheduleLockKey(booking),
    fingerprint,
  };

  await reserveSeats(paymentId, booking, payment.expiresAt);
  try {
    payment.paymentUrl = buildPaymentUrl(vnpayParams(payment, request, now));
    payment.orderSignature = stateSignature('ORDER', payment);
    payment.statusSignature = stateSignature('STATUS', payment);
    const result = await paymentRef.transaction((current) => current || payment, undefined, false);
    const stored = result.snapshot.val();
    if (!stored || stored.ownerUid !== identity.uid || stored.fingerprint !== fingerprint || !hasValidOrderSignature(stored)) {
      await releaseSeats(payment);
      fail('Không thể tạo đơn đặt vé. Vui lòng thử lại.', 409);
    }
    response.json(clientOrder(stored));
  } catch (error) {
    await releaseSeats(payment).catch(() => {});
    throw error;
  }
}

async function handleVnpayCallback(params) {
  if (!params || !verifySecureHash(params)) return { RspCode: '97', Message: 'Chữ ký không hợp lệ' };
  if (params.vnp_TmnCode !== TMN_CODE.value()) return { RspCode: '97', Message: 'Mã website không hợp lệ' };
  const paymentId = text(params.vnp_TxnRef, 40).toUpperCase();
  if (!ORDER_PATTERN.test(paymentId)) return { RspCode: '01', Message: 'Không tìm thấy đơn thanh toán' };

  const paymentRef = db.ref('PaymentOrders/' + paymentId);
  const payment = (await paymentRef.once('value')).val();
  if (!payment || !hasValidOrderSignature(payment) || payment.paymentMethod !== 'VNPAY') {
    return { RspCode: '01', Message: 'Không tìm thấy đơn thanh toán' };
  }
  if (String(payment.amount * 100) !== String(params.vnp_Amount)) return { RspCode: '04', Message: 'Số tiền không hợp lệ' };

  const successful = params.vnp_ResponseCode === '00' && params.vnp_TransactionStatus === '00';
  if (!successful) {
    await paymentRef.transaction((current) => {
      if (!current || !hasValidOrderSignature(current) || current.status !== 'PENDING_VNPAY') return current;
      const updated = { ...current, status: 'FAILED', failedAt: Date.now() };
      updated.statusSignature = stateSignature('STATUS', updated);
      return updated;
    }, undefined, false);
    const latest = (await paymentRef.once('value')).val();
    if (latest?.status === 'FAILED') await releaseSeats(latest);
    return { RspCode: '00', Message: 'Đã ghi nhận kết quả thanh toán' };
  }

  const result = await paymentRef.transaction((current) => {
    if (!current || !hasValidOrderSignature(current) || current.paymentMethod !== 'VNPAY') return;
    if (current.status === 'PAID' && hasValidStatusSignature(current)) return current;
    if (current.status !== 'PENDING_VNPAY') return;
    const updated = {
      ...current,
      status: 'PAID',
      paidAt: Date.now(),
      vnpTransactionNo: text(params.vnp_TransactionNo, 30),
    };
    updated.statusSignature = stateSignature('STATUS', updated);
    return updated;
  }, undefined, false);
  const paidPayment = result.snapshot.val();
  if (!paidPayment || paidPayment.status !== 'PAID' || !hasValidOrderSignature(paidPayment)) {
    return { RspCode: '02', Message: 'Đơn thanh toán không còn hiệu lực' };
  }
  try {
    await issueTicket(paymentRef, paidPayment);
    await releaseSeats(paidPayment);
    return { RspCode: '00', Message: 'Xác nhận thành công' };
  } catch (error) {
    console.error('VNPAY ticket issue failed:', error.message);
    return { RspCode: '99', Message: 'Hệ thống đang hoàn tất phát hành vé' };
  }
}

async function readOrder(paymentId, identity) {
  const paymentRef = db.ref('PaymentOrders/' + paymentId);
  let payment = (await paymentRef.once('value')).val();
  if (!payment || !hasValidOrderSignature(payment)) return null;
  if (payment.ownerUid !== identity.uid && identity.staff !== true && identity.admin !== true) return null;

  if (['PENDING_VNPAY', 'PENDING_COUNTER'].includes(payment.status) && Number(payment.expiresAt) <= Date.now()) {
    await paymentRef.transaction((current) => {
      if (!current || !['PENDING_VNPAY', 'PENDING_COUNTER'].includes(current.status) || Number(current.expiresAt) > Date.now()) return current;
      const updated = { ...current, status: 'EXPIRED', expiredAt: Date.now() };
      updated.statusSignature = stateSignature('STATUS', updated);
      return updated;
    }, undefined, false);
    payment = (await paymentRef.once('value')).val();
    if (payment?.status === 'EXPIRED') await releaseSeats(payment);
  }
  if (['PAID', 'FAILED', 'EXPIRED'].includes(payment.status) && !hasValidStatusSignature(payment)) return null;
  const ticket = payment.status === 'PAID' ? await issueTicket(paymentRef, payment) : null;
  return clientOrder(payment, ticket);
}

async function listUnavailableSeats(request) {
  const booking = {
    title: required(request.query.title, 'phim', 160),
    bookingDate: validateBookingDate(request.query.bookingDate),
    cinema: required(request.query.cinema, 'rạp', 120),
    showtime: required(request.query.showtime, 'suất chiếu', 80),
  };
  const unavailable = await bookedSeats(booking);
  const lockRef = db.ref('SeatLocks/' + scheduleLockKey(booking));
  const now = Date.now();
  await lockRef.transaction((current) => {
    if (!current || typeof current !== 'object') return current;
    const locks = { ...current };
    for (const [seat, lock] of Object.entries(locks)) {
      if (!lock || Number(lock.expiresAt) <= now) delete locks[seat];
    }
    return Object.keys(locks).length ? locks : null;
  }, undefined, false);
  const locks = (await lockRef.once('value')).val() || {};
  for (const [seat, lock] of Object.entries(locks)) {
    if (lock && Number(lock.expiresAt) > Date.now()) unavailable.add(seat);
  }
  return [...unavailable].sort();
}

async function listTickets(identity) {
  const snapshot = await db.ref('Ticker').once('value');
  const tickets = [];
  snapshot.forEach((child) => {
    const ticket = child.val() || {};
    if (ticket.ownerUid !== identity.uid || ticket.status !== 'PAID') return;
    tickets.push({
      ticketCode: ticket.ticketCode,
      ticketQr: ticket.ticketQr,
      title: ticket.title,
      bookingDate: ticket.bookingDate,
      cinema: ticket.cinema,
      showtime: ticket.showtime,
      seats: ticket.seats,
      amount: ticket.amount,
      paymentMethod: ticket.paymentMethod,
      issuedAt: ticket.issuedAt,
    });
  });
  return tickets.sort((a, b) => Number(b.issuedAt) - Number(a.issuedAt)).slice(0, 100);
}

async function verifyTicket(ticketQr, identity) {
  requireStaff(identity);
  const parts = text(ticketQr, 500).split('|');
  if (parts.length !== 4 || parts[0] !== 'TTTT1' || !ORDER_PATTERN.test(parts[1])) fail('Mã QR vé không hợp lệ.', 400);
  const [, paymentId, ticketCode, receivedSignature] = parts;
  const paymentRef = db.ref('PaymentOrders/' + paymentId);
  const payment = (await paymentRef.once('value')).val();
  const ticketRef = db.ref('Ticker/' + paymentId);
  const ticket = (await ticketRef.once('value')).val();
  if (!payment || !ticket || !hasValidOrderSignature(payment) || !hasValidStatusSignature(payment) ||
      payment.status !== 'PAID' || ticket.status !== 'PAID' || ticket.paymentId !== paymentId ||
      ticket.ownerUid !== payment.ownerUid || ticket.ticketCode !== ticketCode) {
    fail('Không tìm thấy vé đã thanh toán hợp lệ.', 404);
  }
  const expected = ticketQrPayload(payment, ticketCode).split('|')[3];
  if (!safeEqual(receivedSignature, expected) || ticket.ticketQr !== ticketQrPayload(payment, ticketCode)) {
    fail('Chữ ký QR vé không hợp lệ.', 403);
  }
  const attemptId = crypto.randomBytes(12).toString('hex');
  const transaction = await ticketRef.transaction((current) => {
    if (!current || current.status !== 'PAID') return;
    if (current.checkedAt) return current;
    return { ...current, checkedAt: Date.now(), checkedByUid: identity.uid, checkedAttemptId: attemptId };
  }, undefined, false);
  const checkedTicket = transaction.snapshot.val();
  if (!checkedTicket) fail('Không thể cập nhật trạng thái soát vé.', 409);
  const newlyChecked = checkedTicket.checkedAttemptId === attemptId;
  return {
    valid: newlyChecked,
    alreadyChecked: !newlyChecked,
    message: newlyChecked ? 'Vé hợp lệ, đã ghi nhận lượt vào rạp.' : 'Vé đã được soát trước đó.',
    ticketCode,
    title: ticket.title,
    cinema: ticket.cinema,
    bookingDate: ticket.bookingDate,
    showtime: ticket.showtime,
    seats: ticket.seats,
  };
}

function safeEqual(left, right) {
  if (typeof left !== 'string' || typeof right !== 'string') return false;
  const leftBuffer = Buffer.from(left);
  const rightBuffer = Buffer.from(right);
  return leftBuffer.length === rightBuffer.length && crypto.timingSafeEqual(leftBuffer, rightBuffer);
}

async function bootstrapAdmin(identity, code) {
  if (!safeEqual(text(code, 256), ADMIN_BOOTSTRAP_CODE.value())) fail('Mã kích hoạt quản trị không hợp lệ.', 403);
  const ownerRef = db.ref('System/firstAdminUid');
  const result = await ownerRef.transaction((current) => current || identity.uid, undefined, false);
  if (!result.committed || result.snapshot.val() !== identity.uid) fail('Quyền quản trị ban đầu đã được thiết lập.', 409);
  const user = await getAuth().getUser(identity.uid);
  await getAuth().setCustomUserClaims(identity.uid, { ...user.customClaims, staff: true, admin: true });
  return { success: true, message: 'Đã cấp quyền quản trị cho tài khoản này.' };
}

async function grantStaff(email) {
  const cleanEmail = text(email, 160).toLowerCase();
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(cleanEmail)) fail('Email nhân viên không hợp lệ.');
  let user;
  try {
    user = await getAuth().getUserByEmail(cleanEmail);
  } catch (_) {
    fail('Hãy tạo tài khoản nhân viên trong ứng dụng trước.', 404);
  }
  await getAuth().setCustomUserClaims(user.uid, { ...user.customClaims, staff: true });
  return { success: true, email: cleanEmail };
}

function sendReturnPage(response, result) {
  const valid = result.RspCode === '00';
  const title = valid ? 'Đã nhận kết quả thanh toán' : 'Không xác nhận được giao dịch';
  const message = valid
    ? 'VNPAY đã trả kết quả. Hãy quay lại ứng dụng để xem trạng thái vé.'
    : 'Chưa thể xác nhận giao dịch. Hãy quay lại ứng dụng và kiểm tra trạng thái.';
  response.status(200).type('html').send(
    '<!doctype html><html lang="vi"><head><meta charset="utf-8">' +
    '<meta name="viewport" content="width=device-width,initial-scale=1">' +
    '<title>TTTT Cinema · VNPAY Sandbox</title></head>' +
    '<body style="font-family:Arial,sans-serif;background:#0b0d12;color:#fff;padding:32px;max-width:560px;margin:auto">' +
    '<h2>' + title + '</h2><p style="color:#b9bdc8">' + message + '</p>' +
    '<p>Bạn có thể đóng trang này an toàn.</p></body></html>'
  );
}

function routeError(response, error) {
  const statusCode = Number(error.statusCode) || 500;
  if (statusCode >= 500) console.error('TTTT API error:', error.message);
  response.status(statusCode).json({
    success: false,
    message: statusCode >= 500 ? 'Máy chủ chưa thể xử lý yêu cầu. Vui lòng thử lại.' : error.message,
  });
}

exports.vnpayApi = onRequest(
  { secrets: [HASH_SECRET, ADMIN_BOOTSTRAP_CODE], cors: true, timeoutSeconds: 30 },
  async (request, response) => {
    try {
      if (request.method === 'OPTIONS') {
        response.status(204).end();
        return;
      }
      const path = request.path.replace(/\/$/, '') || '/';

      if (request.method === 'GET' && path === '/api/seats') {
        response.json({ success: true, unavailableSeats: await listUnavailableSeats(request) });
        return;
      }
      if (request.method === 'GET' && path === '/api/payments/vnpay/ipn') {
        response.status(200).json(await handleVnpayCallback(request.query));
        return;
      }
      if (request.method === 'GET' && path === '/api/payments/vnpay/return') {
        sendReturnPage(response, await handleVnpayCallback(request.query));
        return;
      }

      const identity = await authenticate(request);
      if (request.method === 'GET' && path === '/api/me') {
        response.json({ success: true, staff: identity.staff === true, admin: identity.admin === true });
        return;
      }
      if (request.method === 'POST' && path === '/api/orders/create') {
        await createOrder(request, response, identity);
        return;
      }
      if (request.method === 'GET' && path === '/api/tickets') {
        response.json({ success: true, tickets: await listTickets(identity) });
        return;
      }
      if (request.method === 'POST' && path === '/api/bootstrap/admin') {
        response.json(await bootstrapAdmin(identity, text(request.body?.code, 256)));
        return;
      }
      if (request.method === 'POST' && path === '/api/admin/staff') {
        requireStaff(identity, true);
        response.json(await grantStaff(text(request.body?.email, 160)));
        return;
      }
      if (request.method === 'POST' && path === '/api/staff/tickets/verify') {
        response.json({ success: true, ...await verifyTicket(text(request.body?.ticketQr, 500), identity) });
        return;
      }
      const orderMatch = path.match(/^\/api\/orders\/status\/([A-Fa-f0-9]{32})$/);
      if (request.method === 'GET' && orderMatch) {
        const order = await readOrder(orderMatch[1].toUpperCase(), identity);
        if (!order) fail('Không tìm thấy đơn đặt vé.', 404);
        response.json(order);
        return;
      }
      fail('Không tìm thấy API.', 404);
    } catch (error) {
      routeError(response, error);
    }
  },
);
