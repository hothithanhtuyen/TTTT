package com.example.doan

import org.junit.Test
import org.junit.Assert.*
import com.example.doan.model.*

/**
 * Unit tests for DOAN application logic and models.
 */
class ExampleUnitTest {

    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testTicketPriceCalculation() {
        val seatCount = 3
        val pricePerSeat = 20000
        val transactionFee = 2000
        val totalPrice = (seatCount * pricePerSeat) + transactionFee

        assertEquals(62000, totalPrice)
    }

    @Test
    fun testUserModelDefaults() {
        val user = User(
            name = "Nguyen Van A",
            email = "test@example.com",
            password = "password123",
            birthDate = "01/01/2000",
            gender = "Nam",
            province = "Hà Nội"
        )

        assertEquals("Nguyen Van A", user.name)
        assertEquals("test@example.com", user.email)
        assertEquals("Nam", user.gender)
        assertEquals("Hà Nội", user.province)
    }

    @Test
    fun testFilmModel() {
        val film = Film(
            title = "Avatar 3",
            duration = "150 Phút",
            releaseDate = "20/12/2026",
            genre = "Sci-Fi",
            director = "James Cameron",
            posterUrl = "https://example.com/avatar3.jpg"
        )

        assertEquals("Avatar 3", film.title)
        assertEquals("150 Phút", film.duration)
        assertEquals("Sci-Fi", film.genre)
    }

    @Test
    fun testCinemaSchedule() {
        val cinema = Cinema(
            location = "Hà Nội",
            theater = "CGV Vincom"
        )
        cinema.schedule["Chủ Nhật"]?.add(1)
        cinema.schedule["Chủ Nhật"]?.add(2)

        assertEquals("Hà Nội", cinema.location)
        assertEquals("CGV Vincom", cinema.theater)
        assertEquals(2, cinema.schedule["Chủ Nhật"]?.size)
        assertTrue(cinema.schedule["Chủ Nhật"]?.contains(1) == true)
    }

    @Test
    fun testVerificationCodeGeneration() {
        val kyTu = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val codeLength = 6
        val code = (1..codeLength)
            .map { kyTu.random() }
            .joinToString("")

        assertEquals(6, code.length)
        assertTrue(code.all { it in kyTu })
    }

    @Test
    fun testEmailFormatValidation() {
        val validEmail = "user@example.com"
        val invalidEmail = "user-example.com"

        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$".toRegex()
        assertTrue(validEmail.matches(emailRegex))
        assertFalse(invalidEmail.matches(emailRegex))
    }
}