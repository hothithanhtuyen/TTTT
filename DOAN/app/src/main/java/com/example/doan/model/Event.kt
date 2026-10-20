package com.example.doan.model

data class Event(
    val title: String = "",
    val date: String = "",
    val imageUrl: String = "",
    val location: String = ""
)

data class EventAdmin(
    val title: String = "",
    val days: String = "",
    val pot: String = "",
    val image: String = ""
)
