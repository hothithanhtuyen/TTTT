package com.example.doan.ui.user

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.icu.util.Calendar
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.doan.R
import com.example.doan.utils.Constants
import com.google.firebase.database.*

class BookingActivity : AppCompatActivity() {
    private lateinit var filmTitle: TextView
    private lateinit var filmDetails: TextView
    private lateinit var moviePoster: ImageView
    private lateinit var buyTicketButton: Button
    private lateinit var lich1: Button
    private lateinit var lich2: Button
    private lateinit var lich3: Button
    private lateinit var lich4: Button
    private lateinit var lich5: Button
    private lateinit var buttons: List<Button>
    private lateinit var database: DatabaseReference

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_booking)

        val btnBack: ImageView = findViewById(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }

        filmTitle = findViewById(R.id.filmTitle)
        filmDetails = findViewById(R.id.filmDetails)
        moviePoster = findViewById(R.id.moviePoster)
        buyTicketButton = findViewById(R.id.buyTicketButton)
        lich1 = findViewById(R.id.lich1)
        lich2 = findViewById(R.id.lich2)
        lich3 = findViewById(R.id.lich3)
        lich4 = findViewById(R.id.lich4)
        lich5 = findViewById(R.id.lich5)
        lich1.visibility = View.GONE
        lich2.visibility = View.GONE
        lich3.visibility = View.GONE
        lich4.visibility = View.GONE
        lich5.visibility = View.GONE
        buyTicketButton.visibility = View.GONE

        buttons = listOf(
            findViewById(R.id.buttonCN),
            findViewById(R.id.buttonT2),
            findViewById(R.id.buttonT3),
            findViewById(R.id.buttonT4),
            findViewById(R.id.buttonT5),
            findViewById(R.id.buttonT6),
            findViewById(R.id.buttonT7)
        )

        val cinema = intent.getStringExtra("cinema") ?: "Không xác định"
        val title = intent.getStringExtra("FILM_TITLE")
        val details = intent.getStringExtra("FILM_DETAILS")
        val posterUrl = intent.getStringExtra("FILM_POSTER_URL")
        val province = intent.getStringExtra("province") ?: "Không xác định"
        val iEmail = intent.getStringExtra("userEmail")

        val cnList = mutableListOf<Int>()
        val t2List = mutableListOf<Int>()
        val t3List = mutableListOf<Int>()
        val t4List = mutableListOf<Int>()
        val t5List = mutableListOf<Int>()
        val t6List = mutableListOf<Int>()
        val t7List = mutableListOf<Int>()

        filmTitle.text = title

        com.example.doan.utils.MovieImageUtils.loadMoviePoster(moviePoster, posterUrl, title)

        val daySchedules = listOf(cnList, t2List, t3List, t4List, t5List, t6List, t7List)

        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val selectedButtonIndex = when (dayOfWeek) {
            Calendar.SUNDAY -> 0
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> 0
        }

        fun updateScheduleForDay(index: Int) {
            hideButtons()
            buttons.forEachIndexed { i, btn ->
                if (i == index) {
                    btn.setBackgroundColor(Color.parseColor("#F44336"))
                    btn.setTextColor(Color.WHITE)
                } else {
                    btn.setBackgroundColor(Color.WHITE)
                    btn.setTextColor(Color.BLACK)
                }
            }

            val schedule = daySchedules[index]
            for (slot in schedule) {
                if (slot == 1 || slot == 9) lich1.visibility = View.VISIBLE
                if (slot == 2 || slot == 10 || slot == 14) lich2.visibility = View.VISIBLE
                if (slot == 3 || slot == 13 || slot == 15 || slot == 18) lich3.visibility = View.VISIBLE
                if (slot == 4 || slot == 16 || slot == 19) lich4.visibility = View.VISIBLE
                if (slot == 5 || slot == 20 || slot == 21) lich5.visibility = View.VISIBLE
            }

            // Nếu ngày này chưa có suất chiếu nào, hiển thị mặc định các suất để người dùng luôn đặt được vé
            if (lich1.visibility != View.VISIBLE &&
                lich2.visibility != View.VISIBLE &&
                lich3.visibility != View.VISIBLE &&
                lich4.visibility != View.VISIBLE &&
                lich5.visibility != View.VISIBLE) {
                lich1.visibility = View.VISIBLE
                lich2.visibility = View.VISIBLE
                lich3.visibility = View.VISIBLE
            }
        }

        database = FirebaseDatabase.getInstance().reference.child(Constants.NODE_FILMS)
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                cnList.clear()
                t2List.clear()
                t3List.clear()
                t4List.clear()
                t5List.clear()
                t6List.clear()
                t7List.clear()

                var foundCinema = false
                for (eventSnapshot in snapshot.children) {
                    val name = eventSnapshot.child("name").getValue(String::class.java) ?: ""
                    val cinemasSnapshot = eventSnapshot.child("cinemas")
                    if (name.equals(title, ignoreCase = true)) {
                        for (cinemaSnapshot in cinemasSnapshot.children) {
                            val theater = cinemaSnapshot.child("theater").getValue(String::class.java) ?: ""
                            if (theater.equals(cinema, ignoreCase = true)) {
                                foundCinema = true
                                filmDetails.text = details

                                cinemaSnapshot.child("schedule").child("Chủ Nhật").children.forEach { child ->
                                    child.getValue(Int::class.java)?.let { cnList.add(it) }
                                }
                                cinemaSnapshot.child("schedule").child("Thứ Hai").children.forEach { child ->
                                    child.getValue(Int::class.java)?.let { t2List.add(it) }
                                }
                                cinemaSnapshot.child("schedule").child("Thứ Ba").children.forEach { child ->
                                    child.getValue(Int::class.java)?.let { t3List.add(it) }
                                }
                                cinemaSnapshot.child("schedule").child("Thứ Tư").children.forEach { child ->
                                    child.getValue(Int::class.java)?.let { t4List.add(it) }
                                }
                                cinemaSnapshot.child("schedule").child("Thứ Năm").children.forEach { child ->
                                    child.getValue(Int::class.java)?.let { t5List.add(it) }
                                }
                                cinemaSnapshot.child("schedule").child("Thứ Sáu").children.forEach { child ->
                                    child.getValue(Int::class.java)?.let { t6List.add(it) }
                                }
                                cinemaSnapshot.child("schedule").child("Thứ Bảy").children.forEach { child ->
                                    child.getValue(Int::class.java)?.let { t7List.add(it) }
                                }
                                break
                            }
                        }
                        break
                    }
                }

                // Nếu rạp chưa cấu hình riêng, tự động tạo lịch chiếu mẫu đầy đủ các khung giờ
                if (!foundCinema || (cnList.isEmpty() && t2List.isEmpty() && t3List.isEmpty())) {
                    listOf(cnList, t2List, t3List, t4List, t5List, t6List, t7List).forEach { list ->
                        if (list.isEmpty()) {
                            list.addAll(listOf(1, 2, 3, 4, 5))
                        }
                    }
                    if (filmDetails.text.isNullOrEmpty()) {
                        filmDetails.text = details ?: "Lịch chiếu phim $title tại $cinema"
                    }
                }

                // Tự động kích hoạt hiển thị lịch chiếu cho ngày hôm nay
                updateScheduleForDay(selectedButtonIndex)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@BookingActivity, "Lỗi tải dữ liệu!", Toast.LENGTH_SHORT).show()
                listOf(cnList, t2List, t3List, t4List, t5List, t6List, t7List).forEach { list ->
                    list.addAll(listOf(1, 2, 3, 4, 5))
                }
                updateScheduleForDay(selectedButtonIndex)
            }
        })

        buttons.forEachIndexed { index, button ->
            button.setOnClickListener {
                updateScheduleForDay(index)
            }
        }

        buyTicketButton.setOnClickListener {
            Toast.makeText(this, "Vui lòng chọn suất chiếu bên dưới", Toast.LENGTH_SHORT).show()
        }

        listOf(lich1, lich2, lich3, lich4, lich5).forEach { lichBtn ->
            lichBtn.setOnClickListener {
                val intent = Intent(this, SelectSeatActivity::class.java).apply {
                    putExtra("cinema", cinema)
                    putExtra("userEmail", iEmail)
                    putExtra("province", province)
                    putExtra("FILM_TITLE", title)
                    putExtra("FILM_DETAILS", details)
                    putExtra("FILM_POSTER_URL", posterUrl)
                    putExtra("LICH", lichBtn.text.toString())
                }
                startActivity(intent)
            }
        }
    }

    private fun hideButtons() {
        lich1.visibility = View.GONE
        lich2.visibility = View.GONE
        lich3.visibility = View.GONE
        lich4.visibility = View.GONE
        lich5.visibility = View.GONE
    }
}
