package com.example.doan.model

data class User(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val birthDate: String = "",
    val gender: String = "",
    val province: String = ""
)

data class AppUser(
    val email: String = "",
    val password: String = ""
)
