package com.example.doan.ui.user

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.icu.text.SimpleDateFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.doan.R
import com.example.doan.adapter.FilmAdapter
import com.example.doan.adapter.PosterAdapter
import com.example.doan.model.Film
import com.example.doan.utils.Constants
import com.google.firebase.database.*
import java.util.Date

class FilmActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var filmAdapter: FilmAdapter
    private lateinit var viewPager: ViewPager2
    private lateinit var filma: Button
    private lateinit var filmb: Button
    private lateinit var underline1: View
    private lateinit var underline2: View
    private val filmList = mutableListOf<Film>()
    private lateinit var posterAdapter: PosterAdapter
    private val handler = Handler(Looper.getMainLooper())
    private var currentPage = 0
    private val delay: Long = 3000
    private lateinit var database: DatabaseReference

    private var userEmail: String = ""
    private var province: String = "Không xác định"
    private var cinema: String = "Không xác định"

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_film)

        viewPager = findViewById(R.id.viewPager)
        recyclerView = findViewById(R.id.recyclerView)
        filma = findViewById(R.id.button1)
        filmb = findViewById(R.id.button2)
        underline1 = findViewById(R.id.underline1)
        underline2 = findViewById(R.id.underline2)

        userEmail = intent.getStringExtra("userEmail") ?: ""
        province = intent.getStringExtra("province") ?: "Không xác định"
        cinema = intent.getStringExtra("cinema") ?: "Không xác định"

        filmAdapter = FilmAdapter(filmList, cinema, province, userEmail)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = filmAdapter

        val btnBack: ImageView = findViewById(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }

        database = FirebaseDatabase.getInstance().reference.child(Constants.NODE_FILMS)

        val posterImages = listOf(
            "https://image.tmdb.org/t/p/w780/s16H6tpK2utvwDtzZ8Qy4qm5Emw.jpg",
            "https://media.themoviedb.org/t/p/w780/eZ239CUp1d6OryZEBPnO2n87gMG.jpg",
            "https://media.themoviedb.org/t/p/w780/by8z9Fe8y7p4jo2YlW2SZDnptyT.jpg"
        )

        posterAdapter = PosterAdapter(posterImages)
        viewPager.adapter = posterAdapter
        startAutoScroll()

        fetchEventsFromFirebase()

        filma.setOnClickListener {
            filmList.clear()
            fetchEventsFromFirebase()
            updateTabUI(isFirstSelected = true)
        }

        filmb.setOnClickListener {
            filmList.clear()
            fetchEventsFromFirebase1()
            updateTabUI(isFirstSelected = false)
        }

        updateTabUI(isFirstSelected = true)
    }

    private fun updateTabUI(isFirstSelected: Boolean) {
        if (isFirstSelected) {
            underline1.visibility = View.VISIBLE
            underline2.visibility = View.INVISIBLE
            filma.setTypeface(null, Typeface.BOLD)
            filmb.setTypeface(null, Typeface.NORMAL)
        } else {
            underline1.visibility = View.INVISIBLE
            underline2.visibility = View.VISIBLE
            filma.setTypeface(null, Typeface.NORMAL)
            filmb.setTypeface(null, Typeface.BOLD)
        }
    }

    private fun startAutoScroll() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                currentPage = (currentPage + 1) % posterAdapter.itemCount
                viewPager.setCurrentItem(currentPage, true)
                handler.postDelayed(this, delay)
            }
        }, delay)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    private fun fetchEventsFromFirebase() {
        database.addValueEventListener(object : ValueEventListener {
            @SuppressLint("NotifyDataSetChanged")
            override fun onDataChange(snapshot: DataSnapshot) {
                filmList.clear()
                for (eventSnapshot in snapshot.children) {
                    val name = eventSnapshot.child("name").getValue(String::class.java) ?: ""
                    val days = eventSnapshot.child("releaseDate").getValue(String::class.java) ?: ""
                    val imageUrl = eventSnapshot.child("url").getValue(String::class.java) ?: ""
                    val time = eventSnapshot.child("duration").getValue(String::class.java) ?: ""
                    val type = eventSnapshot.child("genre").getValue(String::class.java) ?: ""
                    val directors = eventSnapshot.child("director").getValue(String::class.java) ?: ""
                    val film = Film(name, "Thời lượng: $time", "Khởi chiếu: $days", "Thể loại: $type", "Đạo diễn: $directors", imageUrl)

                    try {
                        val format = SimpleDateFormat("dd/MM/yyyy")
                        val dateObj = format.parse(days)
                        val now = Date()
                        if (dateObj != null && dateObj.before(now)) {
                            filmList.add(film)
                        }
                    } catch (e: Exception) {
                        filmList.add(film)
                    }
                }

                filmList.reverse()
                filmAdapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@FilmActivity, "Lỗi tải dữ liệu!", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun fetchEventsFromFirebase1() {
        database.addValueEventListener(object : ValueEventListener {
            @SuppressLint("NotifyDataSetChanged")
            override fun onDataChange(snapshot: DataSnapshot) {
                filmList.clear()
                for (eventSnapshot in snapshot.children) {
                    val name = eventSnapshot.child("name").getValue(String::class.java) ?: ""
                    val days = eventSnapshot.child("releaseDate").getValue(String::class.java) ?: ""
                    val imageUrl = eventSnapshot.child("url").getValue(String::class.java) ?: ""
                    val time = eventSnapshot.child("duration").getValue(String::class.java) ?: ""
                    val type = eventSnapshot.child("genre").getValue(String::class.java) ?: ""
                    val directors = eventSnapshot.child("director").getValue(String::class.java) ?: ""
                    val film = Film(name, "Thời lượng: $time", "Khởi chiếu: $days", "Thể loại: $type", "Đạo diễn: $directors", imageUrl)

                    try {
                        val format = SimpleDateFormat("dd/MM/yyyy")
                        val dateObj = format.parse(days)
                        val now = Date()
                        if (dateObj != null && dateObj.after(now)) {
                            filmList.add(film)
                        }
                    } catch (e: Exception) {
                        filmList.add(film)
                    }
                }

                filmList.reverse()
                filmAdapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@FilmActivity, "Lỗi tải dữ liệu!", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
