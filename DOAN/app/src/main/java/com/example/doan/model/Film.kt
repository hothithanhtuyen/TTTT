package com.example.doan.model

data class Film(
    val title: String = "",
    val duration: String = "",
    val releaseDate: String = "",
    val genre: String = "",
    val director: String = "",
    val posterUrl: String = ""
)

data class FilmAdmin(
    val name: String = "",
    val URL: String = "",
    val duration: String = "",
    val releaseDate: String = "",
    val genre: String = "",
    val director: String = "",
    val cinemas: List<Cinema> = emptyList()
)
