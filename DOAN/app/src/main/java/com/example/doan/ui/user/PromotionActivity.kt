package com.example.doan.ui.user

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.doan.R
import com.example.doan.adapter.PromotionAdapter
import com.example.doan.model.Promotion
import com.example.doan.utils.Constants
import com.google.firebase.database.*

class PromotionActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var promotionAdapter: PromotionAdapter
    private val promotionList = mutableListOf<Promotion>()
    private lateinit var database: DatabaseReference

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_promotion)

        val btnBack: ImageView = findViewById(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }

        recyclerView = findViewById(R.id.recyclerViewPromotions)
        recyclerView.layoutManager = LinearLayoutManager(this)

        promotionAdapter = PromotionAdapter(promotionList) { promotion ->
            Log.d("PromotionActivity", "Selected promotion: Title: ${promotion.title}, Days: ${promotion.date}, Pot: ${promotion.location}")

            val intent = Intent(this, ShowInfoActivity::class.java).apply {
                putExtra("eventTitle", promotion.title)
                putExtra("eventDays", promotion.date)
                putExtra("eventImageUrl", promotion.imageUrl)
                putExtra("eventPot", promotion.location)
            }
            startActivity(intent)
        }
        recyclerView.adapter = promotionAdapter

        database = FirebaseDatabase.getInstance().reference.child(Constants.NODE_PROMOTION)
        fetchPromotionsFromFirebase()
    }

    private fun fetchPromotionsFromFirebase() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                promotionList.clear()

                for (promotionSnapshot in snapshot.children) {
                    val title = promotionSnapshot.child("title").getValue(String::class.java) ?: ""
                    val days = promotionSnapshot.child("days").getValue(String::class.java) ?: ""
                    val imageUrl = promotionSnapshot.child("image").getValue(String::class.java) ?: ""
                    val pot = promotionSnapshot.child("location").getValue(String::class.java) ?: ""

                    val promotion = Promotion(title, days, imageUrl, pot)
                    promotionList.add(promotion)
                }

                promotionList.reverse()
                promotionAdapter.updateList(promotionList)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@PromotionActivity, "Lỗi tải dữ liệu!", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
