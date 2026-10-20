"""
Cinema Database Management Module
Using SQLite with atomic transactions, full entity schema,
seat-holding concurrency control, and ticket validation logging.
"""

import sqlite3
import os
import secrets
from datetime import datetime, timedelta
from typing import List, Dict, Any, Optional, Tuple

DB_PATH = os.path.join(os.path.dirname(__file__), "cinema.db")


def get_db_connection() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH, timeout=30.0)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON;")
    return conn


def init_db():
    """Create all required tables, indexes, and constraints."""
    conn = get_db_connection()
    cursor = conn.cursor()

    cursor.executescript("""
    -- 1. Users
    CREATE TABLE IF NOT EXISTS users (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        email TEXT UNIQUE NOT NULL,
        password_hash TEXT NOT NULL,
        full_name TEXT,
        phone TEXT,
        role TEXT NOT NULL DEFAULT 'USER', -- USER or ADMIN
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

    -- 2. Genres
    CREATE TABLE IF NOT EXISTS genres (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT UNIQUE NOT NULL,
        description TEXT
    );

    -- 3. Movies
    CREATE TABLE IF NOT EXISTS movies (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        title TEXT NOT NULL,
        description TEXT,
        duration_minutes INTEGER DEFAULT 120,
        director TEXT,
        actors TEXT,
        release_date TEXT,
        age_rating TEXT DEFAULT 'C13',
        genre_id INTEGER REFERENCES genres(id),
        poster_url TEXT,
        banner_url TEXT,
        trailer_url TEXT,
        rating REAL DEFAULT 8.5,
        status TEXT DEFAULT 'NOW_SHOWING', -- NOW_SHOWING, COMING_SOON
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

    -- 4. Cinemas
    CREATE TABLE IF NOT EXISTS cinemas (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL,
        address TEXT NOT NULL,
        province TEXT NOT NULL DEFAULT 'Đà Nẵng',
        phone TEXT,
        latitude REAL,
        longitude REAL
    );

    -- 5. Cinema Rooms
    CREATE TABLE IF NOT EXISTS cinema_rooms (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        cinema_id INTEGER NOT NULL REFERENCES cinemas(id) ON DELETE CASCADE,
        name TEXT NOT NULL,
        room_type TEXT DEFAULT '2D Digital',
        total_seats INTEGER DEFAULT 30
    );

    -- 6. Seats
    CREATE TABLE IF NOT EXISTS seats (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        room_id INTEGER NOT NULL REFERENCES cinema_rooms(id) ON DELETE CASCADE,
        seat_row TEXT NOT NULL,
        seat_number INTEGER NOT NULL,
        seat_code TEXT NOT NULL,
        seat_type TEXT DEFAULT 'STANDARD', -- STANDARD, VIP, COUPLE
        base_price INTEGER DEFAULT 45000,
        UNIQUE(room_id, seat_code)
    );

    -- 7. Showtimes
    CREATE TABLE IF NOT EXISTS showtimes (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        movie_id INTEGER NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
        room_id INTEGER NOT NULL REFERENCES cinema_rooms(id) ON DELETE CASCADE,
        show_date TEXT NOT NULL,
        start_time TEXT NOT NULL,
        end_time TEXT,
        format_type TEXT DEFAULT '2D',
        price_override INTEGER,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

    -- 8. Orders
    CREATE TABLE IF NOT EXISTS orders (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        order_code TEXT UNIQUE NOT NULL,
        user_id INTEGER REFERENCES users(id),
        customer_name TEXT,
        customer_email TEXT,
        customer_phone TEXT,
        showtime_id INTEGER NOT NULL REFERENCES showtimes(id),
        total_amount INTEGER NOT NULL,
        discount_amount INTEGER DEFAULT 0,
        final_amount INTEGER NOT NULL,
        status TEXT NOT NULL DEFAULT 'PENDING_PAYMENT', -- PENDING_PAYMENT, PAID, EXPIRED, CANCELLED
        hold_expires_at TIMESTAMP,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

    -- 9. Showtime Seats (Real-time seat occupancy & concurrency lock)
    CREATE TABLE IF NOT EXISTS showtime_seats (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        showtime_id INTEGER NOT NULL REFERENCES showtimes(id) ON DELETE CASCADE,
        seat_id INTEGER NOT NULL REFERENCES seats(id) ON DELETE CASCADE,
        status TEXT NOT NULL DEFAULT 'AVAILABLE', -- AVAILABLE, HELD, SOLD
        held_by_order_id INTEGER REFERENCES orders(id) ON DELETE SET NULL,
        hold_expires_at TIMESTAMP,
        UNIQUE(showtime_id, seat_id)
    );

    -- 10. Order Items
    CREATE TABLE IF NOT EXISTS order_items (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        order_id INTEGER NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
        seat_id INTEGER NOT NULL REFERENCES seats(id),
        seat_code TEXT NOT NULL,
        seat_type TEXT NOT NULL,
        price INTEGER NOT NULL
    );

    -- 11. Payments
    CREATE TABLE IF NOT EXISTS payments (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        order_id INTEGER NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
        payment_method TEXT NOT NULL, -- VNPAY, VIETQR, CASH
        transaction_ref TEXT NOT NULL,
        vnp_transaction_no TEXT,
        amount INTEGER NOT NULL,
        status TEXT NOT NULL DEFAULT 'PENDING', -- PENDING, SUCCESS, FAILED, EXPIRED
        bank_code TEXT,
        pay_date TEXT,
        response_code TEXT,
        raw_response TEXT,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

    -- 12. Tickets
    CREATE TABLE IF NOT EXISTS tickets (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        ticket_code TEXT UNIQUE NOT NULL,
        order_id INTEGER NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
        showtime_id INTEGER NOT NULL REFERENCES showtimes(id),
        seat_id INTEGER NOT NULL REFERENCES seats(id),
        seat_code TEXT NOT NULL,
        user_id INTEGER REFERENCES users(id),
        customer_name TEXT,
        customer_email TEXT,
        verification_token TEXT UNIQUE NOT NULL,
        qr_code_data TEXT NOT NULL,
        status TEXT NOT NULL DEFAULT 'VALID', -- VALID, USED, CANCELLED
        issued_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

    -- 13. Ticket Scans (Audit log for check-in)
    CREATE TABLE IF NOT EXISTS ticket_scans (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        ticket_id INTEGER NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
        scanned_by TEXT DEFAULT 'GATE_STAFF',
        scan_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        result TEXT NOT NULL, -- SUCCESS, DUPLICATE, INVALID
        notes TEXT
    );

    -- 14. Promotions
    CREATE TABLE IF NOT EXISTS promotions (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        code TEXT UNIQUE NOT NULL,
        description TEXT,
        discount_percent INTEGER DEFAULT 10,
        max_discount INTEGER DEFAULT 50000,
        start_date TEXT,
        end_date TEXT,
        is_active INTEGER DEFAULT 1
    );

    -- Helpful indexes for rapid queries
    CREATE INDEX IF NOT EXISTS idx_showtime_seats ON showtime_seats(showtime_id, status);
    CREATE INDEX IF NOT EXISTS idx_orders_code ON orders(order_code);
    CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
    CREATE INDEX IF NOT EXISTS idx_tickets_token ON tickets(verification_token);
    CREATE INDEX IF NOT EXISTS idx_tickets_code ON tickets(ticket_code);
    """)

    conn.commit()
    conn.close()


