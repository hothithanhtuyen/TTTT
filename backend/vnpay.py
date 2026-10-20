"""
VNPay Payment Gateway Integration Module (VNPay v2.1.0 Protocol)
Provides functions for:
- Building signed VNPay payment URL using HMAC-SHA512
- Verifying Return URL and IPN callbacks
- Handling standard VNPay IPN response structures
"""

import hmac
import hashlib
import urllib.parse
from datetime import datetime
from typing import Dict, Any, Tuple


class VNPayService:
    def __init__(self, tmn_code: str, hash_secret: str, payment_url: str):
        self.tmn_code = tmn_code.strip()
        self.hash_secret = hash_secret.strip()
        self.payment_url = payment_url.strip()

    @staticmethod
    def _encode_val(val: Any) -> str:
        """Encode value according to VNPay specification (space as %20)."""
        return urllib.parse.quote_plus(str(val)).replace("+", "%20")

    def build_hash_data(self, params: Dict[str, Any]) -> str:
        """
        Build raw query string for HMAC-SHA512 signing:
        1. Remove empty values, vnp_SecureHash, and vnp_SecureHashType
        2. Sort keys alphabetically
        3. URL encode values (replacing '+' with '%20')
        4. Join with '&'
        """
        cleaned_params = {
            k: v for k, v in params.items()
            if v is not None and str(v).strip() != "" and k not in ("vnp_SecureHash", "vnp_SecureHashType")
        }
        sorted_items = sorted(cleaned_params.items())
        parts = [f"{k}={self._encode_val(v)}" for k, v in sorted_items]
        return "&".join(parts)

    def create_secure_hash(self, data: str) -> str:
        """Generate HMAC-SHA512 checksum in lowercase hexadecimal."""
        secret_bytes = self.hash_secret.encode("utf-8")
        data_bytes = data.encode("utf-8")
        return hmac.new(secret_bytes, data_bytes, hashlib.sha512).hexdigest()

    def generate_payment_url(
        self,
        order_code: str,
        amount: int,
        order_info: str,
        return_url: str,
        ip_addr: str = "127.0.0.1",
        bank_code: str = None,
        locale: str = "vn"
    ) -> Tuple[str, str, Dict[str, Any]]:
        """
        Create official VNPay 2.1.0 payment URL and payload.
        VNPay amount unit is multiplied by 100 (VND).
        """
        now = datetime.now()
        create_date = now.strftime("%Y%m%d%H%M%S")
        vnp_amount = int(amount) * 100

        params = {
            "vnp_Version": "2.1.0",
            "vnp_Command": "pay",
            "vnp_TmnCode": self.tmn_code,
            "vnp_Amount": str(vnp_amount),
            "vnp_CurrCode": "VND",
            "vnp_TxnRef": str(order_code),
            "vnp_OrderInfo": order_info,
            "vnp_OrderType": "other",
            "vnp_Locale": locale,
            "vnp_ReturnUrl": return_url,
            "vnp_IpAddr": ip_addr or "127.0.0.1",
            "vnp_CreateDate": create_date,
        }

        if bank_code and bank_code.strip():
            params["vnp_BankCode"] = bank_code.strip()

        hash_data = self.build_hash_data(params)
        secure_hash = self.create_secure_hash(hash_data)
        params["vnp_SecureHash"] = secure_hash

        final_url = f"{self.payment_url}?{hash_data}&vnp_SecureHash={secure_hash}"
        return final_url, secure_hash, params

    def verify_response(self, response_params: Dict[str, Any]) -> Tuple[bool, str]:
        """
        Verify incoming callback from Return URL or IPN:
        1. Extract vnp_SecureHash
        2. Recompute HMAC-SHA512 checksum from remaining params
        3. Compare digests securely
        """
        received_hash = response_params.get("vnp_SecureHash", "")
        if not received_hash:
            return False, "Missing vnp_SecureHash"

        hash_data = self.build_hash_data(response_params)
        expected_hash = self.create_secure_hash(hash_data)

        if hmac.compare_digest(expected_hash.lower(), received_hash.lower()):
            return True, "Valid signature"
        return False, f"Signature mismatch (expected {expected_hash[:8]}..., got {received_hash[:8]}...)"
