package com.example.doan.ui.admin

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.doan.R
import com.example.doan.model.Film
import com.example.doan.utils.Constants
import com.google.firebase.database.*

class ListFilmActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private val filmList = mutableListOf<Film>()
    private lateinit var filmAdapter: AdminFilmListAdapter
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.film)
        recyclerView = findViewById(R.id.recyclerView)

        filmAdapter = AdminFilmListAdapter(filmList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = filmAdapter
        database = FirebaseDatabase.getInstance().reference.child(Constants.NODE_FILMS)
        fetchEventsFromFirebase()
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
                    val film = Film(name, "Thời gian: $time", "Khởi chiếu: $days", "Thể loại: $type", "Đạo diễn: $directors", imageUrl)

                    filmList.add(film)
                }
                filmList.reverse()
                filmAdapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@ListFilmActivity, "Lỗi tải dữ liệu!", Toast.LENGTH_SHORT).show()
            }
        })
    }
}

class AdminFilmListAdapter(private val films: List<Film>) : RecyclerView.Adapter<AdminFilmListAdapter.FilmViewHolder>() {
    private lateinit var database: DatabaseReference

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilmViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.adapterfilm, parent, false)
        return FilmViewHolder(view)
    }

    override fun onBindViewHolder(holder: FilmViewHolder, position: Int) {
        val film = films[position]
        holder.bind(film)
        holder.itemView.findViewById<Button>(R.id.buyTicketButton).apply {
            text = "Xóa phim"
            setOnClickListener {
                database = FirebaseDatabase.getInstance().reference.child(Constants.NODE_FILMS)
                val eventRef = database.orderByChild("name").equalTo(film.title)

                eventRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    @SuppressLint("NotifyDataSetChanged")
                    override fun onDataChange(snapshot: DataSnapshot) {
                        for (eventSnapshot in snapshot.children) {
                            eventSnapshot.ref.removeValue()
                        }
                        (films as MutableList).remove(film)
                        notifyDataSetChanged()
                    }

                    override fun onCancelled(error: DatabaseError) {
                        // Xử lý lỗi nếu cần
                    }
                })
            }
        }
    }

    override fun getItemCount(): Int = films.size

    class FilmViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val moviePoster: ImageView = itemView.findViewById(R.id.moviePoster)
        private val movieTitle: TextView = itemView.findViewById(R.id.movieTitle)
        private val movieDuration: TextView = itemView.findViewById(R.id.movieDuration)
        private val movieReleaseDate: TextView = itemView.findViewById(R.id.movieReleaseDate)
        private val movieGenre: TextView = itemView.findViewById(R.id.movieGenre)
        private val movieDirector: TextView = itemView.findViewById(R.id.movieDirector)

        fun bind(film: Film) {
            movieTitle.text = film.title
            movieDuration.text = film.duration
            movieReleaseDate.text = film.releaseDate
            movieGenre.text = film.genre
            movieDirector.text = film.director

            Glide.with(itemView.context)
                .load(film.posterUrl)
                .into(moviePoster)
        }
    }
}
