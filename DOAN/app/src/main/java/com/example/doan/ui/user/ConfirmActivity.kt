package com.example.doan.ui.user

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.doan.R
import com.example.doan.model.TicketOrder
import com.example.doan.utils.Constants
import com.example.doan.utils.GMailSender
import com.google.firebase.database.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ConfirmActivity : AppCompatActivity() {
    private lateinit var database: DatabaseReference
    private lateinit var databaseticker: DatabaseReference
    private var tenn: String = ""
    private lateinit var iEmail: String
    private lateinit var formattedDate: String
    private lateinit var title: String
    private lateinit var lich: String
    private lateinit var ghe: String
    private lateinit var cinema: String
    private var province: String = ""
    private var gia: Int = 0
    private var posterUrl: String = ""

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_confirm)

        val btnBack: ImageView = findViewById(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }
        val btnConfirm = findViewById<Button>(R.id.btnConfirm)
        val txtMovieName = findViewById<TextView>(R.id.txtMovieName)
        val txtShowTime = findViewById<TextView>(R.id.txtShowTime)
        val txtCinema = findViewById<TextView>(R.id.txtCinema)
        val txtSeats = findViewById<TextView>(R.id.txtSeats)
        val txtPrice = findViewById<TextView>(R.id.txtPrice)
        val txtEmail = findViewById<TextView>(R.id.txtEmail)
        val txtPhoneNumber = findViewById<TextView>(R.id.txtPhoneNumber)
        val txtMovieTitle = findViewById<TextView>(R.id.txtMovieTitle)
        val txtRoom = findViewById<TextView>(R.id.txtRoom)
        val btnprice = findViewById<TextView>(R.id.btnprice)

        cinema = intent.getStringExtra("cinema") ?: "Không xác định"
        val price = intent.getIntExtra("totalPrice", 0)
        title = intent.getStringExtra("FILM_TITLE") ?: ""
        lich = intent.getStringExtra("LICH") ?: ""
        ghe = intent.getStringExtra("Ghe") ?: ""
        iEmail = intent.getStringExtra("userEmail") ?: ""
        province = intent.getStringExtra("province") ?: "Không xác định"
        posterUrl = intent.getStringExtra("FILM_POSTER_URL") ?: ""
        val currentDate = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        formattedDate = currentDate.format(formatter)

        databaseticker = FirebaseDatabase.getInstance().reference.child(Constants.NODE_TICKER)
        database = FirebaseDatabase.getInstance().getReference(Constants.NODE_USERS)
        database.orderByChild("email").equalTo(iEmail)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (userSnapshot in snapshot.children) {
                            val name = userSnapshot.child("name").getValue(String::class.java)
                            txtMovieName.text = "Tên: $name"
                            tenn = name ?: ""
                        }
                    } else {
                        tenn = if (iEmail.isNotEmpty()) iEmail.substringBefore("@") else "Khách hàng"
                        txtMovieName.text = "Tên: $tenn"
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    tenn = if (iEmail.isNotEmpty()) iEmail.substringBefore("@") else "Khách hàng"
                    txtMovieName.text = "Tên: $tenn"
                }
            })

        txtEmail.text = "Email: $iEmail"
        txtPhoneNumber.text = "Ngày đặt vé: $formattedDate"
        txtMovieTitle.text = "Phim: $title"
        txtRoom.text = "Vị trí ghế: $ghe"
        txtShowTime.text = "Suất chiếu: $lich"
        txtCinema.text = "Rạp: $cinema"
        txtSeats.text = "Địa điểm: $province"

        gia = price + 2000
        txtPrice.text = "Giá vé: 20.000đ"
        btnprice.text = "Tổng tiền: $gia đ"

        btnConfirm.setOnClickListener {
            showPaymentOptions()
        }
    }

    private fun saveUserToDatabase(ten: String, email: String, ngaydat: String, phim: String, lich: String, ghe: String, rap: String, gia: String) {
        val userId = databaseticker.push().key
        val customerName = if (ten.isNotEmpty()) ten else (if (email.isNotEmpty()) email.substringBefore("@") else "Khách hàng")
        val tt = if (customerName == "admin") "Đã xác nhận" else "Chờ xác nhận"
        val order = TicketOrder(customerName, email, ngaydat, phim, lich, ghe, rap, gia, tt)

        if (userId != null) {
            databaseticker.child(userId).setValue(order)
                .addOnSuccessListener {
                    if (customerName != "admin") {
                        val emailBody = """
                        Chào $customerName,
                        
                        Cảm ơn bạn đã đăng ký vé xem phim! Dưới đây là thông tin đặt vé của bạn:
                        
                        Phim: $phim
                        Ngày đặt: $ngaydat
                        Lịch: $lich
                        Ghế: $ghe
                        Rạp: $rap
                        Giá: $gia
                        Trạng thái: $tt
                       
                        Xin cảm ơn!
                        """.trimIndent()
                        sendVerificationEmail(email, emailBody)
                    }

                    Toast.makeText(this, "Đặt vé thành công!", Toast.LENGTH_SHORT).show()
                    val ticketCode = "CINE-DN-%05d".format((1000..99999).random())
                    val intent = Intent(this, TicketDetailActivity::class.java).apply {
                        putExtra("TICKET_CODE", ticketCode)
                        putExtra("FILM_TITLE", phim)
                        putExtra("cinema", rap)
                        putExtra("LICH", lich)
                        putExtra("Ghe", ghe)
                        putExtra("province", province)
                        putExtra("ngaydat", ngaydat)
                        putExtra("totalPrice", gia.toIntOrNull() ?: 42000)
                        putExtra("pttt", "Tiền mặt tại quầy")
                        putExtra("tt", tt)
                        putExtra("FILM_POSTER_URL", posterUrl)
                        putExtra("userEmail", email)
                        putExtra("userName", customerName)
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener { error ->
                    Toast.makeText(this, "Đặt vé thất bại: ${error.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun showPaymentOptions() {
        val dialog = AlertDialog.Builder(this).create()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            gravity = Gravity.CENTER
        }

        val title1 = TextView(this).apply {
            text = "Phương thức thanh toán"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#111111"))
            setPadding(0, 0, 0, 16)
        }

        val subtitle = TextView(this).apply {
            text = "Tổng thanh toán: ${String.format("%,d", gia)} VNĐ\nVui lòng chọn phương thức thanh toán để tiếp tục:"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#555555"))
            setPadding(0, 0, 0, 24)
        }

        val qrBtn = Button(this).apply {
            text = "⚡ Quét mã VietQR / Ngân hàng (Tự động)"
            setBackgroundColor(Color.parseColor("#4CAF50"))
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(20, 15, 20, 15)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 14
            }
            layoutParams = params
            setOnClickListener {
                val intent = Intent(this@ConfirmActivity, QRPaymentActivity::class.java).apply {
                    putExtra("FILM_TITLE", title)
                    putExtra("cinema", cinema)
                    putExtra("LICH", lich)
                    putExtra("Ghe", ghe)
                    putExtra("province", province)
                    putExtra("totalPrice", gia)
                    putExtra("userEmail", iEmail)
                    putExtra("userName", tenn)
                    putExtra("ngaydat", formattedDate)
                    putExtra("FILM_POSTER_URL", posterUrl)
                }
                startActivity(intent)
                dialog.dismiss()
                finish()
            }
        }

        val vnpayBtn = Button(this).apply {
            text = "💳 Thanh toán qua VNPAY (NCB Sandbox)"
            setBackgroundColor(Color.parseColor("#005BAA"))
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(20, 15, 20, 15)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 14
            }
            layoutParams = params
            setOnClickListener {
                val intent = Intent(this@ConfirmActivity, VNPayPaymentActivity::class.java).apply {
                    putExtra("FILM_TITLE", title)
                    putExtra("cinema", cinema)
                    putExtra("LICH", lich)
                    putExtra("Ghe", ghe)
                    putExtra("province", province)
                    putExtra("totalPrice", gia)
                    putExtra("userEmail", iEmail)
                    putExtra("userName", tenn)
                    putExtra("ngaydat", formattedDate)
                    putExtra("FILM_POSTER_URL", posterUrl)
                }
                startActivity(intent)
                dialog.dismiss()
                finish()
            }
        }

        val cashBtn = Button(this).apply {
            text = "💵 Thanh toán tiền mặt tại quầy vé"
            setBackgroundColor(Color.parseColor("#F44336"))
            setTextColor(Color.WHITE)
            textSize = 14f
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 14
            }
            layoutParams = params
            setOnClickListener {
                saveUserToDatabase(tenn, iEmail, formattedDate, title, lich, ghe, cinema, gia.toString())
                dialog.dismiss()
            }
        }

        val cancelBtn = Button(this).apply {
            text = "Quay lại"
            setBackgroundColor(Color.parseColor("#9E9E9E"))
            setTextColor(Color.WHITE)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = params
            setOnClickListener {
                dialog.dismiss()
            }
        }

        layout.addView(title1)
        layout.addView(subtitle)
        layout.addView(qrBtn)
        layout.addView(vnpayBtn)
        layout.addView(cashBtn)
        layout.addView(cancelBtn)

        dialog.setView(layout)
        dialog.show()
    }

    private fun spToPixels(sp: Float): Int {
        return (sp * resources.displayMetrics.scaledDensity).toInt()
    }

    private fun sendVerificationEmail(email: String, info: String) {
        database.orderByChild("email").equalTo(email)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                @SuppressLint("SuspiciousIndentation")
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        Thread {
                            try {
                                val sender = GMailSender(
                                    "trannhatluan2k1@gmail.com",
                                    "oavn gbra wuji tpgs"
                                )
                                sender.sendMail(
                                    email,
                                    "Vé xem film của bạn",
                                    info
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }.start()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(applicationContext, "Lỗi khi lấy dữ liệu!", Toast.LENGTH_SHORT).show()
                }
            })
    }
}
