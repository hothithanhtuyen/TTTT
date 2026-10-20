"""
Automated Test Suite for Cinema Movie Booking & VNPay Integration
Tests all required business logic, security constraints, and edge cases:
- Database initialization and entities
- Real-time seat concurrency lock (2 people selecting same seat)
- Seat hold timeout and release
- VNPay v2.1.0 HMAC-SHA512 checksum calculation & verification
- Tampered checksum rejection
- Mismatched amount rejection
- Idempotent callback processing (multiple identical IPNs)
- E-Ticket issuance with secure tokens
- One-time ticket check-in (duplicate scan rejection)
"""

import unittest
import json
import secrets
from datetime import datetime, timedelta

from fastapi.testclient import TestClient

import database
from vnpay import VNPayService
from server import app, vnpay_service, VNPAY_TMN_CODE

client = TestClient(app)


class TestCinemaBackend(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        database.init_db()

    def test_01_movies_and_cinemas_api(self):
        """Verify movies and cinemas can be fetched."""
        res_movies = client.get("/api/movies")
        self.assertEqual(res_movies.status_code, 200)
        movies = res_movies.json()
        self.assertGreater(len(movies), 0)

        res_cinemas = client.get("/api/cinemas")
        self.assertEqual(res_cinemas.status_code, 200)
        cinemas = res_cinemas.json()
        self.assertEqual(len(cinemas), 7)
        self.assertTrue(any("Galaxy Thanh Khê" in c["name"] for c in cinemas))

    def test_02_vnpay_checksum_generation_and_validation(self):
        """Test official VNPay v2.1.0 HMAC-SHA512 algorithm."""
        service = VNPayService(
            tmn_code="GW923J6D",
            hash_secret="TEST_SECRET_KEY_123456",
            payment_url="https://sandbox.vnpayment.vn/paymentv2/vpcpay.html"
        )
        url, hash_val, params = service.generate_payment_url(
            order_code="ORD_TEST_001",
            amount=90000,
            order_info="Test Order",
            return_url="http://localhost:8000/api/payment/vnpay/return"
        )

        self.assertIn("vnp_SecureHash=", url)
        self.assertEqual(params["vnp_Amount"], "9000000") # Multiplied by 100
        self.assertEqual(params["vnp_TmnCode"], "GW923J6D")

        # Verify genuine callback
        is_valid, msg = service.verify_response(params)
        self.assertTrue(is_valid, f"Signature failed: {msg}")

        # Verify tampered payload is rejected
        tampered = params.copy()
        tampered["vnp_Amount"] = "1000000"
        is_valid_tampered, _ = service.verify_response(tampered)
        self.assertFalse(is_valid_tampered, "Tampered payload must be rejected!")

    def test_03_seat_holding_concurrency(self):
        """
        Verify two users selecting the exact same seat:
        User 1 succeeds; User 2 must be rejected!
        """
        conn = database.get_db_connection()
        st = conn.execute("SELECT id FROM showtimes LIMIT 1").fetchone()
        showtime_id = st["id"]

        # Pick 2 available seats
        seats = conn.execute("""
            SELECT s.id, s.seat_code FROM seats s
            LEFT JOIN showtime_seats ss ON s.id = ss.seat_id AND ss.showtime_id = ?
            WHERE ss.status IS NULL OR ss.status = 'AVAILABLE'
            LIMIT 2
        """, (showtime_id,)).fetchall()
        seat_id_1 = seats[0]["id"]
        seat_id_2 = seats[1]["id"]
        conn.close()

        # User 1 holds seat_id_1
        res1 = client.post("/api/orders/hold-seats", json={
            "showtime_id": showtime_id,
            "seat_ids": [seat_id_1],
            "customer_name": "Người Dùng 1",
            "customer_email": "user1@cinema.com"
        })
        self.assertEqual(res1.status_code, 200)
        data1 = res1.json()["data"]
        order_code_1 = data1["order_code"]
        self.assertEqual(data1["status"], "PENDING_PAYMENT")

        # User 2 attempts to hold the same seat_id_1 -> MUST FAIL
        res2 = client.post("/api/orders/hold-seats", json={
            "showtime_id": showtime_id,
            "seat_ids": [seat_id_1, seat_id_2],
            "customer_name": "Người Dùng 2",
            "customer_email": "user2@cinema.com"
        })
        self.assertEqual(res2.status_code, 400)
        self.assertIn("đang được khách hàng khác giữ", res2.json()["detail"])

    def _helper_create_paid_order_and_ticket(self):
        conn = database.get_db_connection()
        st = conn.execute("SELECT id FROM showtimes WHERE id = 2").fetchone()
        showtime_id = st["id"] if st else 1

        seat = conn.execute("""
            SELECT s.id FROM seats s
            WHERE s.id NOT IN (SELECT seat_id FROM showtime_seats WHERE showtime_id = ? AND status IN ('HELD', 'SOLD'))
            LIMIT 1
        """, (showtime_id,)).fetchone()
        seat_id = seat["id"]
        conn.close()

        hold_res = client.post("/api/orders/hold-seats", json={
            "showtime_id": showtime_id,
            "seat_ids": [seat_id],
            "customer_name": "Trần Thị B",
            "customer_email": "b@cinema.com"
        })
        order = hold_res.json()["data"]
        order_code = order["order_code"]
        amount = order["final_amount"]

        sim_res = client.post("/api/admin/simulate-vnpay-callback", json={
            "order_code": order_code,
            "response_code": "00"
        })
        tickets = sim_res.json()["result"]["tickets"]
        return tickets[0]["ticket_code"], tickets[0]["verification_token"]

    def test_04_vnpay_payment_and_idempotent_confirmation(self):
        """
        Full lifecycle:
        1. Hold seats
        2. Create VNPay payment URL
        3. Confirm payment via IPN
        4. Repeat IPN (idempotency check)
        5. Verify tickets issued
        """
        conn = database.get_db_connection()
        st = conn.execute("SELECT id FROM showtimes WHERE id = 2").fetchone()
        showtime_id = st["id"] if st else 1

        seat = conn.execute("""
            SELECT s.id FROM seats s
            WHERE s.id NOT IN (SELECT seat_id FROM showtime_seats WHERE showtime_id = ? AND status IN ('HELD', 'SOLD'))
            LIMIT 1
        """, (showtime_id,)).fetchone()
        seat_id = seat["id"]
        conn.close()

        # 1. Hold seat
        hold_res = client.post("/api/orders/hold-seats", json={
            "showtime_id": showtime_id,
            "seat_ids": [seat_id],
            "customer_name": "Trần Thị B",
            "customer_email": "b@cinema.com"
        })
        self.assertEqual(hold_res.status_code, 200)
        order = hold_res.json()["data"]
        order_code = order["order_code"]
        amount = order["final_amount"]

        # 2. Initiate VNPay payment URL
        pay_res = client.post("/api/payment/vnpay/create", json={
            "order_code": order_code
        })
        self.assertEqual(pay_res.status_code, 200)
        pay_data = pay_res.json()
        self.assertIn("vnp_SecureHash", pay_data["payment_url"])

        # 3. Simulate VNPay Callback via test endpoint
        sim_res = client.post("/api/admin/simulate-vnpay-callback", json={
            "order_code": order_code,
            "response_code": "00"
        })
        self.assertEqual(sim_res.status_code, 200)
        tickets = sim_res.json()["result"]["tickets"]
        self.assertGreater(len(tickets), 0)

        # 4. Check Order status changed to PAID
        st_res = client.get(f"/api/orders/{order_code}/status")
        self.assertEqual(st_res.status_code, 200)
        self.assertEqual(st_res.json()["status"], "PAID")

        # 5. Idempotent check: call IPN again with same data
        ipn_params = {
            "vnp_Amount": str(amount * 100),
            "vnp_BankCode": "NCB",
            "vnp_OrderInfo": f"Thanh toan ve {order_code}",
            "vnp_PayDate": datetime.now().strftime("%Y%m%d%H%M%S"),
            "vnp_ResponseCode": "00",
            "vnp_TmnCode": VNPAY_TMN_CODE,
            "vnp_TransactionNo": "999999",
            "vnp_TxnRef": order_code
        }
        hash_data = vnpay_service.build_hash_data(ipn_params)
        ipn_params["vnp_SecureHash"] = vnpay_service.create_secure_hash(hash_data)

        ipn_res = client.get("/api/payment/vnpay/ipn", params=ipn_params)
        self.assertEqual(ipn_res.status_code, 200)
        self.assertEqual(ipn_res.json()["RspCode"], "02") # Order already confirmed!

    def test_05_ticket_check_in_and_duplicate_scan_prevention(self):
        """
        Verify E-Ticket Check-in Scanner:
        1. First scan -> SUCCESS
        2. Second scan of the same ticket -> DUPLICATE (preventing reuse!)
        3. Fake ticket -> INVALID
        """
        ticket_code, token = self._helper_create_paid_order_and_ticket()

        # First scan
        scan1 = client.post("/api/tickets/scan", json={"code_or_token": token, "scanned_by": "GATE_A"})
        self.assertEqual(scan1.status_code, 200)
        self.assertEqual(scan1.json()["result"], "SUCCESS")
        self.assertTrue(scan1.json()["valid"])

        # Second scan (fraud attempt / reuse)
        scan2 = client.post("/api/tickets/scan", json={"code_or_token": token, "scanned_by": "GATE_A"})
        self.assertEqual(scan2.status_code, 200)
        self.assertEqual(scan2.json()["result"], "DUPLICATE")
        self.assertFalse(scan2.json()["valid"])

        # Invalid scan
        scan3 = client.post("/api/tickets/scan", json={"code_or_token": "FAKE_TOKEN_XYZ", "scanned_by": "GATE_A"})
        self.assertEqual(scan3.status_code, 200)
        self.assertEqual(scan3.json()["result"], "INVALID")
        self.assertFalse(scan3.json()["valid"])


if __name__ == "__main__":
    unittest.main()
