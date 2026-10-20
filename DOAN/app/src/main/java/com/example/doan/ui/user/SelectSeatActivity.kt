package com.example.doan.ui.user

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.doan.R
import com.example.doan.adapter.SeatAdapter
import com.example.doan.utils.Constants
import com.google.firebase.database.*

class SelectSeatActivity : AppCompatActivity() {
    private lateinit var seatAdapter: SeatAdapter
    private lateinit var btnConfirm: Button
    private lateinit var database: DatabaseReference

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_seat)

        val btnBack: ImageView = findViewById(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }

        val tvCinema: TextView = findViewById(R.id.tvCinema)
        val gridView: GridView = findViewById(R.id.gridViewSeats)
        btnConfirm = findViewById(R.id.btnConfirm)

        val cinema = intent.getStringExtra("cinema") ?: "Không xác định"
        val title = intent.getStringExtra("FILM_TITLE")
        val details = intent.getStringExtra("FILM_DETAILS")
        val posterUrl = intent.getStringExtra("FILM_POSTER_URL")
        val lich = intent.getStringExtra("LICH")
        val iEmail = intent.getStringExtra("userEmail")
        val province = intent.getStringExtra("province") ?: "Không xác định"

        var s2 = ""
        tvCinema.text = "Rạp: $cinema"
        val gheList = mutableListOf<String>()

        database = FirebaseDatabase.getInstance().getReference(Constants.NODE_TICKER)
        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    for (userSnapshot in snapshot.children) {
                        val lichs = userSnapshot.child("lich").getValue(String::class.java)
                        val phim = userSnapshot.child("phim").getValue(String::class.java)
                        val rap = userSnapshot.child("rap").getValue(String::class.java)

                        if (lichs == lich && phim == title && cinema == rap) {
                            val ghe = userSnapshot.child("ghe").getValue(String::class.java)
                            if (ghe != null) {
                                val gheArray = ghe.split(",").map { it.trim() }
                                gheList.addAll(gheArray)
                            }
                        }
                    }
                    seatAdapter.disableSeats(gheList)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(applicationContext, "Lỗi khi lấy dữ liệu!", Toast.LENGTH_SHORT).show()
            }
        })

        val seats = mutableListOf<String>()
        for (row in 'A'..'F') {
            for (col in 1..5) {
                seats.add("$row$col")
            }
        }

        seatAdapter = SeatAdapter(this, seats)
        gridView.adapter = seatAdapter

        btnConfirm.visibility = View.GONE

        gridView.setOnItemClickListener { _, _, position, _ ->
            val selectedSeat = seats[position]
            seatAdapter.toggleSeatSelection(selectedSeat)
            val selectedSeats = seatAdapter.getSelectedSeats()
            s2 = selectedSeats.joinToString(", ")

            if (seatAdapter.getSelectedSeats().isNotEmpty()) {
                btnConfirm.visibility = View.VISIBLE
            } else {
                btnConfirm.visibility = View.GONE
            }
        }

        btnConfirm.setOnClickListener {
            val selectedSeats = seatAdapter.getSelectedSeats()
            if (selectedSeats.isEmpty()) {
                Toast.makeText(this, "Vui lòng chọn ít nhất một ghế!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val totalPrice = selectedSeats.size * 20000

            val intent = Intent(this, ConfirmActivity::class.java).apply {
                putExtra("selectedSeats", selectedSeats.joinToString(", "))
                putExtra("totalPrice", totalPrice)
                putExtra("cinema", cinema)
                putExtra("Ghe", s2)
                putExtra("province", province)
                putExtra("FILM_TITLE", title ?: "")
                putExtra("FILM_DETAILS", details ?: "")
                putExtra("LICH", lich)
                putExtra("userEmail", iEmail)
                putExtra("FILM_POSTER_URL", posterUrl ?: "")
            }
            startActivity(intent)
        }
    }
}
