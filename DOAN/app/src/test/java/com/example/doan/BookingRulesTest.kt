package com.example.doan

import com.example.doan.model.BookingRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingRulesTest {
    @Test
    fun priceMatchesServerForZeroOneAndMultipleSeats() {
        assertEquals(0L, BookingRules.totalPrice(0))
        assertEquals(22_000L, BookingRules.totalPrice(1))
        assertEquals(62_000L, BookingRules.totalPrice(3))
    }

    @Test
    fun seatCodesMatchTheSixByFiveSeatMap() {
        assertTrue(BookingRules.isValidSeatCode("A1"))
        assertTrue(BookingRules.isValidSeatCode("F5"))
        assertFalse(BookingRules.isValidSeatCode("G1"))
        assertFalse(BookingRules.isValidSeatCode("A6"))
    }
}
