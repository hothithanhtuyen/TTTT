package com.example.doan.ui.admin

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.doan.R
import com.example.doan.adapter.PromotionAdminAdapter
import com.example.doan.model.Promotion
import com.example.doan.model.PromotionAdmin
import com.example.doan.utils.Constants
import com.google.firebase.database.*

class PromotionManagementActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var promotionAdapter: PromotionAdminAdapter
    private val promotionList = mutableListOf<Promotion>()
    private lateinit var database: DatabaseReference

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.promotion_management_activity)

        recyclerView = findViewById(R.id.recyclerViewPromotions)
        recyclerView.layoutManager = LinearLayoutManager(this)

        database = FirebaseDatabase.getInstance().reference.child(Constants.NODE_PROMOTION)
        loadPromotionData()

        promotionAdapter = PromotionAdminAdapter(promotionList, ::editPromotion, ::deletePromotion)
        recyclerView.adapter = promotionAdapter

        findViewById<Button>(R.id.btnUpdatePromotion).setOnClickListener {
            addPromotion()
        }
    }

    private fun addPromotion() {
        val title = findViewById<EditText>(R.id.edtPromotionTitle).text.toString()
        val date = findViewById<EditText>(R.id.edtPromotionDate).text.toString()
        val location = findViewById<EditText>(R.id.edtPromotionLocation).text.toString()
        val imageUrl = findViewById<EditText>(R.id.edtPromotionImage).text.toString()

        if (title.isNotEmpty() && date.isNotEmpty() && location.isNotEmpty()) {
            savePromotionToDatabase(title, date, location, imageUrl)
            Toast.makeText(this, "Đã thêm khuyến mãi!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun editPromotion(promotion: Promotion) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_event, null)
        val edtTitle = dialogView.findViewById<EditText>(R.id.txtEventTitle)
        val edtDate = dialogView.findViewById<EditText>(R.id.txtEventDate)
        val edtLocation = dialogView.findViewById<EditText>(R.id.txtEventLocation)
        val edtImage = dialogView.findViewById<EditText>(R.id.txtEventImage)

        edtTitle.setText(promotion.title)
        edtDate.setText(promotion.date)
        edtLocation.setText(promotion.location)
        edtImage.setText(promotion.imageUrl)

        AlertDialog.Builder(this)
            .setTitle("Chỉnh sửa Khuyến mãi")
            .setView(dialogView)
            .setPositiveButton("Cập nhật") { _, _ ->
                val updatedEvent = PromotionAdmin(
                    title = edtTitle.text.toString(),
                    days = edtDate.text.toString(),
                    location = edtLocation.text.toString(),
                    image = edtImage.text.toString()
                )
                updatePromotionInDatabase(promotion.title, updatedEvent)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun updatePromotionInDatabase(oldTitle: String, updatedEvent: PromotionAdmin) {
        val eventRef = database.orderByChild("title").equalTo(oldTitle)
        eventRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (eventSnapshot in snapshot.children) {
                    eventSnapshot.ref.setValue(updatedEvent)
                }
                Toast.makeText(this@PromotionManagementActivity, "Khuyến mãi đã được cập nhật!", Toast.LENGTH_SHORT).show()
                loadPromotionData()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@PromotionManagementActivity, "Lỗi khi cập nhật khuyến mãi!", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun deletePromotion(promotion: Promotion) {
        val promotionRef = database.orderByChild("title").equalTo(promotion.title)

        promotionRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (promotionSnapshot in snapshot.children) {
                    promotionSnapshot.ref.removeValue()
                }
                promotionList.remove(promotion)
                promotionAdapter.updateList(promotionList)

                Toast.makeText(this@PromotionManagementActivity, "Đã xóa khuyến mãi!", Toast.LENGTH_SHORT).show()
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@PromotionManagementActivity, "Lỗi khi xóa!", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun savePromotionToDatabase(title: String, days: String, location: String, image: String) {
        val promotionId = database.push().key
        val promotion = PromotionAdmin(title, days, location, image)

        if (promotionId != null) {
            database.child(promotionId).setValue(promotion)
                .addOnSuccessListener {
                    Toast.makeText(this, "Đăng ký thành công!", Toast.LENGTH_SHORT).show()
                    findViewById<EditText>(R.id.edtPromotionTitle).setText("")
                    findViewById<EditText>(R.id.edtPromotionDate).setText("")
                    findViewById<EditText>(R.id.edtPromotionLocation).setText("")
                    findViewById<EditText>(R.id.edtPromotionImage).setText("")
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Đăng ký thất bại!", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun loadPromotionData() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                promotionList.clear()
                for (userSnapshot in snapshot.children) {
                    val title = userSnapshot.child("title").getValue(String::class.java) ?: ""
                    val days = userSnapshot.child("days").getValue(String::class.java) ?: ""
                    val location = userSnapshot.child("location").getValue(String::class.java) ?: ""
                    val imageee = userSnapshot.child("image").getValue(String::class.java) ?: ""
                    val promotion = Promotion(title, days, imageee, location)
                    promotionList.add(promotion)
                }
                promotionList.reverse()
                promotionAdapter.updateList(promotionList)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@PromotionManagementActivity, "Lỗi tải dữ liệu!", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
