package com.example.doan.ui.user

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.example.doan.R
import com.example.doan.adapter.MovieAdapter
import com.example.doan.model.Movie
import com.example.doan.utils.Constants
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.database.*

class HomeActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var adapter: MovieAdapter
    private lateinit var backgroundImage: ImageView
    private lateinit var bannerImage: ImageView
    private lateinit var menuButton: ImageButton
    private lateinit var menuLayout: LinearLayout
    private lateinit var dimBackground: View
    private lateinit var bottomNavigation: BottomNavigationView

    private lateinit var btnInfo: LinearLayout
    private lateinit var btnHistory: LinearLayout
    private lateinit var btnCinema: LinearLayout
    private lateinit var btnLogout: LinearLayout

    private lateinit var database: DatabaseReference
    private var isMenuVisible = false
    private val handler = Handler(Looper.getMainLooper())

    private val autoScrollRunnable = object : Runnable {
        override fun run() {
            if (::adapter.isInitialized && adapter.itemCount > 0) {
                val nextItem = (viewPager.currentItem + 1) % adapter.itemCount
                viewPager.setCurrentItem(nextItem, true)
                Glide.with(backgroundImage.context)
                    .load(adapter.getImageResource(nextItem))
                    .placeholder(R.drawable.banner_avatar2)
                    .error(R.drawable.banner_avatar2)
                    .into(backgroundImage)
                handler.postDelayed(this, 4000)
            }
        }
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val userEmail = intent.getStringExtra("userEmail")
        database = FirebaseDatabase.getInstance().reference.child(Constants.NODE_FILMS)

        bindViews()
        setupListeners(userEmail)
        loadBanner()
        fetchMovies(userEmail)
    }

    private fun bindViews() {
        viewPager = findViewById(R.id.movieViewPager)
        backgroundImage = findViewById(R.id.backgroundImage)
        bannerImage = findViewById(R.id.bannerImage)
        menuButton = findViewById(R.id.menuButton)
        menuLayout = findViewById(R.id.menuLayout)
        dimBackground = findViewById(R.id.dimBackground)
        bottomNavigation = findViewById(R.id.bottomNavigation)

        btnInfo = findViewById(R.id.btnInfo)
        btnHistory = findViewById(R.id.btnHistory)
        btnCinema = findViewById(R.id.btnCinema)
        btnLogout = findViewById(R.id.btnLogout)
    }

    private fun setupListeners(userEmail: String?) {
        menuButton.setOnClickListener { toggleMenu() }
        dimBackground.setOnClickListener { toggleMenu() }

        btnInfo.setOnClickListener {
            showToast("Thông tin")
            startActivity(Intent(this, UserInfoActivity::class.java).apply {
                putExtra("userEmail", userEmail)
            })
        }

        btnHistory.setOnClickListener {
            showToast("Vé của tôi")
            startActivity(Intent(this, TicketActivity::class.java).apply {
                putExtra("userEmail", userEmail)
            })
        }

        btnCinema.setOnClickListener {
            showToast("Rạp")
            startActivity(Intent(this, SelectProvinceActivity::class.java).apply {
                putExtra("userEmail", userEmail)
            })
        }

        btnLogout.setOnClickListener {
            showToast("Đăng xuất")
            finish()
        }

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_phim -> {
                    showToast("Phim")
                    startActivity(Intent(this, FilmActivity::class.java).apply {
                        putExtra("userEmail", userEmail)
                    })
                    true
                }
                R.id.nav_mua_ve -> {
                    showToast("Sự kiện")
                    startActivity(Intent(this, EventActivity::class.java))
                    true
                }
                R.id.nav_rap -> {
                    showToast("Rạp")
                    startActivity(Intent(this, SelectProvinceActivity::class.java).apply {
                        putExtra("userEmail", userEmail)
                    })
                    true
                }
                R.id.nav_khuyen_mai -> {
                    showToast("Khuyến mãi")
                    startActivity(Intent(this, PromotionActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun loadBanner() {
        Glide.with(this)
            .load("https://image.tmdb.org/t/p/w780/s16H6tpK2utvwDtzZ8Qy4qm5Emw.jpg")
            .placeholder(R.drawable.banner_avatar2)
            .error(R.drawable.banner_avatar2)
            .into(bannerImage)
    }

    private fun fetchMovies(userEmail: String?) {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val movieList = snapshot.children.mapNotNull {
                    val name = it.child("name").getValue(String::class.java) ?: return@mapNotNull null
                    val url = it.child("url").getValue(String::class.java) ?: return@mapNotNull null
                    val duration = it.child("duration").getValue(String::class.java) ?: return@mapNotNull null
                    Movie(name, duration, url)
                }

                if (movieList.isNotEmpty()) {
                    adapter = MovieAdapter(movieList) { movie ->
                        val intent = Intent(this@HomeActivity, SelectProvinceActivity::class.java).apply {
                            putExtra("userEmail", userEmail)
                            putExtra("province", "")
                            putExtra("FILM_TITLE", movie.title)
                            putExtra("FILM_DETAILS", "${movie.title}\n${movie.info}\n")
                            putExtra("FILM_POSTER_URL", movie.imageResId)
                        }
                        startActivity(intent)
                    }

                    viewPager.adapter = adapter
                    Glide.with(this@HomeActivity).load(adapter.getImageResource(0)).into(backgroundImage)
                    handler.postDelayed(autoScrollRunnable, 3000)
                } else {
                    showToast("Không có dữ liệu phim.", false)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                showToast("Lỗi tải dữ liệu!", false)
            }
        })
    }

    private fun toggleMenu() {
        isMenuVisible = !isMenuVisible
        menuLayout.visibility = if (isMenuVisible) View.VISIBLE else View.GONE
        dimBackground.visibility = if (isMenuVisible) View.VISIBLE else View.GONE
        menuButton.setImageResource(
            if (isMenuVisible) R.drawable.baseline_keyboard_backspace_24
            else R.drawable.baseline_dehaze_24
        )
    }

    private fun showToast(message: String, closeMenu: Boolean = true) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        if (closeMenu && isMenuVisible) toggleMenu()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(autoScrollRunnable)
    }
}
