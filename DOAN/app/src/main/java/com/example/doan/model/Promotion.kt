package com.example.doan.model

data class Promotion(
    val title: String = "",
    val date: String = "",
    val imageUrl: String = "",
    val location: String = ""
)

data class PromotionAdmin(
    val title: String = "",
    val days: String = "",
    val location: String = "",
    val image: String = ""
)
