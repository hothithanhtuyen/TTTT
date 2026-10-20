package com.example.doan.model

data class CinemaMovie(
    val id: String,
    val title: String,
    val tagline: String,
    val genre: String,
    val duration: String,
    val rating: String,
    val posterUrl: String,
    val synopsis: String,
)

data class BookingDraft(
    val movie: CinemaMovie,
    val bookingDate: String,
    val province: String,
    val cinema: String,
    val showtime: String,
    val seats: List<String>,
    val customerName: String,
)

data class CinemaOrder(
    val orderId: String,
    val status: String,
    val amount: Long,
    val paymentUrl: String = "",
    val ticketCode: String = "",
    val ticketQr: String = "",
    val title: String = "",
    val cinema: String = "",
    val showtime: String = "",
    val seats: String = "",
    val bookingDate: String = "",
)

data class CinemaTicket(
    val ticketCode: String,
    val title: String,
    val cinema: String,
    val showtime: String,
    val seats: String,
    val amount: Long,
    val paymentMethod: String,
    val ticketQr: String = "",
    val bookingDate: String = "",
)

data class PendingCounterOrder(
    val orderId: String,
    val customerName: String,
    val email: String,
    val title: String,
    val cinema: String,
    val showtime: String,
    val seats: String,
    val amount: Long,
    val bookingDate: String = "",
)

data class TicketCheckResult(
    val valid: Boolean,
    val alreadyChecked: Boolean,
    val message: String,
    val ticketCode: String,
    val title: String,
    val cinema: String,
    val bookingDate: String,
    val showtime: String,
    val seats: String,
)

object CinemaCatalogue {
    val movies = listOf(
        CinemaMovie(
            id = "dune-2",
            title = "Dune: Hành Tinh Cát – Phần Hai",
            tagline = "Định mệnh đang gọi tên",
            genre = "Khoa học viễn tưởng · Phiêu lưu",
            duration = "166 phút",
            rating = "T16",
            posterUrl = "https://image.tmdb.org/t/p/w500/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg",
            synopsis = "Paul Atreides sát cánh cùng Chani và người Fremen trên hành trình báo thù, đồng thời phải lựa chọn giữa tình yêu và định mệnh của cả vũ trụ.",
        ),
        CinemaMovie(
            id = "mai",
            title = "Mai",
            tagline = "Ai cũng có một giấc mơ",
            genre = "Tâm lý · Tình cảm",
            duration = "131 phút",
            rating = "T18",
            posterUrl = "https://image.tmdb.org/t/p/w500/uFsn8qBEzbeJCqH0LsViwPom5ww.jpg",
            synopsis = "Một câu chuyện tình cảm gần gũi về những lựa chọn, tổn thương và cơ hội bắt đầu lại trong cuộc sống.",
        ),
        CinemaMovie(
            id = "avatar-2",
            title = "Avatar: Dòng Chảy Của Nước",
            tagline = "Trở lại Pandora",
            genre = "Khoa học viễn tưởng · Hành động",
            duration = "192 phút",
            rating = "T13",
            posterUrl = "https://image.tmdb.org/t/p/w500/t6HIqrRAclMCA60NsSmeqe9RmNV.jpg",
            synopsis = "Jake Sully và Neytiri cùng gia đình khám phá những vùng biển rộng lớn của Pandora và chiến đấu để bảo vệ mái nhà của mình.",
        ),
        CinemaMovie(
            id = "lat-mat-7",
            title = "Lật Mặt 7: Một Điều Ước",
            tagline = "Gia đình là điều kỳ diệu",
            genre = "Gia đình · Tâm lý",
            duration = "138 phút",
            rating = "K",
            posterUrl = "https://image.tmdb.org/t/p/w500/1K7pHhCZprfDXB7LkCquIK62yCb.jpg",
            synopsis = "Một biến cố bất ngờ khiến đại gia đình phải cùng nhau nhìn lại tình thân, trách nhiệm và những điều còn chưa kịp nói.",
        ),
        CinemaMovie(
            id = "deadpool-wolverine",
            title = "Deadpool & Wolverine",
            tagline = "Hai huyền thoại, một chuyến đi",
            genre = "Hành động · Hài hước",
            duration = "128 phút",
            rating = "T18",
            posterUrl = "https://image.tmdb.org/t/p/w500/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg",
            synopsis = "Deadpool bước vào một cuộc phiêu lưu xuyên đa vũ trụ và buộc phải hợp tác với Wolverine để cứu thế giới của mình.",
        ),
        CinemaMovie(
            id = "kung-fu-panda-4",
            title = "Kung Fu Panda 4",
            tagline = "Một chiến binh mới xuất hiện",
            genre = "Hoạt hình · Phiêu lưu",
            duration = "94 phút",
            rating = "P",
            posterUrl = "https://image.tmdb.org/t/p/w500/kDp1vUBnMpe8ak4rjgl3cLELqjU.jpg",
            synopsis = "Po lên đường tìm người kế nhiệm và đối đầu với một kẻ thù mới có thể biến hình thành bất kỳ đối thủ nào.",
        ),
    )

    val provinces = listOf("Đà Nẵng", "TP. Hồ Chí Minh", "Hà Nội")

    fun cinemas(province: String): List<String> = when (province) {
        "Hà Nội" -> listOf("CGV Vincom Bà Triệu", "Lotte Cinema West Lake", "BHD Star Phạm Ngọc Thạch")
        "TP. Hồ Chí Minh" -> listOf("CGV Sư Vạn Hạnh", "Lotte Cinema Nam Sài Gòn", "BHD Star Quang Trung")
        else -> listOf("CGV Vincom Đà Nẵng", "Lotte Cinema Đà Nẵng", "Galaxy Đà Nẵng")
    }

    val showtimes = listOf("09:30", "11:45", "14:00", "16:20", "18:40", "21:10")
}

object BookingRules {
    const val TICKET_PRICE_VND = 20_000L
    const val BOOKING_FEE_VND = 2_000L

    fun totalPrice(numberOfSeats: Int): Long =
        if (numberOfSeats <= 0) 0 else numberOfSeats * TICKET_PRICE_VND + BOOKING_FEE_VND

    fun isValidSeatCode(code: String): Boolean = code.matches(Regex("^[A-F][1-5]$"))
}
