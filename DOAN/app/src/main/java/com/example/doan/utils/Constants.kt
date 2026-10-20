package com.example.doan.utils

object Constants {
    const val NODE_USERS = "Users"
    const val NODE_FILMS = "Films"
    const val NODE_TICKER = "Ticker"
    const val NODE_EVENT = "Event"
    const val NODE_PROMOTION = "Promotion"

    val PROVINCES = arrayOf("Hà Nội", "TP. Hồ Chí Minh", "Đà Nẵng", "Hải Phòng", "Cần Thơ")

    fun getCinemasForProvince(province: String): List<String> {
        return when (province) {
            "Hà Nội" -> listOf("CGV Vincom", "Lotte Landmark", "BHD Star")
            "TP. Hồ Chí Minh" -> listOf("CGV Sư Vạn Hạnh", "Mega GS", "BHD Phạm Ngọc Thạch")
            "Đà Nẵng" -> listOf(
                "Rio Liên Chiểu",
                "Starlight Thanh Khê",
                "Galaxy Thanh Khê",
                "CGV Thanh Khê",
                "CGV Sơn Trà",
                "CGV Liên Chiểu",
                "Lotte Hải Châu"
            )
            "Hải Phòng" -> listOf("CGV Vincom Hải Phòng", "Lotte Cinema Hải Phòng", "Galaxy Hải Phòng")
            "Cần Thơ" -> listOf("CGV Vincom Hùng Vương", "Lotte Cinema Cần Thơ", "CGV Xuân Khánh")
            else -> listOf("Chưa có rạp tại khu vực này")
        }
    }
}