def release_expired_holds():
    """
    Find all showtime seats where hold has expired,
    reset seats to AVAILABLE, and mark orders as EXPIRED.
    """
    conn = get_db_connection()
    cursor = conn.cursor()
    now_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    cursor.execute("""
        SELECT DISTINCT held_by_order_id 
        FROM showtime_seats 
        WHERE status = 'HELD' AND hold_expires_at <= ?
    """, (now_str,))
    expired_order_ids = [row[0] for row in cursor.fetchall() if row[0] is not None]

    if expired_order_ids:
        placeholders = ",".join("?" for _ in expired_order_ids)
        # Mark seats available
        cursor.execute(f"""
            UPDATE showtime_seats 
            SET status = 'AVAILABLE', held_by_order_id = NULL, hold_expires_at = NULL 
            WHERE held_by_order_id IN ({placeholders}) AND status = 'HELD'
        """, expired_order_ids)

        # Mark orders EXPIRED
        cursor.execute(f"""
            UPDATE orders 
            SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP 
            WHERE id IN ({placeholders}) AND status = 'PENDING_PAYMENT'
        """, expired_order_ids)

        conn.commit()

    conn.close()


def hold_seats_transaction(
    showtime_id: int,
    seat_ids: List[int],
    customer_name: str,
    customer_email: str,
    customer_phone: str,
    user_id: Optional[int] = None,
    hold_minutes: int = 10,
    promo_code: Optional[str] = None
) -> Dict[str, Any]:
    """
    Atomic seat hold reservation:
    1. Releases any previously expired holds.
    2. Locks and checks each requested seat.
    3. If any seat is SOLD or actively HELD by another customer, aborts with error.
    4. Calculates prices based on seat types (Standard vs VIP).
    5. Creates Order with status PENDING_PAYMENT and links seats.
    """
    release_expired_holds()

    conn = get_db_connection()
    cursor = conn.cursor()

    try:
        cursor.execute("BEGIN IMMEDIATE;")

        # Fetch showtime & movie info
        cursor.execute("""
            SELECT s.*, m.title as movie_title, r.name as room_name, c.name as cinema_name
            FROM showtimes s
            JOIN movies m ON s.movie_id = m.id
            JOIN cinema_rooms r ON s.room_id = r.id
            JOIN cinemas c ON r.cinema_id = c.id
            WHERE s.id = ?
        """, (showtime_id,))
        showtime = cursor.fetchone()
        if not showtime:
            raise ValueError(f"Suất chiếu #{showtime_id} không tồn tại")

        # Check seats in room
        placeholders = ",".join("?" for _ in seat_ids)
        cursor.execute(f"""
            SELECT s.id, s.seat_code, s.seat_type, s.base_price,
                   COALESCE(ss.status, 'AVAILABLE') as current_status,
                   ss.hold_expires_at, ss.held_by_order_id
            FROM seats s
            LEFT JOIN showtime_seats ss ON s.id = ss.seat_id AND ss.showtime_id = ?
            WHERE s.id IN ({placeholders}) AND s.room_id = ?
        """, [showtime_id] + seat_ids + [showtime["room_id"]])
        seat_rows = cursor.fetchall()

        if len(seat_rows) != len(seat_ids):
            raise ValueError("Một số ghế yêu cầu không thuộc phòng chiếu này!")

        now = datetime.now()
        now_str = now.strftime("%Y-%m-%d %H:%M:%S")

        # Validate availability
        for s in seat_rows:
            st = s["current_status"]
            exp = s["hold_expires_at"]
            if st == "SOLD":
                raise ValueError(f"Ghế {s['seat_code']} đã được bán!")
            if st == "HELD" and exp and exp > now_str:
                raise ValueError(f"Ghế {s['seat_code']} đang được khách hàng khác giữ!")

        # Calculate prices
        total_amount = 0
        order_items_data = []
        for s in seat_rows:
            # VIP seats +15,000 VND
            price = s["base_price"]
            if s["seat_type"] == "VIP":
                price += 15000
            elif s["seat_type"] == "COUPLE":
                price += 35000
            total_amount += price
            order_items_data.append((s["id"], s["seat_code"], s["seat_type"], price))

        # Apply promotion if valid
        discount_amount = 0
        if promo_code:
            cursor.execute("""
                SELECT * FROM promotions 
                WHERE code = ? AND is_active = 1
            """, (promo_code.strip().upper(),))
            promo = cursor.fetchone()
            if promo:
                calc_discount = int(total_amount * (promo["discount_percent"] / 100.0))
                discount_amount = min(calc_discount, promo["max_discount"])

        final_amount = max(0, total_amount - discount_amount)

        # Generate unique order code
        order_code = f"ORD{datetime.now().strftime('%y%m%d%H%M%S')}{secrets.randbelow(1000):03d}"
        hold_expires_at = (now + timedelta(minutes=hold_minutes)).strftime("%Y-%m-%d %H:%M:%S")

        # Insert Order
        cursor.execute("""
            INSERT INTO orders (
                order_code, user_id, customer_name, customer_email, customer_phone,
                showtime_id, total_amount, discount_amount, final_amount,
                status, hold_expires_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING_PAYMENT', ?)
        """, (
            order_code, user_id, customer_name, customer_email, customer_phone,
            showtime_id, total_amount, discount_amount, final_amount, hold_expires_at
        ))
        order_id = cursor.lastrowid

        # Insert Order Items & Update Showtime Seats
        for seat_id, seat_code, seat_type, price in order_items_data:
            cursor.execute("""
                INSERT INTO order_items (order_id, seat_id, seat_code, seat_type, price)
                VALUES (?, ?, ?, ?, ?)
            """, (order_id, seat_id, seat_code, seat_type, price))

            cursor.execute("""
                INSERT INTO showtime_seats (showtime_id, seat_id, status, held_by_order_id, hold_expires_at)
                VALUES (?, ?, 'HELD', ?, ?)
                ON CONFLICT(showtime_id, seat_id) DO UPDATE SET
                    status = 'HELD',
                    held_by_order_id = excluded.held_by_order_id,
                    hold_expires_at = excluded.hold_expires_at
            """, (showtime_id, seat_id, order_id, hold_expires_at))

        conn.commit()

        return {
            "order_id": order_id,
            "order_code": order_code,
            "movie_title": showtime["movie_title"],
            "cinema_name": showtime["cinema_name"],
            "room_name": showtime["room_name"],
            "show_date": showtime["show_date"],
            "start_time": showtime["start_time"],
            "seats": [s[1] for s in order_items_data],
            "total_amount": total_amount,
            "discount_amount": discount_amount,
            "final_amount": final_amount,
            "hold_expires_at": hold_expires_at,
            "status": "PENDING_PAYMENT"
        }

    except Exception as e:
        conn.rollback()
        raise e
    finally:
        conn.close()


