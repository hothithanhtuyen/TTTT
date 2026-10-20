package com.example.doan.ui.user

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.doan.R
import com.example.doan.model.TicketOrder
import com.example.doan.utils.Constants
import com.example.doan.utils.GMailSender
import com.example.doan.utils.QRCodeUtils
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.util.Locale

class QRPaymentActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var txtMovieTitle: TextView
    private lateinit var txtOrderDetails: TextView
    private lateinit var txtTotalAmount: TextView
    private lateinit var txtCountdown: TextView
    private lateinit var imgQRCode: ImageView
    private lateinit var txtBankName: TextView
    private lateinit var txtAccountNumber: TextView
    private lateinit var btnCopyAccount: TextView
    private lateinit var txtAccountHolder: TextView
    private lateinit var txtTransferContent: TextView
    private lateinit var btnCopyContent: TextView
    private lateinit var txtPaymentStatus: TextView
    private lateinit var btnPaidConfirm: Button
    private lateinit var btnAutoSimulate: Button
    private lateinit var btnCancelPayment: Button

    private lateinit var databaseTicker: DatabaseReference
    private var countDownTimer: CountDownTimer? = null

    private var title: String = ""
    private var cinema: String = ""
    private var lich: String = ""
    private var ghe: String = ""
    private var province: String = ""
    private var iEmail: String = ""
    private var tenn: String = ""
    private var formattedDate: String = ""
    private var gia: Int = 42000
    private var posterUrl: String = ""
    private var isCompleted: Boolean = false

    private val bankNumber = "9704198526191432198"
    private val accountHolder = "NGUYEN VAN A"
    private var orderCode = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_payment)

        databaseTicker = FirebaseDatabase.getInstance().reference.child(Constants.NODE_TICKER)

        // Nhận dữ liệu
        title = intent.getStringExtra("FILM_TITLE") ?: ""
        cinema = intent.getStringExtra("cinema") ?: ""
        lich = intent.getStringExtra("LICH") ?: ""
        ghe = intent.getStringExtra("Ghe") ?: ""
        province = intent.getStringExtra("province") ?: ""
        iEmail = intent.getStringExtra("userEmail") ?: ""
        tenn = intent.getStringExtra("userName") ?: (if (iEmail.isNotEmpty()) iEmail.substringBefore("@") else "Khách hàng")
        formattedDate = intent.getStringExtra("ngaydat") ?: ""
        gia = intent.getIntExtra("totalPrice", 42000)
        posterUrl = intent.getStringExtra("FILM_POSTER_URL") ?: ""

        orderCode = (10000..99999).random().toString()

        bindViews()
        setupUI()
        generateAndLoadQR()
        startCountdown()
    }

    private fun bindViews() {
        btnBack = findViewById(R.id.btnBack)
        txtMovieTitle = findViewById(R.id.txtMovieTitle)
        txtOrderDetails = findViewById(R.id.txtOrderDetails)
        txtTotalAmount = findViewById(R.id.txtTotalAmount)
        txtCountdown = findViewById(R.id.txtCountdown)
        imgQRCode = findViewById(R.id.imgQRCode)
        txtBankName = findViewById(R.id.txtBankName)
        txtAccountNumber = findViewById(R.id.txtAccountNumber)
        btnCopyAccount = findViewById(R.id.btnCopyAccount)
        txtAccountHolder = findViewById(R.id.txtAccountHolder)
        txtTransferContent = findViewById(R.id.txtTransferContent)
        btnCopyContent = findViewById(R.id.btnCopyContent)
        txtPaymentStatus = findViewById(R.id.txtPaymentStatus)
        btnPaidConfirm = findViewById(R.id.btnPaidConfirm)
        btnAutoSimulate = findViewById(R.id.btnAutoSimulate)
        btnCancelPayment = findViewById(R.id.btnCancelPayment)
    }

    @SuppressLint("SetTextI18n")
    private fun setupUI() {
        txtMovieTitle.text = title
        txtOrderDetails.text = "Rạp: $cinema\nSuất chiếu: $lich | Ghế: $ghe"
        txtTotalAmount.text = "${String.format(Locale.getDefault(), "%,d", gia)} VNĐ"
        txtAccountNumber.text = bankNumber
        txtAccountHolder.text = accountHolder
        txtTransferContent.text = "VECINE $orderCode"

        btnBack.setOnClickListener { confirmCancel() }
        btnCancelPayment.setOnClickListener { confirmCancel() }

        btnCopyAccount.setOnClickListener {
            copyToClipboard("Số tài khoản", bankNumber)
        }

        btnCopyContent.setOnClickListener {
            copyToClipboard("Nội dung chuyển khoản", "VECINE $orderCode")
        }

        btnPaidConfirm.setOnClickListener {
            processPaymentSuccess()
        }

        btnAutoSimulate.setOnClickListener {
            btnAutoSimulate.isEnabled = false
            btnAutoSimulate.text = "Đang nhận diện giao dịch ngân hàng..."
            txtPaymentStatus.text = "Giao dịch đã được phát hiện, đang xử lý xuất vé..."
            Handler(Looper.getMainLooper()).postDelayed({
                processPaymentSuccess()
            }, 3000)
        }
    }

    private fun generateAndLoadQR() {
        // Nội dung mã QR chuyển khoản VietQR chuẩn
        val qrPayload = "2|99|09704198526191432198|NGUYEN VAN A||0|0|$gia|VECINE $orderCode|transfer_p2p"
        val localQrBitmap = QRCodeUtils.generateQRCode(qrPayload, 512, 512)

        // URL tạo QR VietQR động từ cổng thanh toán mở
        val vietQrUrl = "https://img.vietqr.io/image/NCB-$bankNumber-compact2.png?amount=$gia&addInfo=VECINE%20$orderCode&accountName=NGUYEN%20VAN%20A"

        if (localQrBitmap != null) {
            imgQRCode.setImageBitmap(localQrBitmap)
        }

        Glide.with(this)
            .load(vietQrUrl)
            .placeholder(imgQRCode.drawable)
            .error(imgQRCode.drawable)
            .into(imgQRCode)
    }

    private fun startCountdown() {
        countDownTimer = object : CountDownTimer(600000, 1000) {
            @SuppressLint("SetTextI18n")
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                txtCountdown.text = "Thời gian giữ vé: %02d:%02d".format(minutes, seconds)
            }

            @SuppressLint("SetTextI18n")
            override fun onFinish() {
                txtCountdown.text = "Hết hạn thanh toán!"
                Toast.makeText(this@QRPaymentActivity, "Đơn hàng đã hết hạn thanh toán!", Toast.LENGTH_LONG).show()
                finish()
            }
        }.start()
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Đã sao chép $label: $text", Toast.LENGTH_SHORT).show()
    }

    private fun processPaymentSuccess() {
        if (isCompleted) return
        isCompleted = true
        countDownTimer?.cancel()

        val ticketKey = databaseTicker.push().key ?: "ticket_${System.currentTimeMillis()}"
        val ticketCode = "CINE-DN-%05d".format((1000..99999).random())

        val order = TicketOrder(
            ten = tenn,
            email = iEmail,
            ngaydat = formattedDate,
            phim = title,
            lich = lich,
            ghe = ghe,
            rap = cinema,
            gia = gia.toString(),
            tt = "Đã xác nhận",
            pttt = "VietQR (NCB)"
        )

        databaseTicker.child(ticketKey).setValue(order)
            .addOnSuccessListener {
                // Gửi email xác nhận
                Thread {
                    try {
                        val emailBody = """
                            Chào $tenn,
                            
                            Thanh toán qua mã VietQR thành công!
                            
                            Mã vé: $ticketCode
                            Phim: $title
                            Rạp: $cinema
                            Suất chiếu: $lich
                            Ghế: $ghe
                            Tổng tiền: ${String.format(Locale.getDefault(), "%,d", gia)} VNĐ
                            Trạng thái: Đã xác nhận (Đã thanh toán)
                            
                            Vui lòng mở ứng dụng để xem mã QR soát vé tại rạp.
                            Chúc bạn xem phim vui vẻ!
                        """.trimIndent()
                        val sender = GMailSender("trannhatluan2k1@gmail.com", "oavn gbra wuji tpgs")
                        sender.sendMail(iEmail, "Mã vé xem phim: $title", emailBody)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }.start()

                Toast.makeText(this, "Thanh toán QR thành công! Đã xuất vé điện tử.", Toast.LENGTH_LONG).show()

                // Mở màn hình Chi tiết vé có mã QR
                val intent = Intent(this, TicketDetailActivity::class.java).apply {
                    putExtra("TICKET_CODE", ticketCode)
                    putExtra("FILM_TITLE", title)
                    putExtra("cinema", cinema)
                    putExtra("LICH", lich)
                    putExtra("Ghe", ghe)
                    putExtra("province", province)
                    putExtra("ngaydat", formattedDate)
                    putExtra("totalPrice", gia)
                    putExtra("pttt", "VietQR (NCB)")
                    putExtra("tt", "Đã xác nhận")
                    putExtra("FILM_POSTER_URL", posterUrl)
                    putExtra("userEmail", iEmail)
                    putExtra("userName", tenn)
                }
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { error ->
                isCompleted = false
                Toast.makeText(this, "Lỗi lưu vé: ${error.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun confirmCancel() {
        AlertDialog.Builder(this)
            .setTitle("Hủy thanh toán QR?")
            .setMessage("Ghế của bạn sẽ được giải phóng nếu bạn hủy thanh toán. Bạn có chắc chắn muốn hủy không?")
            .setPositiveButton("Hủy giao dịch") { _, _ ->
                countDownTimer?.cancel()
                finish()
            }
            .setNegativeButton("Tiếp tục thanh toán", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        confirmCancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
