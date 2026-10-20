package com.example.doan.ui.user

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.doan.R
import com.example.doan.ui.auth.MainActivity
import com.example.doan.utils.Constants
import com.google.firebase.database.*

class UserInfoActivity : AppCompatActivity() {
    private lateinit var tvUserName: TextView
    private lateinit var tvUserEmail: TextView
    private lateinit var tvBirthDate: TextView
    private lateinit var tvPass: TextView
    private lateinit var rgGender: RadioGroup
    private lateinit var tvProvince: TextView
    private lateinit var btnUpdate: Button
    private lateinit var btnDelete: Button

    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_info)

        val btnBack: ImageView = findViewById(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }

        tvUserName = findViewById(R.id.etName)
        tvUserEmail = findViewById(R.id.etEmail)
        tvBirthDate = findViewById(R.id.etBirthday)
        tvPass = findViewById(R.id.etPassword)
        rgGender = findViewById(R.id.rgGender)
        tvProvince = findViewById(R.id.tvSeat)
        btnUpdate = findViewById(R.id.btnUpdate)
        btnDelete = findViewById(R.id.btnDelete)

        btnUpdate.setOnClickListener {
            updateAccount()
        }

        btnDelete.setOnClickListener {
            confirmDeleteAccount()
        }

        val userEmail = intent.getStringExtra("userEmail")

        if (!userEmail.isNullOrEmpty()) {
            tvUserEmail.text = userEmail
            database = FirebaseDatabase.getInstance().getReference(Constants.NODE_USERS)

            database.orderByChild("email").equalTo(userEmail)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                            for (userSnapshot in snapshot.children) {
                                val name = userSnapshot.child("name").getValue(String::class.java)
                                val birthDate = userSnapshot.child("birthDate").getValue(String::class.java)
                                val password = userSnapshot.child("password").getValue(String::class.java)
                                val gender = userSnapshot.child("gender").getValue(String::class.java)
                                val province = userSnapshot.child("province").getValue(String::class.java)

                                tvUserName.text = name
                                tvBirthDate.text = birthDate
                                tvPass.text = password
                                when (gender) {
                                    "Nam" -> rgGender.check(R.id.rbMale)
                                    "Nữ" -> rgGender.check(R.id.rbFemale)
                                    "Khác" -> rgGender.check(R.id.rbOther)
                                    else -> rgGender.clearCheck()
                                }
                                tvProvince.text = province
                            }
                        } else {
                            Toast.makeText(applicationContext, "Không tìm thấy thông tin!", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Toast.makeText(applicationContext, "Lỗi khi lấy dữ liệu!", Toast.LENGTH_SHORT).show()
                    }
                })
        } else {
            tvUserEmail.text = "Không nhận được email"
        }
    }

    private fun updateAccount() {
        val name = tvUserName.text.toString().removePrefix("Tên: ")
        val birthDate = tvBirthDate.text.toString().removePrefix("Ngày sinh: ")
        val province = tvProvince.text.toString().removePrefix("Tỉnh/Thành phố: ")

        val selectedGenderId = rgGender.checkedRadioButtonId
        val gender = when (selectedGenderId) {
            R.id.rbMale -> "Nam"
            R.id.rbFemale -> "Nữ"
            R.id.rbOther -> "Khác"
            else -> "Không có"
        }

        val userEmail = intent.getStringExtra("userEmail") ?: return

        val userUpdates = mapOf(
            "name" to name,
            "birthDate" to birthDate,
            "gender" to gender,
            "province" to province
        )

        database.orderByChild("email").equalTo(userEmail)
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
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(applicationContext, "Lỗi khi cập nhật dữ liệu!", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun confirmDeleteAccount() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Xác nhận xóa tài khoản")
        builder.setMessage("Bạn có chắc chắn muốn xóa tài khoản không?")

        builder.setPositiveButton("Có") { _, _ ->
            deleteAccount()
        }

        builder.setNegativeButton("Không") { dialog, _ ->
            dialog.dismiss()
        }

        builder.create().show()
    }

    private fun deleteAccount() {
        val userEmail = intent.getStringExtra("userEmail") ?: return

        database.orderByChild("email").equalTo(userEmail)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (userSnapshot in snapshot.children) {
                            userSnapshot.ref.removeValue()
                                .addOnSuccessListener {
                                    Toast.makeText(applicationContext, "Xóa tài khoản thành công!", Toast.LENGTH_SHORT).show()
                                    val intent = Intent(applicationContext, MainActivity::class.java)
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    startActivity(intent)
                                }
                                .addOnFailureListener {
                                    Toast.makeText(applicationContext, "Xóa tài khoản thất bại!", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(applicationContext, "Lỗi khi xóa dữ liệu!", Toast.LENGTH_SHORT).show()
                }
            })
    }
}
