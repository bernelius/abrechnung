package com.bernelius.abrechnung.dateprovider

import com.bernelius.abrechnung.models.DatePattern
import java.time.LocalDate
import java.time.format.DateTimeFormatter

interface DateProvider {
    fun today(): LocalDate
}

class SystemDateProvider : DateProvider {
    override fun today(): LocalDate = LocalDate.now()
}

class MockDateProvider(
    val year: Int = 1970,
    val month: Int = 1,
    val day: Int = 1,
) : DateProvider {
    override fun today(): LocalDate = LocalDate.of(year, month, day)
}

fun ofPattern(pattern: DatePattern): DateTimeFormatter = DateTimeFormatter.ofPattern(pattern.value)
