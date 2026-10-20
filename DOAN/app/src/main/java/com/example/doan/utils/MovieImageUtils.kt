package com.example.doan.utils

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.example.doan.R

object MovieImageUtils {
    fun getLocalPosterResource(movieTitle: String?): Int {
        val title = movieTitle?.lowercase() ?: ""
        return when {
            title.contains("avatar") -> R.drawable.poster_avatar2
            title.contains("mai") -> R.drawable.poster_mai
            title.contains("dune") || title.contains("hành tinh cát") -> R.drawable.poster_dune2
            title.contains("lật mặt") -> R.drawable.poster_latmat7
            title.contains("deadpool") || title.contains("wolverine") -> R.drawable.poster_deadpool
            title.contains("panda") || title.contains("kung fu") -> R.drawable.poster_panda4
            else -> R.drawable.your_background_image
        }
    }

    fun loadMoviePoster(imageView: ImageView, url: String?, movieTitle: String?) {
        val fallback = getLocalPosterResource(movieTitle)
        if (url.isNullOrEmpty()) {
            imageView.setImageResource(fallback)
        } else {
            Glide.with(imageView.context)
                .load(url)
                .placeholder(fallback)
                .error(fallback)
                .centerCrop()
                .into(imageView)
        }
    }
}
