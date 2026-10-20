package com.example.doan.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.doan.R
import com.example.doan.model.AppUser
import com.example.doan.ui.admin.AdminActivity
import com.example.doan.ui.user.HomeActivity
import com.example.doan.utils.Constants
import com.google.firebase.database.*

class MainActivity : AppCompatActivity() {

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var registerTextView: TextView
    private lateinit var forgotPasswordTextView: TextView
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Ánh xạ View
        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)
        registerTextView = findViewById(R.id.registerTextView)
        forgotPasswordTextView = findViewById(R.id.forgotPasswordTextView)

        // Khởi tạo Firebase Database
        database = FirebaseDatabase.getInstance().reference.child(Constants.NODE_USERS)

        // Xử lý đăng nhập
        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                loginUser(email, password)
            } else {
                Toast.makeText(this, "Vui lòng nhập email và mật khẩu", Toast.LENGTH_SHORT).show()
            }
        }

        // Chuyển đến trang Đăng ký
        registerTextView.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Chuyển đến trang Quên mật khẩu
        forgotPasswordTextView.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }
    }

    private fun loginUser(email: String, password: String) {
        // Kiểm tra tài khoản Quản trị viên (Admin)
        if (email == "admin" && password == "admin123") {
            val intent = Intent(this@MainActivity, AdminActivity::class.java)
            intent.putExtra("userEmail", email)
            startActivity(intent)
            passwordEditText.setText("")
            return
        }

        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var isUserFound = false
                for (userSnapshot in snapshot.children) {
                    val user = userSnapshot.getValue(AppUser::class.java)
                    if (user != null && user.email == email && user.password == password) {
                        isUserFound = true
                        val intent = Intent(this@MainActivity, HomeActivity::class.java)
                        intent.putExtra("userEmail", email)
                        startActivity(intent)
                        passwordEditText.setText("")
                        break
                    }
                }
                if (!isUserFound) {
                    Toast.makeText(this@MainActivity, "Sai email hoặc mật khẩu", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, "Lỗi: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
