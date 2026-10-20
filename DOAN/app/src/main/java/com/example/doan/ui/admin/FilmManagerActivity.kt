package com.example.doan.ui.admin

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.doan.R
import com.example.doan.adapter.CinemaAdapter
import com.example.doan.model.FilmAdmin
import com.example.doan.utils.Constants
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson

class FilmManagerActivity : AppCompatActivity() {

    private lateinit var editTextFilmName: EditText
    private lateinit var editTextDuration: EditText
    private lateinit var editTextReleaseDate: EditText
    private lateinit var editTextGenre: EditText
    private lateinit var editTextDirector: EditText
    private lateinit var editTextFilmmage: EditText
    private lateinit var buttonAddCinema: Button
    private lateinit var buttonAddFilm: Button
    private lateinit var recyclerViewCinemas: RecyclerView
    private lateinit var cinemaAdapter: CinemaAdapter
    private lateinit var database: DatabaseReference

    private val filmList = ArrayList<FilmAdmin>()

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_film_manager)

        editTextFilmName = findViewById(R.id.editTextFilmName)
        editTextDuration = findViewById(R.id.editTextDuration)
        editTextReleaseDate = findViewById(R.id.editTextReleaseDate)
        editTextGenre = findViewById(R.id.editTextGenre)
        editTextDirector = findViewById(R.id.editTextDirector)
        buttonAddCinema = findViewById(R.id.buttonAddCinema)
        buttonAddFilm = findViewById(R.id.buttonAddfilm)
        recyclerViewCinemas = findViewById(R.id.recyclerViewCinemas)
        editTextFilmmage = findViewById(R.id.editTextFilmmage)

        database = FirebaseDatabase.getInstance().reference.child(Constants.NODE_FILMS)

        cinemaAdapter = CinemaAdapter()
        recyclerViewCinemas.adapter = cinemaAdapter
        recyclerViewCinemas.layoutManager = LinearLayoutManager(this)

        buttonAddCinema.setOnClickListener {
            cinemaAdapter.addCinema()
        }

        buttonAddFilm.setOnClickListener {
            addFilm()
        }
    }

    private fun addFilm() {
        val name = editTextFilmName.text.toString().trim()
        val duration = editTextDuration.text.toString().trim()
        val releaseDate = editTextReleaseDate.text.toString().trim()
        val genre = editTextGenre.text.toString().trim()
        val director = editTextDirector.text.toString().trim()
        val image = editTextFilmmage.text.toString().trim()

        if (name.isEmpty() || duration.isEmpty() || releaseDate.isEmpty() || genre.isEmpty() || director.isEmpty() || image.isEmpty()) {
            Log.e("FilmManager", "Lỗi: Vui lòng nhập đầy đủ thông tin!")
            Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin!", Toast.LENGTH_SHORT).show()
            return
        }

        val film = FilmAdmin(name, image, duration, releaseDate, genre, director, cinemaAdapter.getCinemas())
        val filmId = database.push().key
        if (filmId != null) {
            database.child(filmId).setValue(film)
                .addOnSuccessListener {
                    Toast.makeText(this, "Đã thêm phim!", Toast.LENGTH_SHORT).show()
                    clearInputFields()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Thêm phim thất bại!", Toast.LENGTH_SHORT).show()
                }
        }

        val json = Gson().toJson(filmList)
        Log.d("FilmManager", "JSON Phim: $json")
    }

    private fun clearInputFields() {
        editTextFilmName.setText("")
        editTextDuration.setText("")
        editTextReleaseDate.setText("")
        editTextGenre.setText("")
        editTextDirector.setText("")
        editTextFilmmage.setText("")
    }
}
