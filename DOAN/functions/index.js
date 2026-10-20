const crypto = require('node:crypto');
const admin = require('firebase-admin');
const { onRequest } = require('firebase-functions/v2/https');
const { setGlobalOptions } = require('firebase-functions/v2');
const { defineSecret, defineString } = require('firebase-functions/params');

if (admin.apps.length === 0) admin.initializeApp();
setGlobalOptions({ region: 'asia-southeast1', maxInstances: 10 });

const db = admin.database();
const HASH_SECRET = defineSecret('VNPAY_HASH_SECRET');
const TMN_CODE = defineString('VNPAY_TMN_CODE', { default: 'GW923J6D' });
const API_BASE_URL = defineString('VNPAY_API_BASE_URL', {
  default: 'https://asia-southeast1-doan-ecdfa.cloudfunctions.net/vnpayApi',
});

const VNPAY_PAY_URL = 'https://sandbox.vnpayment.vn/paymentv2/vpcpay.html';
const PAYMENT_TTL_MS = 15 * 60 * 1000;
const SEAT_LOCK_TTL_MS = 20 * 60 * 1000;
const TICKET_PRICE_VND = 20000;
const BOOKING_FEE_VND = 2000;

function text(value, maxLength) {
  return typeof value === 'string' ? value.trim().slice(0, maxLength) : '';
}

function required(value, label, maxLength) {
  const result = text(value, maxLength);
  if (!result) {
    const error = new Error('Thiếu thông tin ' + label);
    error.statusCode = 400;
    throw error;
  }
  return result;
}

function normalizeBooking(body) {
  const inputSeats = Array.isArray(body.seats)
    ? body.seats
    : text(body.seats || body.ghe, 500).split(',');
  const seats = [...new Set(inputSeats.map((seat) => text(String(seat), 4).toUpperCase()).filter(Boolean))];
  if (seats.length === 0 || seats.length > 30 || seats.some((seat) => !/^[A-F][1-5]$/.test(seat))) {
    const error = new Error('Danh sách ghế không hợp lệ');
    error.statusCode = 400;
    throw error;
  }
  if (Array.isArray(inputSeats) && seats.length !== inputSeats.length) {
    const error = new Error('Danh sách ghế bị trùng');
    error.statusCode = 400;
    throw error;
  }

  const email = required(body.email, 'email', 160);
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    const error = new Error('Email không hợp lệ');
    error.statusCode = 400;
    throw error;
  }

  return {
    title: required(body.title || body.phim, 'phim', 160),
    cinema: required(body.cinema || body.rap, 'rạp', 120),
    showtime: required(body.showtime || body.lich, 'suất chiếu', 80),
    seats,
    province: text(body.province, 100),
    bookingDate: required(body.bookingDate || body.ngaydat, 'ngày đặt', 40),
    email,
    customerName: text(body.customerName || body.name || body.ten, 120) || email.split('@')[0] || 'Khách hàng',
    posterUrl: text(body.posterUrl, 1000),
  };
}

