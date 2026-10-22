package com.example.doan.ui.user

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.doan.BuildConfig
import com.example.doan.R
import com.example.doan.utils.QRCodeUtils
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

class VNPayPaymentActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnCopyCard: Button
    private lateinit var btnShowVNPayQR: Button
    private lateinit var btnOpenBrowser: Button
    private lateinit var btnRefreshStatus: Button
    private lateinit var btnBack: ImageView
    private lateinit var txtOrderSummary: TextView
    private lateinit var txtStatusBadge: TextView

    private val httpClient = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    private val handler = Handler(Looper.getMainLooper())
    private val apiBaseUrl = BuildConfig.VNPAY_API_BASE_URL.trimEnd('/')

    private var title: String = ""
    private var cinema: String = ""
    private var showtime: String = ""
    private var seats: String = ""
    private var email: String = ""
    private var customerName: String = ""
    private var bookingDate: String = ""
    private var province: String = ""
    private var posterUrl: String = ""
    private var amount: Int = 0
    private var paymentId: String = ""
    private var paymentUrl: String = ""
    private var isCompleted = false
    private var isCreatingPayment = false
    private var pollAttempts = 0
    private var pollingRunnable: Runnable? = null

    private val testCardNumber = "9704198526191432198"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vnpay_payment)

        readBookingFromIntent(intent)
        paymentId = savedInstanceState?.getString("PAYMENT_ID")
            ?: paymentIdFrom(intent)
            ?: UUID.randomUUID().toString().replace("-", "").uppercase(Locale.ROOT)
        paymentUrl = savedInstanceState?.getString("PAYMENT_URL").orEmpty()

        btnBack = findViewById(R.id.btnBack)
        webView = findViewById(R.id.webViewVnpay)
        progressBar = findViewById(R.id.progressBar)
        btnCopyCard = findViewById(R.id.btnCopyCard)
        btnShowVNPayQR = findViewById(R.id.btnShowVNPayQR)
        btnOpenBrowser = findViewById(R.id.btnOpenBrowser)
        btnRefreshStatus = findViewById(R.id.btnRefreshStatus)
        txtOrderSummary = findViewById(R.id.txtOrderSummary)
        txtStatusBadge = findViewById(R.id.txtStatusBadge)
        btnShowVNPayQR.isEnabled = false
        btnOpenBrowser.isEnabled = false

        updateOrderSummary()
        setupWebView()

        btnBack.setOnClickListener { confirmExit() }
        btnCopyCard.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("VNPAY sandbox card", testCardNumber))
            Toast.makeText(this, "Đã sao chép số thẻ thử nghiệm NCB", Toast.LENGTH_SHORT).show()
        }
        btnShowVNPayQR.setOnClickListener { showVNPayQRDialog() }
        btnOpenBrowser.setOnClickListener {
            if (paymentUrl.isBlank()) {
                Toast.makeText(this, "Đang tạo liên kết thanh toán", Toast.LENGTH_SHORT).show()
            } else {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(paymentUrl)))
                } catch (_: Exception) {
                    Toast.makeText(this, "Không mở được trình duyệt", Toast.LENGTH_SHORT).show()
                }
            }
        }
        btnRefreshStatus.setOnClickListener {
            if (paymentUrl.isBlank() && hasBookingDetails()) requestBackendPaymentUrl() else pollPaymentStatus()
        }

        if (hasBookingDetails()) {
            if (paymentUrl.isBlank()) {
                requestBackendPaymentUrl()
            } else {
                webView.loadUrl(paymentUrl)
                startStatusPolling(0)
            }
        } else {
            txtStatusBadge.text = "Đang kiểm tra giao dịch"
            startStatusPolling(0)
        }
    }

    private fun paymentIdFrom(source: Intent): String? {
        val extraId = source.getStringExtra("PAYMENT_ID")
        if (!extraId.isNullOrBlank()) return extraId
        val data = source.data ?: return null
        if (data.scheme == "doan" && data.host == "vnpay-return") return data.getQueryParameter("paymentId")
        return null
    }

    private fun readBookingFromIntent(source: Intent) {
        title = source.getStringExtra("FILM_TITLE") ?: ""
        cinema = source.getStringExtra("cinema") ?: ""
        showtime = source.getStringExtra("LICH") ?: ""
        seats = source.getStringExtra("Ghe") ?: ""
        province = source.getStringExtra("province") ?: ""
        posterUrl = source.getStringExtra("FILM_POSTER_URL") ?: ""
        email = source.getStringExtra("userEmail") ?: ""
        customerName = source.getStringExtra("userName") ?: ""
        bookingDate = source.getStringExtra("ngaydat") ?: ""
        amount = source.getIntExtra("totalPrice", 0)
    }

    private fun hasBookingDetails(): Boolean =
        title.isNotBlank() && cinema.isNotBlank() && showtime.isNotBlank() && seats.isNotBlank()

    private fun updateOrderSummary() {
        txtOrderSummary.text = if (title.isBlank()) {
            "Đang tải thông tin giao dịch..."
        } else {
            "Phim: $title | Rạp: $cinema\nSuất: $showtime | Ghế: $seats | Tổng: " +
                String.format(Locale.getDefault(), "%,d", amount) + " VNĐ"
        }
    }

    private fun requestBackendPaymentUrl() {
        if (isCreatingPayment || paymentUrl.isNotBlank()) return
        if (apiBaseUrl.isBlank()) {
            showError("Chưa cấu hình địa chỉ máy chủ thanh toán")
            return
        }

        isCreatingPayment = true
        progressBar.visibility = View.VISIBLE
        txtStatusBadge.text = "Đang tạo giao dịch"
        val payload = JSONObject().apply {
            put("orderId", paymentId)
            put("title", title)
            put("cinema", cinema)
            put("showtime", showtime)
            put("seats", seats.split(",").map { it.trim() }.filter { it.isNotEmpty() })
            put("province", province)
            put("bookingDate", bookingDate)
            put("email", email)
            put("customerName", customerName)
            put("posterUrl", posterUrl)
        }
        val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("$apiBaseUrl/api/payments/vnpay/create")
            .post(body)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    isCreatingPayment = false
                    progressBar.visibility = View.GONE
                    showError("Không kết nối được máy chủ thanh toán. Vui lòng thử lại.")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string().orEmpty()
                runOnUiThread {
                    isCreatingPayment = false
                    progressBar.visibility = View.GONE
                    try {
                        val json = JSONObject(responseBody)
                        if (!response.isSuccessful || !json.optBoolean("success", false)) {
                            showError(json.optString("message", "Không tạo được giao dịch VNPAY"))
                            return@runOnUiThread
                        }
                        paymentUrl = json.getString("paymentUrl")
                        amount = json.optInt("amount", amount)
                        updateOrderSummary()
                        txtStatusBadge.text = "Chờ thanh toán"
                        btnShowVNPayQR.isEnabled = true
                        btnOpenBrowser.isEnabled = true
                        webView.loadUrl(paymentUrl)
                        startStatusPolling(3000)
                    } catch (_: Exception) {
                        showError("Máy chủ trả về dữ liệu thanh toán không hợp lệ")
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
            allowFileAccess = false
            allowContentAccess = false
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
            }

            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.proceed()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                if (uri.scheme == "doan" && uri.host == "vnpay-return") {
                    uri.getQueryParameter("paymentId")?.let { paymentId = it }
                    pollPaymentStatus()
                    return true
                }
                if (uri.scheme != "https" && uri.scheme != "http") {
                    return try {
                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                        true
                    } catch (_: Exception) {
                        true
                    }
                }
                return false
            }
        }
    }

    private fun showVNPayQRDialog() {
        if (paymentUrl.isBlank()) {
            Toast.makeText(this, "Liên kết thanh toán chưa sẵn sàng", Toast.LENGTH_SHORT).show()
            return
        }
        val dialog = AlertDialog.Builder(this).create()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 32, 40, 32)
            gravity = Gravity.CENTER
            setBackgroundColor(android.graphics.Color.parseColor("#1A1D28"))
        }
        val titleView = TextView(this).apply {
            text = "Mã QR thanh toán VNPAY"
            textSize = 17f
            setTextColor(android.graphics.Color.parseColor("#E5A93C"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }
        val description = TextView(this).apply {
            text = "Quét mã để mở đúng giao dịch đã tạo cho đơn vé này."
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#B0B5C6"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        val qrImage = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(520, 520)
            scaleType = ImageView.ScaleType.FIT_CENTER
            QRCodeUtils.generateQRCode(paymentUrl, 520, 520)?.let { setImageBitmap(it) }
        }
        val closeButton = Button(this).apply {
            text = "Đóng"
            setBackgroundColor(android.graphics.Color.parseColor("#2B3042"))
            setTextColor(android.graphics.Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 20 }
            setOnClickListener { dialog.dismiss() }
        }
        layout.addView(titleView)
        layout.addView(description)
        layout.addView(qrImage)
        layout.addView(closeButton)
        dialog.setView(layout)
        dialog.show()
    }

    private fun startStatusPolling(delayMillis: Long) {
        pollingRunnable?.let { handler.removeCallbacks(it) }
        pollAttempts = 0
        pollingRunnable = object : Runnable {
            override fun run() = pollPaymentStatus()
        }
        handler.postDelayed(pollingRunnable!!, delayMillis)
    }

    private fun scheduleNextPoll() {
        if (isCompleted || isFinishing || pollAttempts >= 310) return
        pollingRunnable?.let { handler.postDelayed(it, 3000) }
    }

    private fun pollPaymentStatus() {
        if (isCompleted || paymentId.isBlank() || apiBaseUrl.isBlank()) return
        pollAttempts++
        val request = Request.Builder()
            .url("$apiBaseUrl/api/payments/vnpay/status/$paymentId")
            .get()
            .build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { scheduleNextPoll() }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string().orEmpty()
                runOnUiThread {
                    try {
                        val json = JSONObject(responseBody)
                        if (!response.isSuccessful || !json.optBoolean("success", false)) {
                            scheduleNextPoll()
                            return@runOnUiThread
                        }
                        when (json.optString("status")) {
                            "PAID" -> openIssuedTicket(json)
                            "FAILED" -> {
                                txtStatusBadge.text = "Thanh toán thất bại"
                                scheduleNextPoll()
                            }
                            "EXPIRED" -> {
                                txtStatusBadge.text = "Giao dịch đã hết hạn"
                                scheduleNextPoll()
                            }
                            else -> {
                                txtStatusBadge.text = "Chờ thanh toán"
                                scheduleNextPoll()
                            }
                        }
                    } catch (_: Exception) {
                        scheduleNextPoll()
                    }
                }
            }
        })
    }

    private fun openIssuedTicket(payment: JSONObject) {
        if (isCompleted) return
        isCompleted = true
        pollingRunnable?.let { handler.removeCallbacks(it) }
        txtStatusBadge.text = "ĐÃ XÁC NHẬN"
        txtStatusBadge.setBackgroundColor(android.graphics.Color.parseColor("#4CAF50"))

        val ticketIntent = Intent(this, TicketDetailActivity::class.java).apply {
            putExtra("TICKET_CODE", payment.optString("ticketCode", "CINE-" + paymentId.takeLast(8)))
            putExtra("TICKET_ID", paymentId)
            putExtra("FILM_TITLE", payment.optString("title", title))
            putExtra("cinema", payment.optString("cinema", cinema))
            putExtra("CINEMA", payment.optString("cinema", cinema))
            putExtra("LICH", payment.optString("showtime", showtime))
            putExtra("SHOWTIME", payment.optString("showtime", showtime))
            putExtra("Ghe", payment.optString("seats", seats))
            putExtra("SEAT", payment.optString("seats", seats))
            putExtra("province", payment.optString("province", province))
            putExtra("ngaydat", payment.optString("bookingDate", bookingDate))
            putExtra("totalPrice", payment.optInt("amount", amount))
            putExtra("PRICE", payment.optString("amount", amount.toString()) + " VNĐ")
            putExtra("pttt", "VNPAY Sandbox")
            putExtra("PAYMENT_METHOD", "VNPAY Sandbox")
            putExtra("tt", "Đã xác nhận")
            putExtra("STATUS", "Đã xác nhận")
            putExtra("FILM_POSTER_URL", payment.optString("posterUrl", posterUrl))
            putExtra("userEmail", payment.optString("email", email))
            putExtra("userName", payment.optString("customerName", customerName))
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        Toast.makeText(this, "Thanh toán VNPAY thành công. Vé đã được phát hành.", Toast.LENGTH_LONG).show()
        startActivity(ticketIntent)
        finish()
    }

    private fun showError(message: String) {
        txtStatusBadge.text = "Chưa tạo được giao dịch"
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun confirmExit() {
        AlertDialog.Builder(this)
            .setTitle("Rời màn hình thanh toán?")
            .setMessage("Giao dịch vẫn có thể được cập nhật khi bạn quay lại ứng dụng.")
            .setPositiveButton("Rời đi") { _, _ -> finish() }
            .setNegativeButton("Ở lại", null)
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("PAYMENT_ID", paymentId)
        outState.putString("PAYMENT_URL", paymentUrl)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        paymentIdFrom(intent)?.let {
            paymentId = it
            isCompleted = false
            startStatusPolling(0)
        }
    }

    override fun onDestroy() {
        pollingRunnable?.let { handler.removeCallbacks(it) }
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }
}
