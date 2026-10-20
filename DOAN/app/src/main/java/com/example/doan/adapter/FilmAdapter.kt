package com.example.doan.adapter

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.doan.R
import com.example.doan.model.Film
import com.example.doan.ui.user.BookingActivity
import com.example.doan.ui.user.SelectProvinceActivity

class FilmAdapter(
    private val films: List<Film>,
    private val cinemaName: String = "Không xác định",
    private val provinceName: String = "Không xác định",
    private val userEmail: String = ""
) : RecyclerView.Adapter<FilmAdapter.FilmViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilmViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.film_list, parent, false)
        return FilmViewHolder(view)
    }

    override fun onBindViewHolder(holder: FilmViewHolder, position: Int) {
        val film = films[position]
        holder.bind(film)
        holder.itemView.findViewById<Button>(R.id.buyTicketButton).setOnClickListener {
            val context = holder.itemView.context
            val intent = if (cinemaName != "Không xác định") {
                Intent(context, BookingActivity::class.java)
            } else {
                Intent(context, SelectProvinceActivity::class.java)
            }

            intent.putExtra("cinema", cinemaName)
            intent.putExtra("FILM_TITLE", film.title)
            intent.putExtra("province", provinceName)
            intent.putExtra("userEmail", userEmail)
            intent.putExtra("FILM_DETAILS", " ${film.duration}\n ${film.releaseDate}\n ${film.genre}\n ${film.director}")
            intent.putExtra("FILM_POSTER_URL", film.posterUrl)

            context.startActivity(intent)
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

            com.example.doan.utils.MovieImageUtils.loadMoviePoster(moviePoster, film.posterUrl, film.title)
        }
    }
}

class PosterAdapter(private val images: List<String>) : RecyclerView.Adapter<PosterAdapter.PosterViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PosterViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.poster_item, parent, false)
        return PosterViewHolder(view)
    }

    override fun onBindViewHolder(holder: PosterViewHolder, position: Int) {
        val fallback = when (position % 3) {
            0 -> R.drawable.banner_avatar2
            1 -> R.drawable.banner_dune2
            else -> R.drawable.banner_deadpool
        }
        Glide.with(holder.itemView.context)
            .load(images[position])
            .placeholder(fallback)
            .error(fallback)
            .into(holder.posterImage)
    }

    override fun getItemCount(): Int = images.size

    class PosterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val posterImage: ImageView = itemView.findViewById(R.id.posterImage)
    }
}
