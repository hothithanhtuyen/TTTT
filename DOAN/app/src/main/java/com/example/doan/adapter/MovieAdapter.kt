package com.example.doan.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.doan.R
import com.example.doan.model.Movie

class MovieAdapter(
    private val movieList: List<Movie>,
    private val onBookTicketClick: (Movie) -> Unit
) : RecyclerView.Adapter<MovieAdapter.MovieViewHolder>() {

    class MovieViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val moviePoster: ImageView = itemView.findViewById(R.id.moviePoster)
        val backgroundBlurImage: ImageView = itemView.findViewById(R.id.backgroundBlurImage)
        val movieTitle: TextView = itemView.findViewById(R.id.movieTitle)
        val movieInfo: TextView = itemView.findViewById(R.id.movieInfo)
        val bookTicketButton: Button = itemView.findViewById(R.id.bookTicketButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MovieViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_movie, parent, false)
        return MovieViewHolder(view)
    }

    override fun onBindViewHolder(holder: MovieViewHolder, position: Int) {
        val movie = movieList[position]
        com.example.doan.utils.MovieImageUtils.loadMoviePoster(holder.moviePoster, movie.imageResId, movie.title)
        holder.movieTitle.text = movie.title
        holder.movieInfo.text = movie.info
        holder.bookTicketButton.setOnClickListener {
            onBookTicketClick(movie)
        }
    }

    fun getImageResource(position: Int): String {
        return movieList[position].imageResId
    }

    override fun getItemCount() = movieList.size
}
