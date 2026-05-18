package com.bernelius.abrechnung.utils

import java.time.LocalDate


internal class TimeSlice(
    val start: LocalDate,
    val end: LocalDate,
    var currentSlice: LocalDate = end,
    var zoomLevel: ZoomLevel = ZoomLevel.YEAR,
) {
    val periodStart: LocalDate
        get() = when (zoomLevel) {
            ZoomLevel.YEAR -> LocalDate.of(currentSlice.year, 1, 1)
            ZoomLevel.MONTH -> currentSlice.withDayOfMonth(1)
        }
    val periodEnd: LocalDate
        get() = when (zoomLevel) {
            ZoomLevel.YEAR -> LocalDate.of(currentSlice.year, 12, 31)
            ZoomLevel.MONTH -> currentSlice.withDayOfMonth(currentSlice.lengthOfMonth())
        }

    init {
        require(start <= end)
        require(currentSlice in start..end)
    }

    enum class ZoomLevel {
        YEAR, MONTH
    }

    fun zoomToMonth() {
        zoomLevel = ZoomLevel.MONTH
    }

    fun zoomToYear() {
        zoomLevel = ZoomLevel.YEAR
    }

    fun prevPeriod() {
        when (zoomLevel) {
            ZoomLevel.YEAR -> decrementYear()
            ZoomLevel.MONTH -> decrementMonth()
        }
    }

    fun nextPeriod() {
        when (zoomLevel) {
            ZoomLevel.YEAR -> incrementYear()
            ZoomLevel.MONTH -> incrementMonth()
        }
    }

    private fun decrementMonth() {
        val newSlice = currentSlice.minusMonths(1)
        if (newSlice < start) currentSlice = start
        else currentSlice = newSlice
    }

    private fun incrementMonth() {
        val newSlice = currentSlice.plusMonths(1)
        if (newSlice > end) currentSlice = end
        else currentSlice = newSlice
    }

    private fun decrementYear() {
        val newSlice = currentSlice.minusYears(1)
        if (newSlice < start) currentSlice = start
        else currentSlice = newSlice
    }

    private fun incrementYear() {
        val newSlice = currentSlice.plusYears(1)
        if (newSlice > end) currentSlice = end
        else currentSlice = newSlice
    }
}