function bookingFingerprint(booking) {
  return JSON.stringify({
    ...booking,
    seats: [...booking.seats].sort(),
    email: booking.email.toLowerCase(),
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

function stateSignature(domain, payment) {
  const payload = domain === 'ORDER'
    ? [
      payment.paymentId, payment.title, payment.cinema, payment.showtime, payment.seats,
      payment.province, payment.bookingDate, payment.email, payment.customerName,
      payment.posterUrl, payment.amount, payment.createdAt, payment.expiresAt,
      payment.lockExpiresAt, payment.lockKey, payment.fingerprint, payment.paymentUrl,
    ]
    : [
      payment.paymentId, payment.status, payment.amount, payment.createdAt,
      payment.expiresAt, payment.paidAt || '', payment.failedAt || '',
      payment.expiredAt || '', payment.ticketIssuedAt || '', payment.vnpTransactionNo || '', payment.orderSignature,
    ];
  return crypto.createHmac('sha512', HASH_SECRET.value())
    .update('DOAN_VNPAY_' + domain + '|' + JSON.stringify(payload), 'utf8')
    .digest('hex');
}

function signatureMatches(received, expected) {
  if (typeof received !== 'string' || !/^[a-fA-F0-9]{128}$/.test(received)) return false;
  const expectedBytes = Buffer.from(expected, 'hex');
  const receivedBytes = Buffer.from(received, 'hex');
  return expectedBytes.length === receivedBytes.length && crypto.timingSafeEqual(expectedBytes, receivedBytes);
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

function amountForSeats(seats) {
  return seats.length * TICKET_PRICE_VND + BOOKING_FEE_VND;
}

function scheduleLockKey(booking) {
  return crypto.createHash('sha256')
    .update([booking.title, booking.cinema, booking.showtime].join('\u0000'))
    .digest('hex');
}

function splitSeats(value) {
  return typeof value === 'string'
    ? value.split(',').map((seat) => seat.trim().toUpperCase()).filter(Boolean)
    : [];
}

function isCancelledTicket(ticket) {
  const status = text(ticket.tt || ticket.status, 60).toLowerCase();
  return ['đã hủy', 'đã huỷ', 'hủy', 'huỷ', 'cancelled', 'failed', 'expired', 'thanh toán thất bại'].includes(status);
}

async function ensureSeatsAvailable(booking) {
  const snapshot = await db.ref('Ticker').once('value');
  const selectedSeats = new Set(booking.seats);
  let occupied = false;
  snapshot.forEach((ticketSnapshot) => {
    const ticket = ticketSnapshot.val() || {};
    if (isCancelledTicket(ticket)) return;
    if (ticket.phim !== booking.title || ticket.rap !== booking.cinema || ticket.lich !== booking.showtime) return;
    if (splitSeats(ticket.ghe).some((seat) => selectedSeats.has(seat))) occupied = true;
  });
  if (occupied) {
    const error = new Error('Một hoặc nhiều ghế vừa được đặt. Vui lòng chọn lại ghế.');
    error.statusCode = 409;
    throw error;
  }
}

async function reserveSeats(paymentId, booking, now) {
  const lockRef = db.ref('SeatLocks/' + scheduleLockKey(booking));
  const result = await lockRef.transaction((current) => {
    const locks = current && typeof current === 'object' ? { ...current } : {};
    for (const [seat, lock] of Object.entries(locks)) {
      if (!lock || Number(lock.expiresAt) <= now) delete locks[seat];
    }
    for (const seat of booking.seats) {
      if (locks[seat] && locks[seat].paymentId !== paymentId) return;
    }
    for (const seat of booking.seats) {
      locks[seat] = { paymentId, expiresAt: now + SEAT_LOCK_TTL_MS };
    }
    return locks;
  }, undefined, false);
  if (!result.committed) {
    const error = new Error('Ghế đang được giữ trong một giao dịch khác. Vui lòng thử lại.');
    error.statusCode = 409;
    throw error;
  }
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

function requestIp(request) {
  return text(request.ip, 45).replace(/^::ffff:/, '') || '127.0.0.1';
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
    vnp_ReturnUrl: API_BASE_URL.value().replace(/\/$/, '') + '/api/payments/vnpay/return',
    vnp_IpAddr: requestIp(request),
    vnp_CreateDate: formatVnpDate(now),
    vnp_ExpireDate: formatVnpDate(now + PAYMENT_TTL_MS),
  };
}

async function createPayment(request, response) {
  const body = request.body && typeof request.body === 'object' ? request.body : {};
  const paymentId = text(body.orderId, 40).toUpperCase();
  if (!/^[A-F0-9]{32}$/.test(paymentId)) {
    response.status(400).json({ success: false, message: 'Mã đơn hàng không hợp lệ' });
    return;
  }

  let booking;
  try {
    booking = normalizeBooking(body);
  } catch (error) {
    response.status(error.statusCode || 400).json({ success: false, message: error.message });
    return;
  }

  const paymentRef = db.ref('PaymentOrders/' + paymentId);
  const fingerprint = bookingFingerprint(booking);
  const existing = (await paymentRef.once('value')).val();
  if (existing) {
    if (!hasValidOrderSignature(existing) || existing.fingerprint !== fingerprint) {
      response.status(409).json({ success: false, message: 'Mã đơn đã được dùng cho giao dịch khác' });
      return;
    }
    if (existing.status === 'PENDING' && Number(existing.expiresAt) > Date.now()) {
      response.json({
        success: true,
        paymentId,
        paymentUrl: existing.paymentUrl,
        amount: existing.amount,
        expiresAt: existing.expiresAt,
      });
      return;
    }
    response.status(409).json({ success: false, message: 'Giao dịch này đã kết thúc hoặc hết hạn' });
    return;
  }

  try {
    await ensureSeatsAvailable(booking);
  } catch (error) {
    response.status(error.statusCode || 500).json({ success: false, message: error.message });
    return;
  }

  const now = Date.now();
  const payment = {
    paymentId,
    ...booking,
    amount: amountForSeats(booking.seats),
    status: 'PENDING',
    createdAt: now,
    expiresAt: now + PAYMENT_TTL_MS,
    lockExpiresAt: now + SEAT_LOCK_TTL_MS,
    lockKey: scheduleLockKey(booking),
    fingerprint,
  };

  try {
    await reserveSeats(paymentId, booking, now);
    payment.paymentUrl = buildPaymentUrl(vnpayParams(payment, request, now));
    payment.orderSignature = stateSignature('ORDER', payment);
    const result = await paymentRef.transaction((current) => current || payment, undefined, false);
    const stored = result.snapshot.val();
    if (!stored || stored.fingerprint !== fingerprint) {
      await releaseSeats(payment);
      response.status(409).json({ success: false, message: 'Không thể tạo giao dịch thanh toán' });
      return;
    }
    response.json({
      success: true,
      paymentId,
      paymentUrl: stored.paymentUrl,
      amount: stored.amount,
      expiresAt: stored.expiresAt,
    });
  } catch (error) {
    await releaseSeats(payment).catch(() => {});
    console.error('VNPAY create error:', error);
    response.status(error.statusCode || 500).json({
      success: false,
      message: error.statusCode ? error.message : 'Không tạo được giao dịch VNPAY',
    });
  }
}

function ticketFromPayment(payment, transactionNo) {
  return {
    ten: payment.customerName,
    email: payment.email,
    ngaydat: payment.bookingDate,
    phim: payment.title,
    lich: payment.showtime,
    ghe: payment.seats.join(', '),
    rap: payment.cinema,
    gia: String(payment.amount),
    tt: 'Đã xác nhận',
    pttt: 'VNPAY Sandbox',
    paymentId: payment.paymentId,
    ticketCode: 'CINE-' + payment.paymentId.slice(-8),
    province: payment.province || '',
    posterUrl: payment.posterUrl || '',
    vnpTransactionNo: transactionNo || '',
  };
}

async function handleVnpayCallback(params) {
  if (!params || !verifySecureHash(params)) return { RspCode: '97', Message: 'Chữ ký không hợp lệ' };
  if (params.vnp_TmnCode !== TMN_CODE.value()) return { RspCode: '97', Message: 'Mã website không hợp lệ' };

  const paymentId = text(params.vnp_TxnRef, 40).toUpperCase();
  if (!/^[A-F0-9]{32}$/.test(paymentId)) return { RspCode: '01', Message: 'Không tìm thấy đơn thanh toán' };

  const paymentRef = db.ref('PaymentOrders/' + paymentId);
  const payment = (await paymentRef.once('value')).val();
  if (!payment) return { RspCode: '01', Message: 'Không tìm thấy đơn thanh toán' };
  if (!hasValidOrderSignature(payment)) return { RspCode: '97', Message: 'Dữ liệu đơn hàng không hợp lệ' };
  if (String(payment.amount * 100) !== params.vnp_Amount) return { RspCode: '04', Message: 'Số tiền không hợp lệ' };

  const successful = params.vnp_ResponseCode === '00' && params.vnp_TransactionStatus === '00';
  if (successful) {
    const result = await paymentRef.transaction((current) => {
      if (!current || !hasValidOrderSignature(current)) return;
      if (current.status === 'PAID' && current.ticketIssuedAt && hasValidStatusSignature(current)) return current;
      if (!['PENDING', 'EXPIRED', 'FAILED', 'PAID'].includes(current.status)) return;
      const updated = {
        ...current,
        status: 'PAID',
        paidAt: Date.now(),
        failedAt: null,
        expiredAt: null,
        vnpTransactionNo: text(params.vnp_TransactionNo, 30),
      };
      updated.statusSignature = stateSignature('STATUS', updated);
      return updated;
    }, undefined, false);
    const paidPayment = result.snapshot.val();
    if (!paidPayment || paidPayment.status !== 'PAID') {
      return { RspCode: '02', Message: 'Giao dịch không còn hiệu lực' };
    }

    const ticket = ticketFromPayment(paidPayment, params.vnp_TransactionNo);
    await db.ref('Ticker/' + paymentId).set(ticket);
    await paymentRef.transaction((current) => {
      if (!current || current.status !== 'PAID' || !hasValidOrderSignature(current)) return current;
      const updated = { ...current, ticketIssuedAt: current.ticketIssuedAt || Date.now() };
      updated.statusSignature = stateSignature('STATUS', updated);
      return updated;
    }, undefined, false);
    await releaseSeats({ ...paidPayment, paymentId });
    return { RspCode: '00', Message: 'Xác nhận thành công' };
  }

  await paymentRef.transaction((current) => {
    if (!current || !hasValidOrderSignature(current)) return current;
    if (current.status === 'PAID' && hasValidStatusSignature(current)) return current;
    if (current.status !== 'PENDING' && current.status !== 'EXPIRED') return current;
    const updated = { ...current, status: 'FAILED', failedAt: Date.now() };
    updated.statusSignature = stateSignature('STATUS', updated);
    return updated;
  }, undefined, false);
  const latestPayment = (await paymentRef.once('value')).val();
  if (latestPayment && latestPayment.status === 'FAILED') {
    await releaseSeats({ ...latestPayment, paymentId });
  }
  return { RspCode: '00', Message: 'Đã ghi nhận kết quả thanh toán' };
}

async function readPaymentStatus(paymentId) {
  const paymentRef = db.ref('PaymentOrders/' + paymentId);
  let payment = (await paymentRef.once('value')).val();
  if (!payment || !hasValidOrderSignature(payment)) return null;

  if (payment.status === 'PENDING' && Number(payment.expiresAt) <= Date.now()) {
    await paymentRef.transaction((current) => {
      if (!current || current.status !== 'PENDING' || Number(current.expiresAt) > Date.now()) return current;
      const updated = { ...current, status: 'EXPIRED', expiredAt: Date.now() };
      updated.statusSignature = stateSignature('STATUS', updated);
      return updated;
    }, undefined, false);
    payment = (await paymentRef.once('value')).val();
  }

  const paidTicketMissing = payment.status === 'PAID' && !Number(payment.ticketIssuedAt);
  const trustedStatus = (['PAID', 'FAILED', 'EXPIRED'].includes(payment.status) && !hasValidStatusSignature(payment)) || paidTicketMissing
    ? 'PENDING'
    : payment.status;
  return {
    paymentId,
    status: trustedStatus,
    amount: payment.amount,
    expiresAt: payment.expiresAt,
    ticketCode: trustedStatus === 'PAID' ? 'CINE-' + paymentId.slice(-8) : '',
    title: payment.title,
    cinema: payment.cinema,
    showtime: payment.showtime,
    seats: Array.isArray(payment.seats) ? payment.seats.join(', ') : '',
    province: payment.province || '',
    bookingDate: payment.bookingDate,
    email: payment.email,
    customerName: payment.customerName,
    posterUrl: payment.posterUrl || '',
  };
}

function sendReturnPage(response, result, params) {
  const valid = result.RspCode === '00';
  const paymentId = /^[A-F0-9]{32}$/i.test(String(params.vnp_TxnRef || ''))
    ? String(params.vnp_TxnRef).toUpperCase()
    : '';
  const appLink = paymentId ? 'doan://vnpay-return?paymentId=' + paymentId : '';
  const title = valid ? 'Đã nhận kết quả thanh toán' : 'Không xác nhận được giao dịch';
  const message = valid
    ? 'Quay lại ứng dụng. Trạng thái vé sẽ được cập nhật sau khi hệ thống xác nhận giao dịch.'
    : 'Vui lòng liên hệ rạp nếu tài khoản ngân hàng đã bị trừ tiền.';
  const link = appLink ? '<p><a href="' + appLink + '">Quay lại ứng dụng</a></p>' : '';
  const redirect = appLink
    ? '<script>window.setTimeout(function(){window.location.href=' + JSON.stringify(appLink) + ';},700);</script>'
    : '';
  response.status(200).type('html').send(
    '<!doctype html><html lang="vi"><head><meta charset="utf-8">' +
    '<meta name="viewport" content="width=device-width,initial-scale=1">' +
    '<title>VNPAY Sandbox</title></head><body style="font-family:Arial,sans-serif;padding:32px">' +
    '<h2>' + title + '</h2><p>' + message + '</p>' + link + redirect + '</body></html>'
  );
}

exports.vnpayApi = onRequest(
  { secrets: [HASH_SECRET], cors: true, timeoutSeconds: 30 },
  async (request, response) => {
    try {
      if (request.method === 'OPTIONS') {
        response.status(204).end();
        return;
      }

      const path = request.path.replace(/\/$/, '') || '/';
      if (request.method === 'POST' && path === '/api/payments/vnpay/create') {
        await createPayment(request, response);
        return;
      }

      const statusMatch = path.match(/^\/api\/payments\/vnpay\/status\/([A-Fa-f0-9]{32})$/);
      if (request.method === 'GET' && statusMatch) {
        const payment = await readPaymentStatus(statusMatch[1].toUpperCase());
        if (!payment) {
          response.status(404).json({ success: false, message: 'Không tìm thấy giao dịch' });
          return;
        }
        response.json({ success: true, ...payment });
        return;
      }

      if (request.method === 'GET' && path === '/api/payments/vnpay/ipn') {
        response.status(200).json(await handleVnpayCallback(request.query));
        return;
      }

      if (request.method === 'GET' && path === '/api/payments/vnpay/return') {
        sendReturnPage(response, await handleVnpayCallback(request.query), request.query);
        return;
      }

      response.status(404).json({ success: false, message: 'Không tìm thấy API' });
    } catch (error) {
      console.error('VNPAY API error:', error);
      response.status(500).json({ success: false, message: 'Lỗi xử lý giao dịch' });
    }
  }
);
