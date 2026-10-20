package com.example.doan.ui.auth

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.doan.R
import com.example.doan.model.User
import com.example.doan.utils.Constants
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class RegisterActivity : AppCompatActivity() {

    private lateinit var emailEditText: EditText
    private lateinit var nameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var confirmPasswordEditText: EditText
    private lateinit var birthDateEditText: EditText
    private lateinit var maleCheckBox: CheckBox
    private lateinit var femaleCheckBox: CheckBox
    private lateinit var otherCheckBox: CheckBox
    private lateinit var provinceSpinner: Spinner
    private lateinit var registerButton: Button
    private lateinit var database: DatabaseReference
    private lateinit var loginTextView: TextView

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Ánh xạ View
        emailEditText = findViewById(R.id.emailEditText)
        nameEditText = findViewById(R.id.nameEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText)
        birthDateEditText = findViewById(R.id.birthDateEditText)
        maleCheckBox = findViewById(R.id.maleCheckBox)
        femaleCheckBox = findViewById(R.id.femaleCheckBox)
        otherCheckBox = findViewById(R.id.CheckBox)
        provinceSpinner = findViewById(R.id.provinceSpinner)
        registerButton = findViewById(R.id.registerButton)
        loginTextView = findViewById(R.id.loginTextView)

        // Khởi tạo Firebase Database
        database = FirebaseDatabase.getInstance().reference.child(Constants.NODE_USERS)

        val provinces = Constants.PROVINCES

        val adapter = ArrayAdapter(
            this,
            R.layout.spinner_item_white,
            provinces
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        provinceSpinner.adapter = adapter

        loginTextView.setOnClickListener {
            finish()
        }

        // Xử lý đăng ký tài khoản
        registerButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val name = nameEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
            val confirmPassword = confirmPasswordEditText.text.toString().trim()
            val birthDate = birthDateEditText.text.toString().trim()
            val gender = when {
                maleCheckBox.isChecked -> "Nam"
                femaleCheckBox.isChecked -> "Nữ"
                else -> "Khác"
            }
            val province = provinceSpinner.selectedItem.toString()

            if (email.isNotEmpty() && name.isNotEmpty() && password.isNotEmpty() && password == confirmPassword && birthDate.isNotEmpty()) {
                database.orderByChild("email").equalTo(email)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.exists()) {
                                Toast.makeText(applicationContext, "Email đã tồn tại!", Toast.LENGTH_SHORT).show()
                            } else {
                                saveUserToDatabase(name, email, password, birthDate, gender, province)
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Toast.makeText(applicationContext, "Lỗi kết nối Firebase: ${error.message}", Toast.LENGTH_LONG).show()
                        }
                    })
            } else {
                Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin hợp lệ!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveUserToDatabase(name: String, email: String, password: String, birthDate: String, gender: String, province: String) {
        val userId = database.push().key
        val user = User(name, email, password, birthDate, gender, province)

        if (userId != null) {
            database.child(userId).setValue(user)
                .addOnSuccessListener {
                    Toast.makeText(this, "Đăng ký thành công!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Đăng ký thất bại: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }
}
