package com.example.doan.model

data class Ticket(
    val movieName: String = "",
    val showTime: String = "",
    val lich: String = "",
    val ghe: String = "",
    val rap: String = "",
    val gia: String = "",
    val tt: String = "",
    val pttt: String = "",
    val code: String = "",
    val posterUrl: String = ""
)

data class TicketOrder(
    val ten: String = "",
    val email: String = "",
    val ngaydat: String = "",
    val phim: String = "",
    val lich: String = "",
    val ghe: String = "",
    val rap: String = "",
    val gia: String = "",
    val tt: String = "",
    val pttt: String = "Tiền mặt"
)

data class ConfirmTicketItem(
    val id: String = "",
    val name: String = ""
)
