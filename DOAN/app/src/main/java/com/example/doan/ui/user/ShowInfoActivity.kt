package com.example.doan.ui.user

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.doan.R

class ShowInfoActivity : AppCompatActivity() {

    private lateinit var titleTextView: TextView
    private lateinit var daysTextView: TextView
    private lateinit var potTextView: TextView
    private lateinit var headerTitle: TextView
    private lateinit var eventImageView: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_show_info)

        val btnBack: ImageView = findViewById(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }

        headerTitle = findViewById(R.id.headerTitle)
        titleTextView = findViewById(R.id.titleTextView)
        daysTextView = findViewById(R.id.daysTextView)
        potTextView = findViewById(R.id.potTextView)
        eventImageView = findViewById(R.id.eventImageView)

        val eventTitle = intent.getStringExtra("eventTitle") ?: "Không có tiêu đề"
        val eventDays = intent.getStringExtra("eventDays") ?: "Không có thông tin"
        val eventImageUrl = intent.getStringExtra("eventImageUrl") ?: ""
        val eventPot = intent.getStringExtra("eventPot") ?: "Không có thông tin"

        titleTextView.text = eventTitle
        daysTextView.text = eventDays
        headerTitle.text = eventTitle
        potTextView.text = eventPot

        Glide.with(this)
            .load(eventImageUrl)
            .placeholder(R.drawable.ic_launcher_background)
            .into(eventImageView)
    }
}
