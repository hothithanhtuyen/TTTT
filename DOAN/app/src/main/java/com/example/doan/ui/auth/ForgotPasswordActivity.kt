package com.example.doan.ui.auth

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.doan.R
import com.example.doan.utils.Constants
import com.example.doan.utils.GMailSender
import com.google.firebase.database.*

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var emailEditText: EditText
    private lateinit var emailma: EditText
    private lateinit var emailmk: EditText
    private lateinit var emailmkl: EditText
    private lateinit var resetPasswordButton: Button
    private lateinit var resetma: Button
    private lateinit var ma: Button
    private lateinit var loginTextView: TextView
    private lateinit var database: DatabaseReference
    private lateinit var thongbao: TextView

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        emailEditText = findViewById(R.id.emailEditText)
        resetPasswordButton = findViewById(R.id.resetPasswordButton)
        emailma = findViewById(R.id.emailma)
        resetma = findViewById(R.id.resetma)
        ma = findViewById(R.id.ma)
        thongbao = findViewById(R.id.thongbao)
        emailmk = findViewById(R.id.emailmk)
        emailmkl = findViewById(R.id.emailmkl)
        loginTextView = findViewById(R.id.loginTextView)
        var maXacNhan = taoMaXacNhan()
        database = FirebaseDatabase.getInstance().getReference(Constants.NODE_USERS)
        var email = emailEditText.text.toString()

        resetPasswordButton.setOnClickListener {
            email = emailEditText.text.toString()

            if (email.isNotEmpty()) {
                database.orderByChild("email").equalTo(email)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        @SuppressLint("SuspiciousIndentation")
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.exists()) {
                                thongbao.text = ""
                                Thread {
                                    try {
                                        val sender = GMailSender(
                                            "trannhatluan2k1@gmail.com",
                                            "oavn gbra wuji tpgs"
                                        )
                                        sender.sendMail(
                                            email,
                                            "Mã xác nhận của bạn",
                                            maXacNhan
                                        )
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }.start()
                                emailEditText.visibility = View.GONE
                                resetPasswordButton.visibility = View.GONE
                                emailma.visibility = View.VISIBLE
                                resetma.visibility = View.VISIBLE
                                ma.visibility = View.VISIBLE
                                emailmkl.visibility = View.VISIBLE
                                emailmk.visibility = View.VISIBLE
                            } else {
                                thongbao.text = "Tài khoản không tồn tại"
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Toast.makeText(applicationContext, "Lỗi khi lấy dữ liệu!", Toast.LENGTH_SHORT).show()
                        }
                    })
            } else {
                thongbao.text = "Nhập thông tin"
            }
        }

        resetma.setOnClickListener {
            maXacNhan = taoMaXacNhan()
            Thread {
                try {
                    val sender = GMailSender(
                        "trannhatluan2k1@gmail.com",
                        "oavn gbra wuji tpgs"
                    )
                    sender.sendMail(
                        email,
                        "Mã xác nhận của bạn",
                        maXacNhan
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()
        }

        ma.setOnClickListener {
            val s1 = emailmk.text.toString()
            val s2 = emailmkl.text.toString()
            val s3 = emailma.text.toString()

            if (s1.isNotEmpty() && s2.isNotEmpty() && s3.isNotEmpty() && s3 == maXacNhan && s1 == s2) {
                Log.d("CinemaInfo", "OKELA")
                val userUpdates = mapOf(
                    "password" to s1
                )
                database.orderByChild("email").equalTo(email)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.exists()) {
                                for (userSnapshot in snapshot.children) {
                                    userSnapshot.ref.updateChildren(userUpdates)
                                        .addOnSuccessListener {
                                            Toast.makeText(applicationContext, "Cập nhật thông tin thành công!", Toast.LENGTH_SHORT).show()
                                        }
                                        .addOnFailureListener {
                                            Toast.makeText(applicationContext, "Cập nhật thất bại!", Toast.LENGTH_SHORT).show()
                                        }
                                }
                                finish()
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Toast.makeText(applicationContext, "Lỗi khi cập nhật dữ liệu!", Toast.LENGTH_SHORT).show()
                        }
                    })
            }
        }

        loginTextView.setOnClickListener {
            finish()
        }
    }

    private fun taoMaXacNhan(length: Int = 6): String {
        val kyTu = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..length)
            .map { kyTu.random() }
            .joinToString("")
    }
}
