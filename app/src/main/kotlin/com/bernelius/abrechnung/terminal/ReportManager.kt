package com.bernelius.abrechnung.terminal

import com.bernelius.abrechnung.models.InvoiceDTO
import com.bernelius.abrechnung.repository.Repository
import com.bernelius.abrechnung.utils.TimeSlice
import com.github.ajalt.mordant.rendering.BorderType
import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.table.Borders
import com.github.ajalt.mordant.table.ColumnWidth
import com.github.ajalt.mordant.table.table
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import com.bernelius.abrechnung.theme.Theme as th

class ReportGridFactory(
    val invoices: List<InvoiceDTO>,
    var timeSlice: TimeSlice,
    val maxHeight: Int,
) {
    var sumTotal = 0.0
    var sumExVat = 0.0
    var sumVat = 0.0
    var gridIndex = 0
    lateinit var grids: List<Widget>
    val grid: Widget
        get() = grids[gridIndex]

    init {
        println(invoices.size)
        refresh()
    }

    fun scrollUp() {
        gridIndex = max(gridIndex - 1, 0)
    }

    fun scrollDown() {
        gridIndex = min(gridIndex + 1, grids.size - 1)
    }

    fun refresh() {
        val invoices = getInvoicesInTimeSlice()
        println(invoices.size)
        sumTotal = invoices.sumOf { it.total }
        sumExVat = invoices.sumOf { it.total - it.vatAmount }
        sumVat = invoices.sumOf { it.vatAmount }
        val rows = createReportingRows(invoices)
        //can change rowHeight if needed
        val chunks = createChunks(rows, rowHeight = 2)
        grids = createGrids(chunks)
    }

    private fun getInvoicesInTimeSlice(): List<InvoiceDTO> {
        return invoices.filter { timeSlice.start <= it.dueDate && timeSlice.end >= it.dueDate }
    }

    private fun createReportingRows(invoices: List<InvoiceDTO>): List<InvoiceReportingRow> {
        val l = mutableListOf<InvoiceReportingRow>()
        for (invoice in invoices) {
            l.add(
                InvoiceReportingRow(
                    id = invoice.id,
                    name = invoice.recipient.companyName,
                    total = invoice.total,
                    totalExVat = invoice.total - invoice.vatAmount,
                    dueDate = invoice.dueDate,
                )
            )
        }
        return l
    }

    private fun createChunks(
        source: List<InvoiceReportingRow>,
        rowHeight: Int
    ): List<List<InvoiceReportingRow>> {
        // we take rows from maxHeight because we need space for totals at the bottom and descriptors at the top
        return source.chunked((maxHeight - 8) / rowHeight)
    }

    private fun createGrids(chunks: List<List<InvoiceReportingRow>>): List<Widget> {
        val grids = mutableListOf<Widget>()
        for (chunk in chunks) {
            val items = chunk.map { it }
            grids.add(
                table {
                    cellBorders = Borders.ALL
                    borderType = BorderType.ROUNDED
                    borderStyle = th.tertiary
                    column(0) {
                        width = ColumnWidth(1)
                        align = TextAlign.RIGHT
                    }
                    column(1) {
                        width = ColumnWidth(1)
                        align = TextAlign.LEFT
                    }
                    column(2) {
                        width = ColumnWidth(1)
                        align = TextAlign.LEFT
                    }
                    column(3) {
                        width = ColumnWidth(1)
                        align = TextAlign.LEFT
                    }
                    column(4) {
                        width = ColumnWidth(1)
                        align = TextAlign.LEFT
                    }
                    header {
                        row {
                            cell("Invoice id")
                            cell("Recipient")
                            cell("Total")
                            cell("Total Ex. Vat")
                            cell("Due Date")
                        }
                    }
                    body {
                        items.forEach { item ->
                            row {
                                cell(item.id)
                                cell(item.name)
                                cell(item.total)
                                cell(item.totalExVat)
                                cell(item.dueDate.toString())
                            }
                        }
                    }
                    footer {
                        row {
                            cell("Total: $sumTotal - Total Ex. Vat: $sumExVat - Vat: $sumVat") { columnSpan = 5 }
                        }
                    }
                }
            )

        }
        return grids
    }
}

data class InvoiceReportingRow(
    val id: Int,
    val name: String,
    val total: Double,
    val totalExVat: Double,
    val dueDate: LocalDate
)

class ReportManager(private val writer: Writer, private val reader: InputReader) {
    // these are ordered by dueDate further upstream
    val allInvoices = runBlocking { getAllInvoices() }
    val timeSlice = TimeSlice(
        start = allInvoices.first().dueDate,
        end = allInvoices.last().dueDate
    )
    val factory = ReportGridFactory(allInvoices, timeSlice, writer.t.size.height)
    val grid = factory.grid


    private val actions: Map<String, suspend () -> Unit> =
        mapOf(
            "y" to { timeSlice.zoomToYear() },
            "m" to { timeSlice.zoomToMonth() },
            // "e" to { exportToCSV() },
            "ArrowLeft" to { timeSlice.prevPeriod() },
            "h" to { timeSlice.prevPeriod() },
            "ArrowRight" to { timeSlice.nextPeriod() },
            "l" to { timeSlice.nextPeriod() },
            "ArrowDown" to { factory.scrollDown() },
            "j" to { factory.scrollDown() },
            "ArrowUp" to { factory.scrollUp() },
            "k" to { factory.scrollUp() },
        )


    suspend fun getAllInvoices(): List<InvoiceDTO> {
        return writer.withLoading(
            {
                Repository.findAllInvoices()
            }
        )
    }

    suspend fun mainMenu() {
        var counter = 0
        navigationLoop {
            val scene = MordantScene(writer).apply {
                addRow("Counter: ${counter++}")
                addRow(grid)
                display()
            }
            val choice = reader.getKeyIn(actions.keys)
            actions[choice]!!.invoke()
        }
    }
}