def confirm_order_payment(
    order_code: str,
    payment_method: str,
    transaction_ref: str,
    amount: int,
    vnp_transaction_no: Optional[str] = None,
    bank_code: Optional[str] = None,
    raw_response: Optional[str] = None
) -> Dict[str, Any]:
    """
    Idempotent order payment confirmation:
    1. Verifies order exists and matches amount.
    2. If already PAID, returns existing ticket details (prevents double issuance).
    3. Sets Order -> PAID.
    4. Sets ShowtimeSeats -> SOLD.
    5. Records Payment record.
    6. Generates valid e-tickets with secure tokens and QR codes.
    """
    conn = get_db_connection()
    cursor = conn.cursor()

    try:
        cursor.execute("BEGIN IMMEDIATE;")

        cursor.execute("""
            SELECT o.*, s.show_date, s.start_time, m.title as movie_title, m.poster_url,
                   r.name as room_name, c.name as cinema_name, c.address as cinema_address
            FROM orders o
            JOIN showtimes s ON o.showtime_id = s.id
            JOIN movies m ON s.movie_id = m.id
            JOIN cinema_rooms r ON s.room_id = r.id
            JOIN cinemas c ON r.cinema_id = c.id
            WHERE o.order_code = ?
        """, (order_code,))
        order = cursor.fetchone()

        if not order:
            raise ValueError(f"Không tìm thấy đơn hàng {order_code}")

        # Idempotency check
        if order["status"] == "PAID":
            # Fetch existing tickets
            cursor.execute("SELECT * FROM tickets WHERE order_id = ?", (order["id"],))
            existing_tickets = [dict(t) for t in cursor.fetchall()]
            conn.commit()
            return {
                "already_confirmed": True,
                "order_code": order_code,
                "status": "PAID",
                "tickets": existing_tickets
            }

        # Verify amount
        if order["final_amount"] != amount:
            raise ValueError(f"Số tiền không khớp! Đơn hàng: {order['final_amount']}, Nhận được: {amount}")

        # Update Order
        cursor.execute("""
            UPDATE orders 
            SET status = 'PAID', updated_at = CURRENT_TIMESTAMP 
            WHERE id = ?
        """, (order["id"],))

        # Update Seats to SOLD
        cursor.execute("""
            UPDATE showtime_seats 
            SET status = 'SOLD', hold_expires_at = NULL 
            WHERE held_by_order_id = ?
        """, (order["id"],))

        # Record Payment
        cursor.execute("""
            INSERT INTO payments (
                order_id, payment_method, transaction_ref, vnp_transaction_no,
                amount, status, bank_code, pay_date, response_code, raw_response
            ) VALUES (?, ?, ?, ?, ?, 'SUCCESS', ?, datetime('now'), '00', ?)
        """, (
            order["id"], payment_method, transaction_ref, vnp_transaction_no,
            amount, bank_code, raw_response
        ))

        # Get Order Items
        cursor.execute("SELECT * FROM order_items WHERE order_id = ?", (order["id"],))
        items = cursor.fetchall()

        # Generate Tickets
        issued_tickets = []
        for item in items:
            ticket_code = f"TCK{datetime.now().strftime('%y%m%d%H%M')}{secrets.randbelow(10000):04d}"
            verification_token = secrets.token_urlsafe(24)
            # QR payload specifically for e-ticket entrance scanning
            qr_data = f"CINETICKET:{ticket_code}|FILM:{order['movie_title']}|SEAT:{item['seat_code']}|ROOM:{order['room_name']}|TIME:{order['start_time']}|DATE:{order['show_date']}|TOKEN:{verification_token}"

            cursor.execute("""
                INSERT INTO tickets (
                    ticket_code, order_id, showtime_id, seat_id, seat_code,
                    user_id, customer_name, customer_email,
                    verification_token, qr_code_data, status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'VALID')
            """, (
                ticket_code, order["id"], order["showtime_id"], item["seat_id"], item["seat_code"],
                order["user_id"], order["customer_name"], order["customer_email"],
                verification_token, qr_data
            ))

            issued_tickets.append({
                "ticket_code": ticket_code,
                "seat_code": item["seat_code"],
                "verification_token": verification_token,
                "qr_code_data": qr_data,
                "status": "VALID"
            })

        conn.commit()

        return {
            "already_confirmed": False,
            "order_code": order_code,
            "status": "PAID",
            "movie_title": order["movie_title"],
            "cinema_name": order["cinema_name"],
            "cinema_address": order["cinema_address"],
            "room_name": order["room_name"],
            "show_date": order["show_date"],
            "start_time": order["start_time"],
            "customer_name": order["customer_name"],
            "tickets": issued_tickets
        }

    except Exception as e:
        conn.rollback()
        raise e
    finally:
        conn.close()


