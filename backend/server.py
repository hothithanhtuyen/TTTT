"""
Main Cinema Backend Application
Built with FastAPI, SQLite, and VNPay v2.1.0 Protocol.
Provides RESTful APIs, Web UI (Admin Dashboard, Customer Portal, Ticket Scanner),
real-time seat holding, and idempotent VNPay payment verification.
"""

import os
import secrets
import json
import requests
from datetime import datetime
from typing import List, Optional, Dict, Any

from fastapi import FastAPI, Request, Query, HTTPException, Depends
from fastapi.responses import HTMLResponse, JSONResponse, RedirectResponse
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, EmailStr
from dotenv import load_dotenv

import database
from vnpay import VNPayService

# Load environment configuration
load_dotenv(os.path.join(os.path.dirname(__file__), ".env"))

VNPAY_TMN_CODE = os.getenv("VNPAY_TMN_CODE", "GW923J6D")
VNPAY_HASH_SECRET = os.getenv("VNPAY_HASH_SECRET", "SANDBOX_TEST_SECRET_KEY_GW923J6D")
VNPAY_URL = os.getenv("VNPAY_URL", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html")
VNPAY_RETURN_URL = os.getenv("VNPAY_RETURN_URL", "http://localhost:8000/api/payment/vnpay/return")
VNPAY_IPN_URL = os.getenv("VNPAY_IPN_URL", "http://localhost:8000/api/payment/vnpay/ipn")
APP_BASE_URL = os.getenv("APP_BASE_URL", "http://localhost:8000")
FIREBASE_URL = "https://doan-ecdfa-default-rtdb.firebaseio.com"

# Initialize services
vnpay_service = VNPayService(
    tmn_code=VNPAY_TMN_CODE,
    hash_secret=VNPAY_HASH_SECRET,
    payment_url=VNPAY_URL
)

app = FastAPI(
    title="Cinema Movie Booking & VNPay Gateway API",
    version="2.0.0",
    description="Modern cinema booking backend with seat-holding locks, VNPay Sandbox, and e-ticket check-in system."
)

# Enable CORS for all clients (Android app, Web, Tools)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.on_event("startup")
def startup_event():
    database.init_db()


# -------------------------------------------------------------
# PYDANTIC REQUEST / RESPONSE SCHEMAS
# -------------------------------------------------------------

class HoldSeatRequest(BaseModel):
    showtime_id: int
    seat_ids: List[int]
    customer_name: str
    customer_email: str
    customer_phone: Optional[str] = "0900000000"
    user_id: Optional[int] = None
    promo_code: Optional[str] = None


class CreatePaymentRequest(BaseModel):
    order_code: str
    bank_code: Optional[str] = None
    return_url: Optional[str] = None


class TicketScanRequest(BaseModel):
    code_or_token: str
    scanned_by: Optional[str] = "GATE_STAFF"


class SimulateCallbackRequest(BaseModel):
    order_code: str
    response_code: Optional[str] = "00"
    bank_code: Optional[str] = "NCB"


# Helper to sync confirmed tickets to Firebase RTDB
def sync_to_firebase(order_code: str, movie_title: str, cinema: str, showtime: str, seats: str, email: str, name: str, price: int):
    try:
        url = f"{FIREBASE_URL}/Ticker.json"
        data = {
            "cinema": cinema,
            "email": email,
            "ghe": seats,
            "gia": f"{price} VNĐ",
            "lich": showtime,
            "ngaydat": datetime.now().strftime("%d/%m/%Y"),
            "phim": movie_title,
            "pttt": "VNPay Sandbox",
            "ten": name or email.split("@")[0],
            "tt": "Đã xác nhận",
            "orderCode": order_code
        }
        requests.post(url, json=data, timeout=3.0)
    except Exception as e:
        print(f"[Firebase Sync Warning] {e}")


# -------------------------------------------------------------
# PUBLIC / MOVIE & SHOWTIME APIS
# -------------------------------------------------------------

@app.get("/api/movies")
def get_movies(status: Optional[str] = None):
    database.release_expired_holds()
    conn = database.get_db_connection()
    if status:
        rows = conn.execute("SELECT * FROM movies WHERE status = ? ORDER BY id ASC", (status,)).fetchall()
    else:
        rows = conn.execute("SELECT * FROM movies ORDER BY id ASC").fetchall()
    conn.close()
    return [dict(r) for r in rows]


@app.get("/api/movies/{movie_id}")
def get_movie_detail(movie_id: int):
    conn = database.get_db_connection()
    row = conn.execute("""
        SELECT m.*, g.name as genre_name
        FROM movies m
        LEFT JOIN genres g ON m.genre_id = g.id
        WHERE m.id = ?
    """, (movie_id,)).fetchone()
    conn.close()
    if not row:
        raise HTTPException(status_code=404, detail="Không tìm thấy phim")
    return dict(row)


@app.get("/api/cinemas")
def get_cinemas():
    conn = database.get_db_connection()
    rows = conn.execute("SELECT * FROM cinemas ORDER BY id ASC").fetchall()
    conn.close()
    return [dict(r) for r in rows]


@app.get("/api/cinemas/{cinema_id}/showtimes")
def get_cinema_showtimes(cinema_id: int, date: Optional[str] = None):
    conn = database.get_db_connection()
    query = """
        SELECT s.*, m.title as movie_title, m.poster_url, m.age_rating,
               r.name as room_name, r.room_type, c.name as cinema_name
        FROM showtimes s
        JOIN movies m ON s.movie_id = m.id
        JOIN cinema_rooms r ON s.room_id = r.id
        JOIN cinemas c ON r.cinema_id = c.id
        WHERE c.id = ?
    """
    params = [cinema_id]
    if date:
        query += " AND s.show_date = ?"
        params.append(date)
    query += " ORDER BY s.show_date ASC, s.start_time ASC"

    rows = conn.execute(query, params).fetchall()
    conn.close()
    return [dict(r) for r in rows]


@app.get("/api/showtimes/{showtime_id}/seats")
def get_showtime_seats(showtime_id: int):
    """
    Get all seats for a given showtime along with real-time status:
    AVAILABLE, HELD, or SOLD.
    """
    database.release_expired_holds()
    conn = database.get_db_connection()

    # Get room and showtime info
    st = conn.execute("""
        SELECT s.*, r.name as room_name, c.name as cinema_name, m.title as movie_title
        FROM showtimes s
        JOIN cinema_rooms r ON s.room_id = r.id
        JOIN cinemas c ON r.cinema_id = c.id
        JOIN movies m ON s.movie_id = m.id
        WHERE s.id = ?
    """, (showtime_id,)).fetchone()

    if not st:
        conn.close()
        raise HTTPException(status_code=404, detail="Không tìm thấy suất chiếu")

    # Get seats
    now_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    seats = conn.execute("""
        SELECT s.id, s.seat_row, s.seat_number, s.seat_code, s.seat_type, s.base_price,
               CASE 
                   WHEN ss.status = 'SOLD' THEN 'SOLD'
                   WHEN ss.status = 'HELD' AND ss.hold_expires_at > ? THEN 'HELD'
                   ELSE 'AVAILABLE'
               END as current_status
        FROM seats s
        LEFT JOIN showtime_seats ss ON s.id = ss.seat_id AND ss.showtime_id = ?
        WHERE s.room_id = ?
        ORDER BY s.seat_row ASC, s.seat_number ASC
    """, (now_str, showtime_id, st["room_id"])).fetchall()

    conn.close()
    return {
        "showtime": dict(st),
        "seats": [dict(s) for s in seats]
    }


# -------------------------------------------------------------
# SEAT RESERVATION & ORDER APIS
# -------------------------------------------------------------

@app.post("/api/orders/hold-seats")
def hold_seats(req: HoldSeatRequest):
    """
    Hold seats with concurrency control and lock expiration.
    Returns calculated prices and temporary order.
    """
    try:
        order = database.hold_seats_transaction(
            showtime_id=req.showtime_id,
            seat_ids=req.seat_ids,
            customer_name=req.customer_name,
            customer_email=req.customer_email,
            customer_phone=req.customer_phone or "",
            user_id=req.user_id,
            hold_minutes=10,
            promo_code=req.promo_code
        )
        return {"success": True, "data": order}
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Lỗi đặt ghế: {str(e)}")


@app.get("/api/orders/{order_code}")
def get_order_detail(order_code: str):
    database.release_expired_holds()
    conn = database.get_db_connection()
    order = conn.execute("""
        SELECT o.*, s.show_date, s.start_time, m.title as movie_title, m.poster_url,
               r.name as room_name, c.name as cinema_name, c.address as cinema_address
        FROM orders o
        JOIN showtimes s ON o.showtime_id = s.id
        JOIN movies m ON s.movie_id = m.id
        JOIN cinema_rooms r ON s.room_id = r.id
        JOIN cinemas c ON r.cinema_id = c.id
        WHERE o.order_code = ?
    """, (order_code,)).fetchone()

    if not order:
        conn.close()
        raise HTTPException(status_code=404, detail="Không tìm thấy đơn hàng")

    items = conn.execute("SELECT * FROM order_items WHERE order_id = ?", (order["id"],)).fetchall()
    conn.close()

    return {
        "order": dict(order),
        "items": [dict(i) for i in items]
    }


@app.get("/api/orders/{order_code}/status")
def get_order_status(order_code: str):
    """Real-time polling endpoint for clients waiting for payment confirmation."""
    database.release_expired_holds()
    conn = database.get_db_connection()
    row = conn.execute("SELECT status, hold_expires_at, final_amount FROM orders WHERE order_code = ?", (order_code,)).fetchone()
    conn.close()

    if not row:
        raise HTTPException(status_code=404, detail="Đơn hàng không tồn tại")

    return {
        "order_code": order_code,
        "status": row["status"],
        "final_amount": row["final_amount"],
        "hold_expires_at": row["hold_expires_at"]
    }


# -------------------------------------------------------------
# VNPAY SANDBOX INTEGRATION APIS
# -------------------------------------------------------------

@app.post("/api/payment/vnpay/create")
def create_vnpay_payment(req: CreatePaymentRequest, request: Request):
    """
    Generate official VNPay 2.1.0 payment URL and payload.
    Also returns QR payment payload for scanning.
    """
    database.release_expired_holds()
    conn = database.get_db_connection()
    order = conn.execute("SELECT * FROM orders WHERE order_code = ?", (req.order_code,)).fetchone()
    conn.close()

    if not order:
        raise HTTPException(status_code=404, detail="Không tìm thấy đơn hàng")

    if order["status"] == "PAID":
        raise HTTPException(status_code=400, detail="Đơn hàng này đã được thanh toán!")

    if order["status"] == "EXPIRED":
        raise HTTPException(status_code=400, detail="Thời gian giữ ghế cho đơn hàng này đã hết hạn!")

    client_ip = request.client.host if request.client else "127.0.0.1"
    return_url = req.return_url or VNPAY_RETURN_URL
    order_info = f"Thanh toan ve xem phim {order['order_code']}"

    payment_url, secure_hash, params = vnpay_service.generate_payment_url(
        order_code=order["order_code"],
        amount=order["final_amount"],
        order_info=order_info,
        return_url=return_url,
        ip_addr=client_ip,
        bank_code=req.bank_code
    )

    # In case user scans QR with mobile to open payment URL:
    qr_url = payment_url

    return {
        "success": True,
        "order_code": order["order_code"],
        "amount": order["final_amount"],
        "payment_url": payment_url,
        "qr_url": qr_url,
        "qr_notice": "Mã QR mở trang thanh toán VNPay Sandbox. Quét để mở cổng thanh toán an toàn.",
        "params": params
    }


@app.get("/api/payment/vnpay/return")
def vnpay_return(request: Request):
    """
    VNPay Return URL Endpoint:
    1. Reads query parameters sent by VNPay.
    2. Verifies cryptographic HMAC-SHA512 checksum.
    3. If valid and vnp_ResponseCode == '00', confirms order and issues tickets.
    4. Renders responsive cinema confirmation page.
    """
    query_params = dict(request.query_params)
    is_valid, msg = vnpay_service.verify_response(query_params)

    order_code = query_params.get("vnp_TxnRef", "")
    response_code = query_params.get("vnp_ResponseCode", "")
    vnp_amount = int(query_params.get("vnp_Amount", 0)) // 100
    transaction_no = query_params.get("vnp_TransactionNo", "")
    bank_code = query_params.get("vnp_BankCode", "")

    if not is_valid:
        return HTMLResponse(content=f"""
        <!DOCTYPE html>
        <html>
        <head><meta charset="utf-8"><title>Xác thực thất bại - VNPay</title>
        <style>body {{ background:#12141C; color:#fff; font-family:sans-serif; text-align:center; padding:50px; }}
        .box {{ background:#1A1D28; max-width:500px; margin:auto; padding:30px; border-radius:12px; border:1px solid #E50914; }}</style>
        </head><body>
        <div class="box">
            <h2 style="color:#E50914;">❌ Chữ Ký Không Hợp Lệ!</h2>
            <p>Hệ thống phát hiện sai lệch chữ ký bảo mật từ VNPay ({msg}).</p>
            <a href="/" style="color:#E5A93C;">Quay lại trang chủ</a>
        </div></body></html>
        """, status_code=400)

    if response_code == "00":
        try:
            result = database.confirm_order_payment(
                order_code=order_code,
                payment_method="VNPAY",
                transaction_ref=transaction_no or order_code,
                amount=vnp_amount,
                vnp_transaction_no=transaction_no,
                bank_code=bank_code,
                raw_response=json.dumps(query_params)
            )

            # Sync to Firebase for real-time mobile app notification
            sync_to_firebase(
                order_code=order_code,
                movie_title=result.get("movie_title", "Phim"),
                cinema=result.get("cinema_name", "Rạp"),
                showtime=f"{result.get('start_time', '')} {result.get('show_date', '')}",
                seats=", ".join(t["seat_code"] for t in result.get("tickets", [])),
                email="khachhang@cinema.com",
                name=result.get("customer_name", "Khách hàng"),
                price=vnp_amount
            )

            # Render success UI
            ticket_items_html = "".join([
                f"""<div style="background:#232738; padding:12px; margin:10px 0; border-radius:8px; display:flex; justify-content:space-between; align-items:center;">
                    <div><b>Ghế: {t['seat_code']}</b> | Mã vé: <span style="color:#E5A93C;">{t['ticket_code']}</span></div>
                    <a href="/ticket/{t['ticket_code']}" style="background:#E50914; color:#fff; padding:6px 14px; text-decoration:none; border-radius:6px; font-size:13px;">Xem Vé 🎟</a>
                   </div>""" for t in result.get("tickets", [])
            ])

            return HTMLResponse(content=f"""
            <!DOCTYPE html>
            <html lang="vi">
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Thanh Toán Thành Công - VNPay Sandbox</title>
                <style>
                    body {{ background: #12141C; color: #FFFFFF; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; display: flex; justify-content: center; align-items: center; min-height: 100vh; margin: 0; padding: 20px; box-sizing: border-box; }}
                    .card {{ background: #1A1D28; border-radius: 16px; padding: 32px; max-width: 520px; width: 100%; box-shadow: 0 10px 30px rgba(0,0,0,0.5); border: 1px solid #2B3042; text-align: center; }}
                    .icon {{ width: 72px; height: 72px; background: rgba(76, 175, 80, 0.15); border-radius: 50%; display: flex; align-items: center; justify-content: center; margin: 0 auto 16px; font-size: 36px; color: #4CAF50; }}
                    h1 {{ font-size: 24px; margin: 0 0 8px; color: #4CAF50; }}
                    .price {{ font-size: 28px; font-weight: 800; color: #E5A93C; margin: 12px 0 20px; }}
                    .info-row {{ display: flex; justify-content: space-between; padding: 10px 0; border-bottom: 1px solid #2B3042; font-size: 14px; color: #B0B5C6; }}
                    .info-row span:last-child {{ color: #FFFFFF; font-weight: 600; }}
                    .btn {{ display: inline-block; width: 100%; box-sizing: border-box; background: #E50914; color: #fff; text-decoration: none; padding: 14px; border-radius: 8px; font-weight: bold; margin-top: 20px; font-size: 16px; transition: background 0.2s; }}
                    .btn:hover {{ background: #f6121d; }}
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="icon">✓</div>
                    <h1>THANH TOÁN THÀNH CÔNG!</h1>
                    <p style="color: #8C92A4; margin: 0 0 16px;">Giao dịch qua VNPay Sandbox đã được xác minh thành công.</p>
                    <div class="price">{vnp_amount:,.0f} VNĐ</div>
                    <div class="info-row"><span>Mã đơn hàng:</span><span>{order_code}</span></div>
                    <div class="info-row"><span>Mã giao dịch VNPay:</span><span>{transaction_no}</span></div>
                    <div class="info-row"><span>Ngân hàng:</span><span>{bank_code}</span></div>
                    <div class="info-row"><span>Phim:</span><span>{result.get('movie_title', 'Phim')}</span></div>
                    <div class="info-row"><span>Rạp:</span><span>{result.get('cinema_name', 'Rạp')}</span></div>
                    <div class="info-row"><span>Suất chiếu:</span><span>{result.get('start_time', '')} - {result.get('show_date', '')}</span></div>
                    
                    <h3 style="margin: 20px 0 10px; text-align: left; font-size: 16px; color: #E5A93C;">🎟 Danh Sách Vé Điện Tử</h3>
                    {ticket_items_html}
                    
                    <a href="/" class="btn">Quay Về Trang Chủ</a>
                </div>
            </body>
            </html>
            """)

        except Exception as e:
            return HTMLResponse(content=f"<h3>Lỗi cập nhật đơn hàng: {e}</h3>", status_code=500)

    else:
        return HTMLResponse(content=f"""
        <!DOCTYPE html>
        <html>
        <head><meta charset="utf-8"><title>Giao Dịch Thất Bại</title>
        <style>body {{ background:#12141C; color:#fff; font-family:sans-serif; text-align:center; padding:50px; }}
        .box {{ background:#1A1D28; max-width:500px; margin:auto; padding:30px; border-radius:12px; border:1px solid #E50914; }}</style>
        </head><body>
        <div class="box">
            <h2 style="color:#E50914;">❌ Thanh Toán Không Thành Công</h2>
            <p>Mã lỗi phản hồi từ cổng thanh toán: <b>{response_code}</b></p>
            <p>Đơn hàng: {order_code}</p>
            <a href="/" style="display:inline-block; margin-top:20px; background:#E50914; color:#fff; padding:12px 24px; text-decoration:none; border-radius:8px;">Thử lại</a>
        </div></body></html>
        """, status_code=400)


@app.api_route("/api/payment/vnpay/ipn", methods=["GET", "POST"])
def vnpay_ipn(request: Request):
    """
    VNPay IPN (Instant Payment Notification) Webhook:
    - Called in the background by VNPay servers.
    - Validates HMAC-SHA512 checksum.
    - Validates order existence, amount, and idempotency.
    - Returns official VNPay JSON response codes.
    """
    query_params = dict(request.query_params)
    is_valid, msg = vnpay_service.verify_response(query_params)

    if not is_valid:
        return JSONResponse(content={"RspCode": "97", "Message": "Invalid Checksum"})

    order_code = query_params.get("vnp_TxnRef", "")
    response_code = query_params.get("vnp_ResponseCode", "")
    vnp_amount = int(query_params.get("vnp_Amount", 0)) // 100
    transaction_no = query_params.get("vnp_TransactionNo", "")
    bank_code = query_params.get("vnp_BankCode", "")

    conn = database.get_db_connection()
    order = conn.execute("SELECT * FROM orders WHERE order_code = ?", (order_code,)).fetchone()
    conn.close()

    if not order:
        return JSONResponse(content={"RspCode": "01", "Message": "Order not found"})

    if order["final_amount"] != vnp_amount:
        return JSONResponse(content={"RspCode": "04", "Message": "Invalid amount"})

    # Idempotency check: if already confirmed, acknowledge success safely
    if order["status"] == "PAID":
        return JSONResponse(content={"RspCode": "02", "Message": "Order already confirmed"})

    if response_code == "00":
        try:
            result = database.confirm_order_payment(
                order_code=order_code,
                payment_method="VNPAY",
                transaction_ref=transaction_no or order_code,
                amount=vnp_amount,
                vnp_transaction_no=transaction_no,
                bank_code=bank_code,
                raw_response=json.dumps(query_params)
            )

            sync_to_firebase(
                order_code=order_code,
                movie_title=result.get("movie_title", "Phim"),
                cinema=result.get("cinema_name", "Rạp"),
                showtime=f"{result.get('start_time', '')} {result.get('show_date', '')}",
                seats=", ".join(t["seat_code"] for t in result.get("tickets", [])),
                email="khachhang@cinema.com",
                name=result.get("customer_name", "Khách hàng"),
                price=vnp_amount
            )

            return JSONResponse(content={"RspCode": "00", "Message": "Confirm Success"})

        except Exception as e:
            return JSONResponse(content={"RspCode": "99", "Message": f"Internal Error: {str(e)}"})

    else:
        # Mark payment failed
        conn = database.get_db_connection()
        conn.execute("UPDATE orders SET status = 'CANCELLED' WHERE id = ?", (order["id"],))
        conn.execute("""
            UPDATE showtime_seats 
            SET status = 'AVAILABLE', held_by_order_id = NULL, hold_expires_at = NULL 
            WHERE held_by_order_id = ?
        """, (order["id"],))
        conn.commit()
        conn.close()
        return JSONResponse(content={"RspCode": "00", "Message": "Confirm Success (Payment marked failed)"})


# -------------------------------------------------------------
# E-TICKET SCANNING & VERIFICATION APIS
# -------------------------------------------------------------

@app.get("/api/tickets/{ticket_code}")
def get_ticket_detail(ticket_code: str):
    conn = database.get_db_connection()
    ticket = conn.execute("""
        SELECT t.*, o.order_code, o.status as order_status,
               s.show_date, s.start_time, s.format_type,
               m.title as movie_title, m.poster_url, m.age_rating,
               r.name as room_name, c.name as cinema_name, c.address as cinema_address
        FROM tickets t
        JOIN orders o ON t.order_id = o.id
        JOIN showtimes s ON t.showtime_id = s.id
        JOIN movies m ON s.movie_id = m.id
        JOIN cinema_rooms r ON s.room_id = r.id
        JOIN cinemas c ON r.cinema_id = c.id
        WHERE t.ticket_code = ?
    """, (ticket_code,)).fetchone()
    conn.close()

    if not ticket:
        raise HTTPException(status_code=404, detail="Không tìm thấy vé")

    return dict(ticket)


@app.post("/api/tickets/scan")
def scan_ticket(req: TicketScanRequest):
    """
    Validate and redeem e-ticket at cinema entrance:
    Prevents reuse by tracking ticket status and logging duplicates.
    """
    result = database.verify_ticket_scan(req.code_or_token, scanned_by=req.scanned_by or "GATE_STAFF")
    return result


@app.post("/api/admin/simulate-vnpay-callback")
def simulate_vnpay_callback(req: SimulateCallbackRequest):
    """
    Developer / Automated Testing Endpoint:
    Simulates a cryptographically authentic VNPay callback for testing the full workflow.
    """
    conn = database.get_db_connection()
    order = conn.execute("SELECT * FROM orders WHERE order_code = ?", (req.order_code,)).fetchone()
    conn.close()

    if not order:
        raise HTTPException(status_code=404, detail="Không tìm thấy đơn hàng để test")

    test_params = {
        "vnp_Amount": str(order["final_amount"] * 100),
        "vnp_BankCode": req.bank_code or "NCB",
        "vnp_BankTranNo": "VNPAYTEST123456",
        "vnp_CardType": "ATM",
        "vnp_OrderInfo": f"Thanh toan ve {order['order_code']}",
        "vnp_PayDate": datetime.now().strftime("%Y%m%d%H%M%S"),
        "vnp_ResponseCode": req.response_code or "00",
        "vnp_TmnCode": VNPAY_TMN_CODE,
        "vnp_TransactionNo": f"TRANS{secrets.randbelow(1000000):06d}",
        "vnp_TransactionStatus": req.response_code or "00",
        "vnp_TxnRef": order["order_code"]
    }

    # Generate genuine hash
    hash_data = vnpay_service.build_hash_data(test_params)
    secure_hash = vnpay_service.create_secure_hash(hash_data)
    test_params["vnp_SecureHash"] = secure_hash

    # Trigger IPN verification logic
    is_valid, msg = vnpay_service.verify_response(test_params)
    if not is_valid:
        raise HTTPException(status_code=500, detail="Lỗi tạo chữ ký mô phỏng")

    result = database.confirm_order_payment(
        order_code=order["order_code"],
        payment_method="VNPAY (Sandbox)",
        transaction_ref=test_params["vnp_TransactionNo"],
        amount=order["final_amount"],
        vnp_transaction_no=test_params["vnp_TransactionNo"],
        bank_code=req.bank_code or "NCB",
        raw_response=json.dumps(test_params)
    )

    sync_to_firebase(
        order_code=order["order_code"],
        movie_title=result.get("movie_title", "Phim"),
        cinema=result.get("cinema_name", "Rạp"),
        showtime=f"{result.get('start_time', '')} {result.get('show_date', '')}",
        seats=", ".join(t["seat_code"] for t in result.get("tickets", [])),
        email="khachhang@cinema.com",
        name=result.get("customer_name", "Khách hàng"),
        price=order["final_amount"]
    )

    return {
        "success": True,
        "message": "Mô phỏng thanh toán VNPay thành công!",
        "result": result
    }


# -------------------------------------------------------------
# ADMIN DASHBOARD & SCANNER PAGES
# -------------------------------------------------------------

@app.get("/api/admin/stats")
def get_admin_stats():
    conn = database.get_db_connection()
    total_revenue = conn.execute("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE status = 'SUCCESS'").fetchone()[0]
    total_tickets = conn.execute("SELECT COUNT(*) FROM tickets WHERE status IN ('VALID', 'USED')").fetchone()[0]
    total_scanned = conn.execute("SELECT COUNT(*) FROM tickets WHERE status = 'USED'").fetchone()[0]
    total_orders = conn.execute("SELECT COUNT(*) FROM orders").fetchone()[0]

    recent_orders = conn.execute("""
        SELECT o.*, m.title as movie_title, c.name as cinema_name
        FROM orders o
        JOIN showtimes s ON o.showtime_id = s.id
        JOIN movies m ON s.movie_id = m.id
        JOIN cinema_rooms r ON s.room_id = r.id
        JOIN cinemas c ON r.cinema_id = c.id
        ORDER BY o.id DESC LIMIT 10
    """).fetchall()

    recent_scans = conn.execute("""
        SELECT ts.*, t.ticket_code, t.seat_code, m.title as movie_title
        FROM ticket_scans ts
        JOIN tickets t ON ts.ticket_id = t.id
        JOIN showtimes s ON t.showtime_id = s.id
        JOIN movies m ON s.movie_id = m.id
        ORDER BY ts.id DESC LIMIT 10
    """).fetchall()

    conn.close()

    return {
        "total_revenue": total_revenue,
        "total_tickets": total_tickets,
        "total_scanned": total_scanned,
        "total_orders": total_orders,
        "recent_orders": [dict(r) for r in recent_orders],
        "recent_scans": [dict(s) for s in recent_scans]
    }


@app.get("/admin", response_class=HTMLResponse)
def admin_page():
    return HTMLResponse(content="""
    <!DOCTYPE html>
    <html lang="vi">
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Hệ Thống Quản Trị Rạp Chiếu Phim - Cinema Admin</title>
        <style>
            :root {
                --bg: #12141C;
                --card: #1A1D28;
                --border: #2B3042;
                --primary: #E50914;
                --accent: #E5A93C;
                --text: #FFFFFF;
                --muted: #8C92A4;
            }
            body { background: var(--bg); color: var(--text); font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; margin: 0; padding: 20px; }
            .container { max-width: 1200px; margin: 0 auto; }
            header { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--border); padding-bottom: 16px; margin-bottom: 24px; }
            h1 { margin: 0; font-size: 22px; color: var(--accent); }
            .badge-live { background: rgba(76, 175, 80, 0.2); color: #4CAF50; padding: 4px 10px; border-radius: 20px; font-size: 13px; font-weight: bold; }
            .nav-links a { color: var(--muted); text-decoration: none; margin-left: 20px; font-size: 14px; }
            .nav-links a.active { color: var(--text); font-weight: bold; border-bottom: 2px solid var(--primary); padding-bottom: 4px; }
            
            .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px; margin-bottom: 24px; }
            .stat-card { background: var(--card); border: 1px solid var(--border); border-radius: 12px; padding: 20px; }
            .stat-label { font-size: 13px; color: var(--muted); text-transform: uppercase; margin-bottom: 8px; }
            .stat-val { font-size: 28px; font-weight: 800; color: var(--text); }
            .stat-val.gold { color: var(--accent); }
            .stat-val.green { color: #4CAF50; }
            
            .section { background: var(--card); border: 1px solid var(--border); border-radius: 12px; padding: 20px; margin-bottom: 24px; }
            .section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
            h2 { font-size: 18px; margin: 0; }
            
            table { width: 100%; border-collapse: collapse; font-size: 14px; }
            th { text-align: left; color: var(--muted); padding: 12px; border-bottom: 1px solid var(--border); font-size: 12px; text-transform: uppercase; }
            td { padding: 12px; border-bottom: 1px solid var(--border); }
            tr:hover { background: rgba(255,255,255,0.02); }
            
            .status-tag { padding: 4px 8px; border-radius: 4px; font-size: 12px; font-weight: bold; }
            .status-paid { background: rgba(76, 175, 80, 0.2); color: #4CAF50; }
            .status-pending { background: rgba(229, 169, 60, 0.2); color: var(--accent); }
            .status-expired { background: rgba(229, 9, 20, 0.2); color: var(--primary); }
            
            .btn { background: var(--primary); color: #fff; padding: 8px 16px; border: none; border-radius: 6px; cursor: pointer; text-decoration: none; font-size: 14px; }
            .btn-secondary { background: var(--border); color: #fff; }
        </style>
    </head>
    <body>
        <div class="container">
            <header>
                <div>
                    <h1>🎬 Cinema Booking Admin Portal</h1>
                    <span class="badge-live">● VNPay Sandbox & Realtime DB Active</span>
                </div>
                <div class="nav-links">
                    <a href="/admin" class="active">Thống Kê</a>
                    <a href="/scanner" target="_blank">Máy Soát Vé 📱</a>
                    <a href="/" target="_blank">Cổng Đặt Vé 🌐</a>
                </div>
            </header>

            <div class="stats-grid" id="statsGrid">
                <div class="stat-card">
                    <div class="stat-label">Tổng Doanh Thu</div>
                    <div class="stat-val gold" id="revVal">0 VNĐ</div>
                </div>
                <div class="stat-card">
                    <div class="stat-label">Vé Đã Bán</div>
                    <div class="stat-val green" id="ticketsVal">0</div>
                </div>
                <div class="stat-card">
                    <div class="stat-label">Vé Đã Soát Cổng</div>
                    <div class="stat-val" id="scannedVal">0</div>
                </div>
                <div class="stat-card">
                    <div class="stat-label">Tổng Đơn Hàng</div>
                    <div class="stat-val" id="ordersVal">0</div>
                </div>
            </div>

            <div class="section">
                <div class="section-header">
                    <h2>Đơn Đặt Vé Gần Đây</h2>
                    <button class="btn btn-secondary" onclick="loadStats()">Làm Mới ⟳</button>
                </div>
                <table>
                    <thead>
                        <tr>
                            <th>Mã Đơn</th>
                            <th>Khách Hàng</th>
                            <th>Phim</th>
                            <th>Rạp</th>
                            <th>Tổng Tiền</th>
                            <th>Trạng Thái</th>
                            <th>Thời Gian</th>
                        </tr>
                    </thead>
                    <tbody id="ordersTable"><tr><td colspan="7">Đang tải dữ liệu...</td></tr></tbody>
                </table>
            </div>

            <div class="section">
                <div class="section-header">
                    <h2>Nhật Ký Quét Vé Soát Cổng (Audit Log)</h2>
                </div>
                <table>
                    <thead>
                        <tr>
                            <th>Mã Vé</th>
                            <th>Phim</th>
                            <th>Ghế</th>
                            <th>Kết Quả</th>
                            <th>Ghi Chú</th>
                            <th>Thời Gian Quét</th>
                        </tr>
                    </thead>
                    <tbody id="scansTable"><tr><td colspan="6">Đang tải nhật ký...</td></tr></tbody>
                </table>
            </div>
        </div>

        <script>
            async function loadStats() {
                try {
                    const res = await fetch('/api/admin/stats');
                    const data = await res.json();
                    
                    document.getElementById('revVal').innerText = Number(data.total_revenue).toLocaleString('vi-VN') + ' VNĐ';
                    document.getElementById('ticketsVal').innerText = data.total_tickets;
                    document.getElementById('scannedVal').innerText = data.total_scanned;
                    document.getElementById('ordersVal').innerText = data.total_orders;

                    const ordersHtml = data.recent_orders.map(o => `
                        <tr>
                            <td><b>${o.order_code}</b></td>
                            <td>${o.customer_name || 'Khách'}</td>
                            <td>${o.movie_title || '-'}</td>
                            <td>${o.cinema_name || '-'}</td>
                            <td style="color:#E5A93C; font-weight:bold;">${Number(o.final_amount).toLocaleString('vi-VN')} đ</td>
                            <td><span class="status-tag ${o.status === 'PAID' ? 'status-paid' : (o.status === 'PENDING_PAYMENT' ? 'status-pending' : 'status-expired')}">${o.status}</span></td>
                            <td style="color:#8C92A4;">${o.created_at}</td>
                        </tr>
                    `).join('');
                    document.getElementById('ordersTable').innerHTML = ordersHtml || '<tr><td colspan="7">Chưa có đơn hàng nào</td></tr>';

                    const scansHtml = data.recent_scans.map(s => `
                        <tr>
                            <td><b>${s.ticket_code}</b></td>
                            <td>${s.movie_title || '-'}</td>
                            <td><b style="color:#E50914;">${s.seat_code}</b></td>
                            <td><span class="status-tag ${s.result === 'SUCCESS' ? 'status-paid' : 'status-expired'}">${s.result}</span></td>
                            <td>${s.notes || '-'}</td>
                            <td style="color:#8C92A4;">${s.scan_time}</td>
                        </tr>
                    `).join('');
                    document.getElementById('scansTable').innerHTML = scansHtml || '<tr><td colspan="6">Chưa có lượt quét vé nào</td></tr>';
                } catch(e) {
                    console.error(e);
                }
            }
            loadStats();
            setInterval(loadStats, 5000);
        </script>
    </body>
    </html>
    """)


@app.get("/scanner", response_class=HTMLResponse)
def ticket_scanner_page():
    return HTMLResponse(content="""
    <!DOCTYPE html>
    <html lang="vi">
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Thiết Bị Soát Vé Tại Cổng - Cinema Ticket Scanner</title>
        <style>
            :root {
                --bg: #12141C;
                --card: #1A1D28;
                --border: #2B3042;
                --primary: #E50914;
                --accent: #E5A93C;
                --green: #4CAF50;
                --text: #FFFFFF;
                --muted: #8C92A4;
            }
            body { background: var(--bg); color: var(--text); font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; margin: 0; padding: 20px; display: flex; justify-content: center; }
            .scanner-box { background: var(--card); border: 1px solid var(--border); border-radius: 16px; padding: 24px; max-width: 480px; width: 100%; box-shadow: 0 10px 30px rgba(0,0,0,0.5); }
            h1 { font-size: 20px; color: var(--accent); margin-top: 0; text-align: center; }
            .input-group { margin-bottom: 20px; }
            label { display: block; font-size: 13px; color: var(--muted); margin-bottom: 8px; }
            input[type="text"] { width: 100%; box-sizing: border-box; background: #0E1017; border: 1px solid var(--border); border-radius: 8px; padding: 14px; color: #fff; font-size: 16px; }
            input[type="text"]:focus { outline: none; border-color: var(--accent); }
            .btn { width: 100%; background: var(--primary); color: #fff; border: none; padding: 14px; border-radius: 8px; font-size: 16px; font-weight: bold; cursor: pointer; }
            .btn:hover { background: #f6121d; }
            
            #resultCard { margin-top: 24px; padding: 16px; border-radius: 8px; display: none; }
            .res-success { background: rgba(76, 175, 80, 0.15); border: 1px solid var(--green); color: var(--green); }
            .res-duplicate { background: rgba(229, 9, 20, 0.15); border: 1px solid var(--primary); color: var(--primary); }
            .res-invalid { background: rgba(229, 169, 60, 0.15); border: 1px solid var(--accent); color: var(--accent); }
            
            .ticket-info { color: #fff; font-size: 14px; margin-top: 12px; border-top: 1px solid rgba(255,255,255,0.1); padding-top: 12px; }
            .ticket-info p { margin: 6px 0; }
        </style>
    </head>
    <body>
        <div class="scanner-box">
            <h1>📱 Máy Soát Vé Vào Phòng Chiếu</h1>
            <p style="color:var(--muted); font-size:13px; text-align:center; margin-bottom:20px;">
                Quét mã QR trên cuống vé hoặc nhập mã vé / token để xác thực hợp lệ. Hệ thống tự động chống vé trùng và phát hiện gian lận.
            </p>

            <div class="input-group">
                <label>Nhập Mã Vé Hoặc Chuỗi Dữ Liệu QR Code:</label>
                <input type="text" id="ticketInput" placeholder="Ví dụ: TCK26... hoặc TOKEN..." autofocus>
            </div>
            <button class="btn" onclick="checkTicket()">XÁC THỰC VÉ 🔍</button>

            <div id="resultCard">
                <h3 id="resTitle" style="margin:0 0 8px; font-size:18px;"></h3>
                <div id="resMsg" style="font-size:14px;"></div>
                <div class="ticket-info" id="ticketDetails" style="display:none;"></div>
            </div>
        </div>

        <script>
            document.getElementById('ticketInput').addEventListener('keypress', function(e) {
                if (e.key === 'Enter') checkTicket();
            });

            async function checkTicket() {
                const raw = document.getElementById('ticketInput').value.trim();
                if (!raw) return;

                // Extract token if raw QR string
                let codeOrToken = raw;
                if (raw.includes('TOKEN:')) {
                    codeOrToken = raw.split('TOKEN:')[1].split('|')[0];
                } else if (raw.startsWith('CINETICKET:')) {
                    codeOrToken = raw.split('CINETICKET:')[1].split('|')[0];
                }

                try {
                    const res = await fetch('/api/tickets/scan', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ code_or_token: codeOrToken, scanned_by: 'CỔNG_01' })
                    });
                    const data = await res.json();
                    
                    const card = document.getElementById('resultCard');
                    const title = document.getElementById('resTitle');
                    const msg = document.getElementById('resMsg');
                    const details = document.getElementById('ticketDetails');
                    card.style.display = 'block';

                    if (data.result === 'SUCCESS') {
                        card.className = 'res-success';
                        title.innerText = '✓ VÉ HỢP LỆ - QUA CỬA';
                        msg.innerText = data.message;
                        details.style.display = 'block';
                        details.innerHTML = `
                            <p><b>Phim:</b> ${data.movie_title}</p>
                            <p><b>Rạp:</b> ${data.cinema_name} | <b>Phòng:</b> ${data.room_name}</p>
                            <p><b>Suất chiếu:</b> ${data.start_time} - ${data.show_date}</p>
                            <p><b>Vị trí ghế:</b> <span style="font-size:18px; color:#E5A93C; font-weight:bold;">${data.seat_code}</span></p>
                            <p><b>Khách hàng:</b> ${data.customer_name || 'Khách'}</p>
                        `;
                    } else if (data.result === 'DUPLICATE') {
                        card.className = 'res-duplicate';
                        title.innerText = '❌ CẢNH BÁO: VÉ ĐÃ QUA CỬA!';
                        msg.innerText = data.message;
                        details.style.display = 'none';
                    } else {
                        card.className = 'res-invalid';
                        title.innerText = '⚠️ VÉ KHÔNG HỢP LỆ';
                        msg.innerText = data.message;
                        details.style.display = 'none';
                    }

                    document.getElementById('ticketInput').value = '';
                } catch(e) {
                    alert('Lỗi kết nối máy chủ: ' + e);
                }
            }
        </script>
    </body>
    </html>
    """)


@app.get("/ticket/{ticket_code}", response_class=HTMLResponse)
def ticket_view_page(ticket_code: str):
    conn = database.get_db_connection()
    ticket = conn.execute("""
        SELECT t.*, o.order_code, o.status as order_status,
               s.show_date, s.start_time, s.format_type,
               m.title as movie_title, m.poster_url, m.age_rating,
               r.name as room_name, c.name as cinema_name, c.address as cinema_address
        FROM tickets t
        JOIN orders o ON t.order_id = o.id
        JOIN showtimes s ON t.showtime_id = s.id
        JOIN movies m ON s.movie_id = m.id
        JOIN cinema_rooms r ON s.room_id = r.id
        JOIN cinemas c ON r.cinema_id = c.id
        WHERE t.ticket_code = ?
    """, (ticket_code,)).fetchone()
    conn.close()

    if not ticket:
        return HTMLResponse("<h2>Vé không tồn tại</h2>", status_code=404)

    # Use online QR generator for web display
    qr_img_url = f"https://api.qrserver.com/v1/create-qr-code/?size=250x250&data={ticket['qr_code_data']}"

    return HTMLResponse(content=f"""
    <!DOCTYPE html>
    <html lang="vi">
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Vé Điện Tử - {ticket['movie_title']}</title>
        <style>
            :root {{
                --bg: #12141C;
                --card: #1A1D28;
                --border: #2B3042;
                --primary: #E50914;
                --accent: #E5A93C;
                --text: #FFFFFF;
                --muted: #8C92A4;
            }}
            body {{ background: var(--bg); color: var(--text); font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; display: flex; justify-content: center; padding: 20px; margin: 0; }}
            .stub {{ background: var(--card); max-width: 420px; width: 100%; border-radius: 16px; overflow: hidden; border: 1px solid var(--border); box-shadow: 0 15px 35px rgba(0,0,0,0.6); position: relative; }}
            .poster-wrap {{ height: 180px; overflow: hidden; position: relative; background: #000; }}
            .poster-img {{ width: 100%; height: 100%; object-fit: cover; opacity: 0.6; }}
            .movie-headline {{ position: absolute; bottom: 16px; left: 16px; right: 16px; }}
            .movie-title {{ font-size: 20px; font-weight: bold; margin: 0 0 6px; text-shadow: 0 2px 4px rgba(0,0,0,0.8); }}
            .badges span {{ background: var(--accent); color: #000; font-size: 11px; font-weight: bold; padding: 2px 6px; border-radius: 4px; margin-right: 6px; }}
            
            .stub-body {{ padding: 20px; }}
            .row {{ display: flex; justify-content: space-between; margin-bottom: 12px; font-size: 14px; }}
            .row .label {{ color: var(--muted); }}
            .row .val {{ font-weight: 600; }}
            
            /* Perforated divider with cutouts */
            .perforated {{ position: relative; border-top: 2px dashed #2B3042; margin: 20px -20px; }}
            .perforated::before, .perforated::after {{
                content: ''; position: absolute; width: 24px; height: 24px; background: var(--bg); border-radius: 50%; top: -12px;
            }}
            .perforated::before {{ left: -12px; }}
            .perforated::after {{ right: -12px; }}
            
            .qr-area {{ text-align: center; padding: 10px 0; }}
            .qr-img {{ background: #fff; padding: 10px; border-radius: 12px; box-shadow: 0 4px 12px rgba(0,0,0,0.3); }}
            .token-text {{ font-size: 12px; color: var(--accent); margin-top: 10px; font-family: monospace; letter-spacing: 1px; }}
            .guide {{ font-size: 12px; color: var(--muted); text-align: center; margin-top: 14px; line-height: 1.5; }}
        </style>
    </head>
    <body>
        <div class="stub">
            <div class="poster-wrap">
                <img class="poster-img" src="{ticket['poster_url']}" alt="{ticket['movie_title']}">
                <div class="movie-headline">
                    <div class="badges">
                        <span>{ticket['format_type'] or '2D'}</span>
                        <span>{ticket['age_rating']}</span>
                    </div>
                    <div class="movie-title">{ticket['movie_title']}</div>
                </div>
            </div>

            <div class="stub-body">
                <div class="row">
                    <span class="label">Rạp chiếu:</span>
                    <span class="val">{ticket['cinema_name']}</span>
                </div>
                <div class="row">
                    <span class="label">Phòng chiếu:</span>
                    <span class="val">{ticket['room_name']}</span>
                </div>
                <div class="row">
                    <span class="label">Suất chiếu:</span>
                    <span class="val" style="color:var(--accent);">{ticket['start_time']} - {ticket['show_date']}</span>
                </div>
                <div class="row">
                    <span class="label">Vị trí ghế:</span>
                    <span class="val" style="font-size:20px; color:var(--primary);">{ticket['seat_code']}</span>
                </div>
                <div class="row">
                    <span class="label">Trạng thái vé:</span>
                    <span class="val" style="color:#4CAF50;">● {ticket['status']} (Đã thanh toán)</span>
                </div>

                <div class="perforated"></div>

                <div class="qr-area">
                    <img class="qr-img" src="{qr_img_url}" width="180" height="180" alt="Mã QR Soát Vé">
                    <div class="token-text">{ticket['ticket_code']}</div>
                    <div class="guide">
                        Vui lòng đưa mã QR này cho nhân viên soát vé tại cửa phòng chiếu.<br>
                        Mỗi mã chỉ được sử dụng một lần duy nhất.
                    </div>
                </div>
            </div>
        </div>
    </body>
    </html>
    """)


@app.get("/", response_class=HTMLResponse)
def home_portal_page():
    """Customer-facing modern movie booking landing page."""
    return HTMLResponse(content="""
    <!DOCTYPE html>
    <html lang="vi">
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Cinema Booking - Hệ Thống Đặt Vé Xem Phim Online</title>
        <style>
            :root {
                --bg: #12141C;
                --card: #1A1D28;
                --border: #2B3042;
                --primary: #E50914;
                --accent: #E5A93C;
                --text: #FFFFFF;
                --muted: #8C92A4;
            }
            body { background: var(--bg); color: var(--text); font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; margin: 0; padding: 0; }
            header { background: rgba(18, 20, 28, 0.95); backdrop-filter: blur(10px); position: sticky; top: 0; z-index: 100; border-bottom: 1px solid var(--border); padding: 16px 24px; display: flex; justify-content: space-between; align-items: center; }
            .logo { font-size: 20px; font-weight: 800; color: var(--primary); text-decoration: none; }
            .logo span { color: var(--accent); }
            .nav-actions a { color: var(--muted); text-decoration: none; font-size: 14px; margin-left: 20px; }
            .nav-actions a:hover { color: var(--text); }
            
            .hero { padding: 40px 24px 20px; text-align: center; }
            .hero h1 { font-size: 32px; margin: 0 0 10px; }
            .hero p { color: var(--muted); margin: 0; }
            
            .container { max-width: 1200px; margin: 0 auto; padding: 24px; }
            .section-title { font-size: 20px; margin-bottom: 20px; display: flex; align-items: center; }
            .section-title::before { content: ''; width: 4px; height: 20px; background: var(--primary); margin-right: 10px; border-radius: 2px; }
            
            .movie-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 24px; }
            .movie-card { background: var(--card); border: 1px solid var(--border); border-radius: 12px; overflow: hidden; transition: transform 0.2s, box-shadow 0.2s; }
            .movie-card:hover { transform: translateY(-4px); box-shadow: 0 12px 24px rgba(0,0,0,0.4); }
            .poster-box { height: 340px; overflow: hidden; position: relative; background: #000; }
            .poster-box img { width: 100%; height: 100%; object-fit: cover; }
            .rating-tag { position: absolute; top: 12px; right: 12px; background: rgba(0,0,0,0.7); backdrop-filter: blur(4px); color: var(--accent); padding: 4px 8px; border-radius: 6px; font-size: 13px; font-weight: bold; }
            .card-body { padding: 16px; }
            .movie-name { font-size: 16px; font-weight: bold; margin: 0 0 8px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
            .movie-meta { font-size: 13px; color: var(--muted); margin-bottom: 16px; }
            .btn-book { display: block; text-align: center; background: var(--primary); color: #fff; text-decoration: none; padding: 10px; border-radius: 8px; font-weight: bold; font-size: 14px; }
            .btn-book:hover { background: #f6121d; }
        </style>
    </head>
    <body>
        <header>
            <a href="/" class="logo">CINE<span>TICKET</span></a>
            <div class="nav-actions">
                <a href="/admin">Quản Trị Admin ⚙️</a>
                <a href="/scanner">Máy Soát Vé 📱</a>
            </div>
        </header>

        <div class="hero">
            <h1>ĐẶT VÉ XEM PHIM TRỰC TUYẾN</h1>
            <p>Trải nghiệm rạp chiếu đẳng cấp - Thanh toán tức thì qua cổng VNPay Sandbox an toàn</p>
        </div>

        <div class="container">
            <div class="section-title">Phim Đang Chiếu Tại Đà Nẵng</div>
            <div class="movie-grid" id="movieList">Đang tải danh sách phim...</div>
        </div>

        <script>
            async function loadMovies() {
                try {
                    const res = await fetch('/api/movies?status=NOW_SHOWING');
                    const movies = await res.json();
                    const html = movies.map(m => `
                        <div class="movie-card">
                            <div class="poster-box">
                                <img src="${m.poster_url}" alt="${m.title}">
                                <div class="rating-tag">★ ${m.rating}</div>
                            </div>
                            <div class="card-body">
                                <div class="movie-name">${m.title}</div>
                                <div class="movie-meta">${m.duration_minutes} phút | ${m.age_rating}</div>
                                <a href="/admin" class="btn-book">ĐẶT VÉ NGAY</a>
                            </div>
                        </div>
                    `).join('');
                    document.getElementById('movieList').innerHTML = html;
                } catch(e) {
                    console.error(e);
                }
            }
            loadMovies();
        </script>
    </body>
    </html>
    """)
