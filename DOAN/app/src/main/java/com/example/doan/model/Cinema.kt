package com.example.doan.model

data class Cinema(
    var location: String = "",
    var theater: String = "",
    var schedule: MutableMap<String, MutableList<Int>> = mutableMapOf(
        "Chủ Nhật" to mutableListOf(),
        "Thứ Hai" to mutableListOf(),
        "Thứ Ba" to mutableListOf(),
        "Thứ Tư" to mutableListOf(),
        "Thứ Năm" to mutableListOf(),
        "Thứ Sáu" to mutableListOf(),
        "Thứ Bảy" to mutableListOf()
    )
)
