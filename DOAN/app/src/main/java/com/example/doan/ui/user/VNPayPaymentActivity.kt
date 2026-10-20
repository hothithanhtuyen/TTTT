package com.example.doan.ui.user

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.doan.R
import com.example.doan.model.TicketOrder
import com.example.doan.utils.Constants
import com.example.doan.utils.GMailSender
import com.example.doan.utils.QRCodeUtils
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class VNPayPaymentActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnCopyCard: Button
    private lateinit var btnShowVNPayQR: Button
    private lateinit var btnOpenBrowser: Button
    private lateinit var btnDemoSuccess: Button
    private lateinit var btnBack: ImageView
    private lateinit var txtOrderSummary: TextView
    private lateinit var txtStatusBadge: TextView
    private lateinit var databaseTicker: DatabaseReference

    private var title: String = ""
    private var cinema: String = ""
    private var lich: String = ""
    private var ghe: String = ""
    private var iEmail: String = ""
    private var tenn: String = ""
    private var formattedDate: String = ""
    private var gia: Int = 0
    private var province: String = ""
    private var posterUrl: String = ""
    private var orderCode: String = ""
    private var currentPaymentUrl: String = "https://sandbox.vnpayment.vn/tryitnow/Home/CreateOrder"
    private var isCompleted: Boolean = false

    private val testCardNumber = "9704198526191432198"
    private val httpClient = OkHttpClient()
    private val handler = Handler(Looper.getMainLooper())
    private var pollingRunnable: Runnable? = null

    // Backend base URLs: 10.0.2.2 for Android emulator -> localhost on host machine
    private val backendBaseUrl = "http://10.0.2.2:8000"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vnpay_payment)

        databaseTicker = FirebaseDatabase.getInstance().reference.child(Constants.NODE_TICKER)

        // Nhận dữ liệu đơn hàng
        title = intent.getStringExtra("FILM_TITLE") ?: "Phim Điện Ảnh"
        cinema = intent.getStringExtra("cinema") ?: "Galaxy Cinema"
        lich = intent.getStringExtra("LICH") ?: "19:00"
        ghe = intent.getStringExtra("Ghe") ?: "A1"
        province = intent.getStringExtra("province") ?: "Đà Nẵng"
        posterUrl = intent.getStringExtra("FILM_POSTER_URL") ?: ""
        iEmail = intent.getStringExtra("userEmail") ?: "khachhang@cinema.com"
        tenn = intent.getStringExtra("userName") ?: (if (iEmail.isNotEmpty()) iEmail.substringBefore("@") else "Khách hàng")
        formattedDate = intent.getStringExtra("ngaydat") ?: ""
        gia = intent.getIntExtra("totalPrice", 60000)

        // Sinh mã đơn hàng duy nhất
        orderCode = "VNP" + System.currentTimeMillis().toString().takeLast(8)

        // Ánh xạ View
        btnBack = findViewById(R.id.btnBack)
        webView = findViewById(R.id.webViewVnpay)
        progressBar = findViewById(R.id.progressBar)
        btnCopyCard = findViewById(R.id.btnCopyCard)
        btnShowVNPayQR = findViewById(R.id.btnShowVNPayQR)
        btnOpenBrowser = findViewById(R.id.btnOpenBrowser)
        btnDemoSuccess = findViewById(R.id.btnDemoSuccess)
        txtOrderSummary = findViewById(R.id.txtOrderSummary)
        txtStatusBadge = findViewById(R.id.txtStatusBadge)

        txtOrderSummary.text = "Phim: $title | Rạp: $cinema\nSuất: $lich | Ghế: $ghe | Tổng: ${String.format("%,d", gia)} VNĐ"

        btnBack.setOnClickListener {
            confirmExit()
        }

        btnCopyCard.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("VNPay Card", testCardNumber)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Đã sao chép số thẻ test NCB: $testCardNumber", Toast.LENGTH_SHORT).show()
        }

        btnShowVNPayQR.setOnClickListener {
            showVNPayQRDialog()
        }

        btnOpenBrowser.setOnClickListener {
            if (currentPaymentUrl.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentPaymentUrl))
                startActivity(intent)
            }
        }

        btnDemoSuccess.setOnClickListener {
            triggerBackendVerify()
        }

        setupWebView()
        requestBackendPaymentUrl()
        startOrderStatusPolling()
    }

    private fun requestBackendPaymentUrl() {
        // Gửi yêu cầu lên backend tạo URL thanh toán VNPay có ký mã HMAC-SHA512
        val jsonPayload = JSONObject().apply {
            put("order_code", orderCode)
            put("bank_code", "NCB")
        }

        val requestBody = jsonPayload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$backendBaseUrl/api/payment/vnpay/create")
            .post(requestBody)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Fallback nếu server backend chưa chạy trên port 8000
                runOnUiThread {
                    currentPaymentUrl = "https://sandbox.vnpayment.vn/tryitnow/Home/CreateOrder"
                    webView.loadUrl(currentPaymentUrl)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string() ?: ""
                try {
                    val jsonObj = JSONObject(bodyStr)
                    if (jsonObj.optBoolean("success", false)) {
                        val paymentUrl = jsonObj.getString("payment_url")
                        runOnUiThread {
                            currentPaymentUrl = paymentUrl
                            webView.loadUrl(paymentUrl)
                        }
                    } else {
                        runOnUiThread {
                            webView.loadUrl(currentPaymentUrl)
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        webView.loadUrl(currentPaymentUrl)
                    }
                }
            }
        })
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE

                // Nếu đang ở trang TryItNow của VNPay, tự động điền form
                if (url?.contains("tryitnow") == true) {
                    val jsFill = """
                        (function() {
                            var amountInput = document.getElementById('Amount');
                            if (amountInput) amountInput.value = '$gia';
                            var descInput = document.getElementById('OrderDescription');
                            if (descInput) descInput.value = 'Thanh toan ve xem phim: $title';
                            var bankSelect = document.getElementById('bankcode');
                            if (bankSelect) bankSelect.value = 'NCB';
                        })();
                    """.trimIndent()
                    view?.evaluateJavascript(jsFill, null)
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()

                // Kiểm tra mã phản hồi thành công từ VNPay
                if (url.contains("vnp_ResponseCode=00") || url.contains("vnp_TransactionStatus=00")) {
                    processPaymentSuccess()
                    return true
                }
                return false
            }
        }
    }

    private fun showVNPayQRDialog() {
        val dialog = AlertDialog.Builder(this).create()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 32, 40, 32)
            gravity = Gravity.CENTER
            setBackgroundColor(android.graphics.Color.parseColor("#1A1D28"))
        }

        val titleTv = TextView(this).apply {
            text = "Mã QR Mở Trang VNPAY Sandbox"
            textSize = 17f
            setTextColor(android.graphics.Color.parseColor("#E5A93C"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }

        val descTv = TextView(this).apply {
            text = "Quét bằng Camera để mở cổng thanh toán VNPay trên điện thoại khác.\n(Lưu ý: Đây là mã QR liên kết thanh toán, không phải VietQR chuyển khoản cá nhân)"
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#B0B5C6"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }

        val qrImageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(520, 520)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val qrBitmap = QRCodeUtils.generateQRCode(currentPaymentUrl, 520, 520)
            if (qrBitmap != null) {
                setImageBitmap(qrBitmap)
            }
        }

        val closeBtn = Button(this).apply {
            text = "Đóng"
            setBackgroundColor(android.graphics.Color.parseColor("#2B3042"))
            setTextColor(android.graphics.Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 20
            }
            setOnClickListener { dialog.dismiss() }
        }

        layout.addView(titleTv)
        layout.addView(descTv)
        layout.addView(qrImageView)
        layout.addView(closeBtn)

        dialog.setView(layout)
        dialog.show()
    }

    private fun startOrderStatusPolling() {
        pollingRunnable = object : Runnable {
            override fun run() {
                if (isCompleted || isFinishing) return

                val request = Request.Builder()
                    .url("$backendBaseUrl/api/orders/$orderCode/status")
                    .get()
                    .build()

                httpClient.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        // Không kết nối được backend, thử lại sau 3s
                        handler.postDelayed(pollingRunnable!!, 3000)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val body = response.body?.string() ?: ""
                        try {
                            val json = JSONObject(body)
                            val status = json.optString("status", "")
                            if (status == "PAID") {
                                runOnUiThread {
                                    txtStatusBadge.text = "ĐÃ XÁC NHẬN"
                                    txtStatusBadge.setBackgroundColor(android.graphics.Color.parseColor("#4CAF50"))
                                    processPaymentSuccess()
                                }
                                return
                            }
                        } catch (_: Exception) {}

                        handler.postDelayed(pollingRunnable!!, 3000)
                    }
                })
            }
        }
        handler.postDelayed(pollingRunnable!!, 3000)
    }

    private fun triggerBackendVerify() {
        progressBar.visibility = View.VISIBLE
        val jsonPayload = JSONObject().apply {
            put("order_code", orderCode)
            put("response_code", "00")
            put("bank_code", "NCB")
        }

        val requestBody = jsonPayload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$backendBaseUrl/api/admin/simulate-vnpay-callback")
            .post(requestBody)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    // Fallback xử lý cục bộ
                    processPaymentSuccess()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    processPaymentSuccess()
                }
            }
        })
    }

    private fun processPaymentSuccess() {
        if (isCompleted) return
        isCompleted = true
        pollingRunnable?.let { handler.removeCallbacks(it) }

        // Lưu vé vào Firebase RTDB
        val ticketId = databaseTicker.push().key ?: System.currentTimeMillis().toString()
        val ticket = TicketOrder(
            rap = cinema,
            email = iEmail,
            ghe = ghe,
            gia = "$gia VNĐ",
            lich = lich,
            ngaydat = formattedDate,
            phim = title,
            pttt = "VNPay Sandbox (NCB)",
            ten = tenn,
            tt = "Đã xác nhận"
        )

        databaseTicker.child(ticketId).setValue(ticket).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // Gửi email xác nhận tự động
                sendEmailConfirmation(ticket)

                Toast.makeText(this, "🎉 Thanh toán VNPay thành công! Đã phát hành vé điện tử.", Toast.LENGTH_LONG).show()

                // Chuyển thẳng sang xem Cuống vé điện tử (TicketDetailActivity)
                val intent = Intent(this, TicketDetailActivity::class.java).apply {
                    putExtra("TICKET_ID", ticketId)
                    putExtra("FILM_TITLE", title)
                    putExtra("CINEMA", cinema)
                    putExtra("SHOWTIME", lich)
                    putExtra("SEAT", ghe)
                    putExtra("PRICE", "$gia VNĐ")
                    putExtra("ORDER_DATE", formattedDate)
                    putExtra("STATUS", "Đã xác nhận")
                    putExtra("PAYMENT_METHOD", "VNPay Sandbox (NCB)")
                    putExtra("FILM_POSTER_URL", posterUrl)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "Lỗi lưu vé: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun sendEmailConfirmation(ticket: TicketOrder) {
        val subject = "Xác nhận đặt vé xem phim thành công qua VNPAY - $title"
        val message = """
            Xin chào ${ticket.ten},
            
            Bạn đã thanh toán thành công qua cổng VNPAY Sandbox cho đơn vé:
            --------------------------------------------------
            Phim: ${ticket.phim}
            Rạp: ${ticket.rap}
            Suất chiếu: ${ticket.lich}
            Ghế: ${ticket.ghe}
            Tổng tiền: ${ticket.gia}
            Phương thức: ${ticket.pttt}
            Trạng thái: ${ticket.tt}
            Mã đơn: $orderCode
            --------------------------------------------------
            Mã QR check-in tại quầy: CINETICKET:$orderCode|FILM:${ticket.phim}|SEAT:${ticket.ghe}
            
            Cảm ơn bạn đã lựa chọn dịch vụ của chúng tôi!
        """.trimIndent()

        Thread {
            try {
                val sender = GMailSender("anhnguyenthac2004@gmail.com", "vbsb unct bzvy lkvt")
                sender.sendMail(ticket.email, subject, message)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun confirmExit() {
        AlertDialog.Builder(this)
            .setTitle("Hủy thanh toán?")
            .setMessage("Giao dịch thanh toán VNPAY chưa hoàn tất. Bạn có chắc chắn muốn thoát?")
            .setPositiveButton("Thoát") { _, _ ->
                pollingRunnable?.let { handler.removeCallbacks(it) }
                finish()
            }
            .setNegativeButton("Ở lại", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingRunnable?.let { handler.removeCallbacks(it) }
    }
}
