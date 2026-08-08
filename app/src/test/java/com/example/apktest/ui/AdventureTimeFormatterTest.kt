package com.example.apktest.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AdventureTimeFormatterTest {

    @Test
    fun format_zeroSecondsReturnsDoubleZero() {
        assertEquals("00:00", AdventureTimeFormatter.format(0f))
    }

    @Test
    fun format_negativeSecondsClampedToZero() {
        assertEquals("00:00", AdventureTimeFormatter.format(-10f))
    }

    @Test
    fun format_lessThanOneMinute() {
        assertEquals("00:45", AdventureTimeFormatter.format(45f))
    }

    @Test
    fun format_exactlyOneMinute() {
        assertEquals("01:00", AdventureTimeFormatter.format(60f))
    }

    @Test
    fun format_minutesAndSeconds() {
        assertEquals("02:14", AdventureTimeFormatter.format(134f))
    }

    @Test
    fun format_largeValue() {
        assertEquals("99:59", AdventureTimeFormatter.format(5999f))
    }

    @Test
    fun format_fractionalSecondsAreTruncated() {
        // 90.9 → 1 minute 30 seconds
        assertEquals("01:30", AdventureTimeFormatter.format(90.9f))
    }
}
