package com.bernelius.abrechnung.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DatePatternTest {
    @Test
    fun `valid pattern yyyy-MM-dd creates successfully`() {
        val pattern = DatePattern("yyyy-MM-dd")
        val formatter = DateTimeFormatter.ofPattern(pattern.value)
        val date = LocalDate.of(2024, 1, 15)
        assertEquals("2024-01-15", date.format(formatter))
    }

    @Test
    fun `valid pattern dd-MM-yyyy creates successfully`() {
        val pattern = DatePattern("dd-MM-yyyy")
        val formatter = DateTimeFormatter.ofPattern(pattern.value)
        val date = LocalDate.of(2024, 1, 15)
        assertEquals("15-01-2024", date.format(formatter))
    }

    @Test
    fun `valid pattern MM-dd-yyyy creates successfully`() {
        val pattern = DatePattern("MM-dd-yyyy")
        val formatter = DateTimeFormatter.ofPattern(pattern.value)
        val date = LocalDate.of(2024, 1, 15)
        assertEquals("01-15-2024", date.format(formatter))
    }

    @Test
    fun `valid pattern yyyyMMdd creates successfully`() {
        val pattern = DatePattern("yyyyMMdd")
        val formatter = DateTimeFormatter.ofPattern(pattern.value)
        val date = LocalDate.of(2024, 1, 15)
        assertEquals("20240115", date.format(formatter))
    }

    @Test
    fun `valid pattern with month name MMMM creates successfully`() {
        val pattern = DatePattern("MMMM d, yyyy")
        val formatter = DateTimeFormatter.ofPattern(pattern.value)
        val date = LocalDate.of(2024, 1, 15)
        assertEquals("January 15, 2024", date.format(formatter))
    }

    @Test
    fun `DatePattern value is accessible`() {
        val pattern = DatePattern("yyyy-MM-dd")
        assertEquals("yyyy-MM-dd", pattern.value)
    }

    @Test
    fun `two DatePatterns with same value are equal`() {
        val pattern1 = DatePattern("yyyy-MM-dd")
        val pattern2 = DatePattern("yyyy-MM-dd")
        assertEquals(pattern1, pattern2)
    }

    @Test
    fun `DatePattern preserves value in toString`() {
        val pattern = DatePattern("dd/MM/yyyy")
        assertEquals("DatePattern(value=dd/MM/yyyy)", pattern.toString())
    }

    @Test
    fun `missing year throws IllegalArgumentException`() {
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                DatePattern("MM-dd")
            }
        assertEquals("DatePattern must contain year, month and day. Example: yyyy-MM-dd", exception.message)
    }

    @Test
    fun `missing month throws IllegalArgumentException`() {
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                DatePattern("yyyy-dd")
            }
        assertEquals("DatePattern must contain year, month and day. Example: yyyy-MM-dd", exception.message)
    }

    @Test
    fun `missing day throws IllegalArgumentException`() {
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                DatePattern("yyyy-MM")
            }
        assertEquals("DatePattern must contain year, month and day. Example: yyyy-MM-dd", exception.message)
    }

    @Test
    fun `empty pattern throws IllegalArgumentException`() {
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                DatePattern("")
            }
        assertEquals("DatePattern must contain year, month and day. Example: yyyy-MM-dd", exception.message)
    }

    @Test
    fun `invalid pattern throws IllegalArgumentException`() {
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                DatePattern("ABC")
            }
        assertEquals("DatePattern must contain year, month and day. Example: yyyy-MM-dd", exception.message)
    }

    @Test
    fun `year only pattern throws IllegalArgumentException`() {
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                DatePattern("yyyy")
            }
        assertEquals("DatePattern must contain year, month and day. Example: yyyy-MM-dd", exception.message)
    }
}
