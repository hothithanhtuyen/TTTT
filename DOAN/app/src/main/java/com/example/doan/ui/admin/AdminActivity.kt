package com.example.doan.ui.admin

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.doan.R
import com.example.doan.ui.user.SelectProvinceActivity

class AdminActivity : AppCompatActivity() {

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        val btnManageEvents: Button = findViewById(R.id.btnManageEvents)
        val btnManagePromotions: Button = findViewById(R.id.btnManagePromotions)
        val btnManageTickets: Button = findViewById(R.id.btnManageTickets)
        val btnManageMovies: Button = findViewById(R.id.btnManageMovies)
        val btnManagepay: Button = findViewById(R.id.btnManagepay)
        val btnManageout: Button = findViewById(R.id.btnManageout)
        val btnManagelist: Button = findViewById(R.id.btnManagelist)
        val iEmail = intent.getStringExtra("userEmail")

        btnManageEvents.setOnClickListener {
            startActivity(Intent(this, EventManagementActivity::class.java))
        }

        btnManagePromotions.setOnClickListener {
            startActivity(Intent(this, PromotionManagementActivity::class.java))
        }

        btnManageTickets.setOnClickListener {
            val intent = Intent(this, SelectProvinceActivity::class.java)
            intent.putExtra("userEmail", iEmail)
            startActivity(intent)
        }

        btnManageMovies.setOnClickListener {
            startActivity(Intent(this, FilmManagerActivity::class.java))
        }

        btnManagepay.setOnClickListener {
            startActivity(Intent(this, ConfirmationActivity::class.java))
        }

        btnManagelist.setOnClickListener {
            startActivity(Intent(this, ListFilmActivity::class.java))
        }

        btnManageout.setOnClickListener {
            finish()
        }
    }
}
