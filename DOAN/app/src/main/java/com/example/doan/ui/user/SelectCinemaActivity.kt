package com.example.doan.ui.user

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.doan.R
import com.example.doan.utils.Constants

class SelectCinemaActivity : AppCompatActivity() {

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_cinema)

        val btnBack: ImageView = findViewById(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }

        val tvProvince: TextView = findViewById(R.id.tvProvince)
        val listView: ListView = findViewById(R.id.listViewCinemas)

        val title = intent.getStringExtra("FILM_TITLE")
        val details = intent.getStringExtra("FILM_DETAILS")
        val posterUrl = intent.getStringExtra("FILM_POSTER_URL")
        val iEmail = intent.getStringExtra("userEmail")
        val province = intent.getStringExtra("province") ?: "Không xác định"

        tvProvince.text = "Tỉnh: $province"

        val cinemas = Constants.getCinemasForProvince(province)

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, cinemas)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedCinema = cinemas[position]

            if (title == null || details == null || posterUrl == null) {
                val intent = Intent(this, FilmActivity::class.java).apply {
                    putExtra("cinema", selectedCinema)
                    putExtra("province", province)
                    putExtra("userEmail", iEmail)
                }
                startActivity(intent)
            } else {
                val intent = Intent(this, BookingActivity::class.java).apply {
                    putExtra("cinema", selectedCinema)
                    putExtra("province", province)
                    putExtra("userEmail", iEmail)
                    putExtra("FILM_TITLE", title)
                    putExtra("FILM_DETAILS", details)
                    putExtra("FILM_POSTER_URL", posterUrl)
                }
                startActivity(intent)
            }
        }
    }
}