def verify_ticket_scan(token_or_code: str, scanned_by: str = "GATE_STAFF") -> Dict[str, Any]:
    """
    Validate and redeem e-ticket at cinema entrance:
    - Verifies ticket authenticity
    - Detects and prevents duplicate scans (anti-fraud)
    - Logs scan result to audit table
    """
    conn = get_db_connection()
    cursor = conn.cursor()

    try:
        cursor.execute("BEGIN IMMEDIATE;")

        cursor.execute("""
            SELECT t.*, o.order_code, o.status as order_status,
                   s.show_date, s.start_time, m.title as movie_title,
                   r.name as room_name, c.name as cinema_name
            FROM tickets t
            JOIN orders o ON t.order_id = o.id
            JOIN showtimes s ON t.showtime_id = s.id
            JOIN movies m ON s.movie_id = m.id
            JOIN cinema_rooms r ON s.room_id = r.id
            JOIN cinemas c ON r.cinema_id = c.id
            WHERE t.verification_token = ? OR t.ticket_code = ?
        """, (token_or_code.strip(), token_or_code.strip()))
        ticket = cursor.fetchone()

        if not ticket:
            return {
                "valid": False,
                "result": "INVALID",
                "message": "Vé không tồn tại trong hệ thống!"
            }

        ticket_id = ticket["id"]

        # Check if already used
        if ticket["status"] == "USED":
            cursor.execute("""
                INSERT INTO ticket_scans (ticket_id, scanned_by, result, notes)
                VALUES (?, ?, 'DUPLICATE', 'Phát hiện vé đã qua cửa trước đó!')
            """, (ticket_id, scanned_by))
            conn.commit()

            return {
                "valid": False,
                "result": "DUPLICATE",
                "message": f"CẢNH BÁO: Vé {ticket['ticket_code']} (Ghế {ticket['seat_code']}) đã được sử dụng trước đó!",
                "ticket_code": ticket["ticket_code"],
                "seat_code": ticket["seat_code"],
                "movie_title": ticket["movie_title"]
            }

        if ticket["status"] != "VALID" or ticket["order_status"] != "PAID":
            cursor.execute("""
                INSERT INTO ticket_scans (ticket_id, scanned_by, result, notes)
                VALUES (?, ?, 'INVALID', 'Trạng thái vé hoặc đơn hàng không hợp lệ')
            """, (ticket_id, scanned_by))
            conn.commit()

            return {
                "valid": False,
                "result": "INVALID",
                "message": f"Vé không hợp lệ (Trạng thái: {ticket['status']})"
            }

        # Mark ticket as USED
        cursor.execute("UPDATE tickets SET status = 'USED' WHERE id = ?", (ticket_id,))
        cursor.execute("""
            INSERT INTO ticket_scans (ticket_id, scanned_by, result, notes)
            VALUES (?, ?, 'SUCCESS', 'Check-in thành công tại cổng')
        """, (ticket_id, scanned_by))

        conn.commit()

        return {
            "valid": True,
            "result": "SUCCESS",
            "message": f"HỢP LỆ! Mời quý khách vào phòng chiếu {ticket['room_name']}",
            "ticket_code": ticket["ticket_code"],
            "movie_title": ticket["movie_title"],
            "cinema_name": ticket["cinema_name"],
            "room_name": ticket["room_name"],
            "show_date": ticket["show_date"],
            "start_time": ticket["start_time"],
            "seat_code": ticket["seat_code"],
            "customer_name": ticket["customer_name"]
        }

    except Exception as e:
        conn.rollback()
        raise e
    finally:
        conn.close()
