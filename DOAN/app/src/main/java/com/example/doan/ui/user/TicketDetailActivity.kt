package com.example.doan.ui.user

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.doan.R
import com.example.doan.utils.MovieImageUtils
import com.example.doan.utils.QRCodeUtils
import java.util.Locale

class TicketDetailActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var imgMoviePoster: ImageView
    private lateinit var txtMovieName: TextView
    private lateinit var txtCinemaName: TextView
    private lateinit var txtShowDate: TextView
    private lateinit var txtShowTime: TextView
    private lateinit var txtTheaterRoom: TextView
    private lateinit var txtSeatNumbers: TextView
    private lateinit var txtPriceAndMethod: TextView
    private lateinit var txtTicketStatus: TextView
    private lateinit var imgTicketQR: ImageView
    private lateinit var txtTicketCode: TextView
    private lateinit var btnDone: Button
    private lateinit var btnViewAllTickets: Button

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ticket_detail)

        val ticketCode = intent.getStringExtra("TICKET_CODE") ?: "CINE-DN-%05d".format((1000..99999).random())
        val title = intent.getStringExtra("FILM_TITLE") ?: "Phim chưa xác định"
        val cinema = intent.getStringExtra("cinema") ?: "CGV Thanh Khê"
        val lich = intent.getStringExtra("LICH") ?: "19:30"
        val ghe = intent.getStringExtra("Ghe") ?: "E05, E06"
        val formattedDate = intent.getStringExtra("ngaydat") ?: "20/10/2026"
        val gia = intent.getIntExtra("totalPrice", 42000)
        val pttt = intent.getStringExtra("pttt") ?: "VietQR"
        val tt = intent.getStringExtra("tt") ?: "Đã xác nhận"
        val posterUrl = intent.getStringExtra("FILM_POSTER_URL") ?: ""
        val userEmail = intent.getStringExtra("userEmail") ?: ""

        bindViews()

        // Gán dữ liệu lên màn hình
        txtMovieName.text = title
        txtCinemaName.text = cinema
        txtShowDate.text = formattedDate
        txtShowTime.text = lich
        txtTheaterRoom.text = "Cinema 0${(1..4).random()}"
        txtSeatNumbers.text = ghe
        txtPriceAndMethod.text = "${String.format(Locale.getDefault(), "%,d", gia)} đ ($pttt)"
        txtTicketCode.text = "MÃ VÉ: $ticketCode"

        // Trạng thái vé
        if (tt == "Đã xác nhận") {
            txtTicketStatus.text = "✔ ĐÃ XÁC NHẬN"
            txtTicketStatus.setBackgroundColor(Color.parseColor("#1B5E20"))
            txtTicketStatus.setTextColor(Color.parseColor("#A5D6A7"))
        } else {
            txtTicketStatus.text = "⏳ CHỜ XÁC NHẬN"
            txtTicketStatus.setBackgroundColor(Color.parseColor("#E65100"))
            txtTicketStatus.setTextColor(Color.parseColor("#FFE0B2"))
        }

        // Tải ảnh poster với thông minh fallback
        MovieImageUtils.loadMoviePoster(imgMoviePoster, posterUrl, title)

        // Sinh mã QR của vé có đầy đủ dữ liệu xác thực
        val qrPayload = "CINETICKET:$ticketCode|FILM:$title|SEAT:$ghe|SHOWTIME:$lich|DATE:$formattedDate|CINEMA:$cinema"
        val qrBitmap = QRCodeUtils.generateQRCode(qrPayload, 512, 512)
        if (qrBitmap != null) {
            imgTicketQR.setImageBitmap(qrBitmap)
        }

        btnBack.setOnClickListener {
            finish()
        }

        btnDone.setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java).apply {
                putExtra("userEmail", userEmail)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
            finish()
        }

        btnViewAllTickets.setOnClickListener {
            val intent = Intent(this, TicketActivity::class.java).apply {
                putExtra("userEmail", userEmail)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
            finish()
        }
    }

    private fun bindViews() {
        btnBack = findViewById(R.id.btnBack)
        imgMoviePoster = findViewById(R.id.imgMoviePoster)
        txtMovieName = findViewById(R.id.txtMovieName)
        txtCinemaName = findViewById(R.id.txtCinemaName)
        txtShowDate = findViewById(R.id.txtShowDate)
        txtShowTime = findViewById(R.id.txtShowTime)
        txtTheaterRoom = findViewById(R.id.txtTheaterRoom)
        txtSeatNumbers = findViewById(R.id.txtSeatNumbers)
        txtPriceAndMethod = findViewById(R.id.txtPriceAndMethod)
        txtTicketStatus = findViewById(R.id.txtTicketStatus)
        imgTicketQR = findViewById(R.id.imgTicketQR)
        txtTicketCode = findViewById(R.id.txtTicketCode)
        btnDone = findViewById(R.id.btnDone)
        btnViewAllTickets = findViewById(R.id.btnViewAllTickets)
    }
}
