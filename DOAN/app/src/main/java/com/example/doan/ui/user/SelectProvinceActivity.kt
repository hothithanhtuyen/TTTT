package com.example.doan.ui.user

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.example.doan.R
import com.example.doan.utils.Constants

class SelectProvinceActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_province)

        val title = intent.getStringExtra("FILM_TITLE")
        val details = intent.getStringExtra("FILM_DETAILS")
        val posterUrl = intent.getStringExtra("FILM_POSTER_URL")
        val iEmail = intent.getStringExtra("userEmail")

        val listView: ListView = findViewById(R.id.listViewProvinces)
        val provinces = Constants.PROVINCES.toList()

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, provinces)
        listView.adapter = adapter

        val btnBack: ImageView = findViewById(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedProvince = provinces[position]
            val intent = Intent(this, SelectCinemaActivity::class.java).apply {
                putExtra("province", selectedProvince)
                putExtra("userEmail", iEmail)
                putExtra("FILM_TITLE", title)
                putExtra("FILM_DETAILS", details)
                putExtra("FILM_POSTER_URL", posterUrl)
            }
            startActivity(intent)
        }
    }
}
