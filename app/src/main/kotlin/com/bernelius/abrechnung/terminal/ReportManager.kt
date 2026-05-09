package com.bernelius.abrechnung.terminal

import com.bernelius.abrechnung.models.InvoiceDTO
import com.bernelius.abrechnung.repository.Repository
import com.bernelius.abrechnung.utils.TimeSlice
import kotlinx.coroutines.runBlocking


class ReportManager(private val writer: Writer, private val reader: InputReader) {
    val allInvoices = runBlocking { getAllInvoices() }
    val timeSlice = TimeSlice(
        start = allInvoices.first().dueDate,
        end = allInvoices.last().dueDate
    )
    private val actions: Map<String, suspend () -> Unit> =
        mapOf(
            "y" to { timeSlice.zoomToYear() },
            "m" to { timeSlice.zoomToMonth() },
            // "e" to { exportToCSV() },
            "ArrowLeft" to { timeSlice.prevPeriod() },
            "h" to { timeSlice.prevPeriod() },
            "ArrowRight" to { timeSlice.nextPeriod() },
            "l" to { timeSlice.nextPeriod() },
            // "ArrowDown" to { scrollDown() },
            // "j" to { scrollDown() },
            // "ArrowUp" to {scrollUp() },
            // "k" to {scrollUp() },
        )


    suspend fun getAllInvoices(): List<InvoiceDTO> {
        return writer.withLoading(
            {
                Repository.findAllInvoices()
            }
        )
    }
    suspend fun mainMenu() {
        val scene = MordantScene(writer).apply{
            // createReportingGrid(allInvoices, timeSlice)
        }
    }
}
