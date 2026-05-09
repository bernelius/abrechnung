package com.bernelius.abrechnung.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class TimeSliceTest {
    private val start = LocalDate.of(2020, 1, 1)
    private val end = LocalDate.of(2025, 12, 31)

    @Test
    fun `currentSlice defaults to end date`() {
        val period = TimeSlice(start = start, end = end)
        assertEquals(end, period.currentSlice)
    }

    @Test
    fun `zoomLevel defaults to YEAR`() {
        val period = TimeSlice(start = start, end = end)
        assertEquals(TimeSlice.ZoomLevel.YEAR, period.zoomLevel)
    }

    @Test
    fun `zoomToMonth sets zoomLevel to MONTH`() {
        val period = TimeSlice(start = start, end = end)
        period.zoomToMonth()
        assertEquals(TimeSlice.ZoomLevel.MONTH, period.zoomLevel)
    }

    @Test
    fun `zoomToYear sets zoomLevel to YEAR`() {
        val period = TimeSlice(start = start, end = end, zoomLevel = TimeSlice.ZoomLevel.MONTH)
        period.zoomToYear()
        assertEquals(TimeSlice.ZoomLevel.YEAR, period.zoomLevel)
    }

    @Test
    fun `prevPeriod in YEAR zoom clamps to start when crossing lower bound`() {
        val period = TimeSlice(start = start, end = end, currentSlice = LocalDate.of(2020, 6, 15))
        period.prevPeriod()
        assertEquals(start, period.currentSlice)
    }

    @Test
    fun `nextPeriod in YEAR zoom clamps to end when crossing upper bound`() {
        val period = TimeSlice(start = start, end = end, currentSlice = LocalDate.of(2025, 6, 15))
        period.nextPeriod()
        assertEquals(end, period.currentSlice)
    }

    @Test
    fun `prevPeriod in YEAR zoom decrements year`() {
        val period = TimeSlice(start = start, end = end, currentSlice = LocalDate.of(2023, 6, 15))
        period.prevPeriod()
        assertEquals(LocalDate.of(2022, 6, 15), period.currentSlice)
    }

    @Test
    fun `nextPeriod in YEAR zoom increments year`() {
        val period = TimeSlice(start = start, end = end, currentSlice = LocalDate.of(2023, 6, 15))
        period.nextPeriod()
        assertEquals(LocalDate.of(2024, 6, 15), period.currentSlice)
    }

    @Test
    fun `prevPeriod in MONTH zoom decrements month`() {
        val period = TimeSlice(
            start = start,
            end = end,
            currentSlice = LocalDate.of(2023, 5, 1),
            zoomLevel = TimeSlice.ZoomLevel.MONTH
        )
        period.prevPeriod()
        assertEquals(LocalDate.of(2023, 4, 1), period.currentSlice)
    }

    @Test
    fun `prevPeriod in MONTH zoom crosses year boundary`() {
        val period = TimeSlice(
            start = start,
            end = end,
            currentSlice = LocalDate.of(2023, 1, 1),
            zoomLevel = TimeSlice.ZoomLevel.MONTH
        )
        period.prevPeriod()
        assertEquals(LocalDate.of(2022, 12, 1), period.currentSlice)
    }

    @Test
    fun `nextPeriod in MONTH zoom increments month`() {
        val period = TimeSlice(
            start = start,
            end = end,
            currentSlice = LocalDate.of(2023, 5, 1),
            zoomLevel = TimeSlice.ZoomLevel.MONTH
        )
        period.nextPeriod()
        assertEquals(LocalDate.of(2023, 6, 1), period.currentSlice)
    }

    @Test
    fun `nextPeriod in MONTH zoom crosses year boundary`() {
        val period = TimeSlice(
            start = start,
            end = end,
            currentSlice = LocalDate.of(2023, 12, 1),
            zoomLevel = TimeSlice.ZoomLevel.MONTH
        )
        period.nextPeriod()
        assertEquals(LocalDate.of(2024, 1, 1), period.currentSlice)
    }

    @Test
    fun `prevPeriod in MONTH zoom does not decrement when at lower bound`() {
        val period = TimeSlice(
            start = start,
            end = end,
            currentSlice = start,
            zoomLevel = TimeSlice.ZoomLevel.MONTH
        )
        period.prevPeriod()
        assertEquals(start, period.currentSlice)
    }

    @Test
    fun `nextPeriod in MONTH zoom does not increment when at upper bound`() {
        val period = TimeSlice(
            start = start,
            end = end,
            currentSlice = end,
            zoomLevel = TimeSlice.ZoomLevel.MONTH
        )
        period.nextPeriod()
        assertEquals(end, period.currentSlice)
    }

    @Test
    fun `prevPeriod in MONTH zoom stops at start when crossing would undershoot`() {
        val period = TimeSlice(
            start = LocalDate.of(2020, 6, 15),
            end = end,
            currentSlice = LocalDate.of(2020, 7, 15),
            zoomLevel = TimeSlice.ZoomLevel.MONTH
        )
        period.prevPeriod()
        assertEquals(LocalDate.of(2020, 6, 15), period.currentSlice)
    }

    @Test
    fun `nextPeriod in MONTH zoom stops at end when crossing would overshoot`() {
        val period = TimeSlice(
            start = start,
            end = LocalDate.of(2025, 6, 15),
            currentSlice = LocalDate.of(2025, 5, 15),
            zoomLevel = TimeSlice.ZoomLevel.MONTH
        )
        period.nextPeriod()
        assertEquals(LocalDate.of(2025, 6, 15), period.currentSlice)
    }

    @Test
    fun `prevPeriod in YEAR zoom clamps to start for partial year lower bound`() {
        val period = TimeSlice(
            start = LocalDate.of(2020, 6, 15),
            end = end,
            currentSlice = LocalDate.of(2021, 3, 10)
        )
        period.prevPeriod()
        assertEquals(LocalDate.of(2020, 6, 15), period.currentSlice)
    }

    @Test
    fun `nextPeriod in YEAR zoom clamps to end for partial year upper bound`() {
        val period = TimeSlice(
            start = start,
            end = LocalDate.of(2025, 6, 15),
            currentSlice = LocalDate.of(2024, 9, 10)
        )
        period.nextPeriod()
        assertEquals(LocalDate.of(2025, 6, 15), period.currentSlice)
    }

    @Test
    fun `prevPeriod in MONTH zoom clamps to start across end of month mismatch`() {
        val period = TimeSlice(
            start = LocalDate.of(2020, 1, 31),
            end = end,
            currentSlice = LocalDate.of(2020, 3, 31),
            zoomLevel = TimeSlice.ZoomLevel.MONTH
        )
        period.prevPeriod()
        assertEquals(LocalDate.of(2020, 2, 29), period.currentSlice)
        period.prevPeriod()
        assertEquals(LocalDate.of(2020, 1, 31), period.currentSlice)
    }

    @Test
    fun `start equals end is a no-op in YEAR zoom`() {
        val single = LocalDate.of(2023, 6, 15)
        val period = TimeSlice(start = single, end = single)
        period.prevPeriod()
        assertEquals(single, period.currentSlice)
        period.nextPeriod()
        assertEquals(single, period.currentSlice)
    }

    @Test
    fun `start equals end is a no-op in MONTH zoom`() {
        val single = LocalDate.of(2023, 6, 15)
        val period = TimeSlice(
            start = single,
            end = single,
            zoomLevel = TimeSlice.ZoomLevel.MONTH
        )
        period.prevPeriod()
        assertEquals(single, period.currentSlice)
        period.nextPeriod()
        assertEquals(single, period.currentSlice)
    }

    @Test
    fun `constructor rejects start after end`() {
        assertThrows<IllegalArgumentException> {
            TimeSlice(start = LocalDate.of(2025, 1, 1), end = LocalDate.of(2020, 1, 1))
        }
    }

    @Test
    fun `constructor rejects currentSlice before start`() {
        assertThrows<IllegalArgumentException> {
            TimeSlice(
                start = start,
                end = end,
                currentSlice = LocalDate.of(2019, 6, 15)
            )
        }
    }

    @Test
    fun `constructor rejects currentSlice after end`() {
        assertThrows<IllegalArgumentException> {
            TimeSlice(
                start = start,
                end = end,
                currentSlice = LocalDate.of(2026, 6, 15)
            )
        }
    }
}
