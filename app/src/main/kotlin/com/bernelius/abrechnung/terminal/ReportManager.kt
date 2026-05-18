package com.bernelius.abrechnung.terminal

import com.bernelius.abrechnung.models.InvoiceDTO
import com.bernelius.abrechnung.repository.Repository
import com.bernelius.abrechnung.utils.TimeSlice
import com.bernelius.abrechnung.utils.TimeSlice.ZoomLevel
import com.github.ajalt.mordant.rendering.BorderType
import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.table.Borders
import com.github.ajalt.mordant.table.ColumnWidth
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.widgets.Padding
import com.github.ajalt.mordant.widgets.Panel
import com.github.ajalt.mordant.widgets.Text
import kotlinx.coroutines.runBlocking
import java.time.format.DateTimeFormatter
import java.util.Locale.getDefault
import kotlin.math.max
import kotlin.math.min
import com.bernelius.abrechnung.theme.Theme as th

class ScrollbarGenerator(
    reportGridGenerator: ReportGridGenerator,
) {
    val grid = reportGridGenerator
    private val _scrollbar = StringBuilder()
    val scrollbar: Widget
        get() = Text(_scrollbar.toString())

    init {
        val table = grid.table
        val trackHeight = table.height
        val totalPages = grid.tables.size
        val upTheme = if (grid.hasPrevious)
            th.secondary else th.dim
        val downTheme = if (grid.hasNext)
            th.secondary else th.dim

        _scrollbar.append(upTheme("↑\n"))
        if (totalPages > 0) {
            val thumbSize = max(1, trackHeight / totalPages)
            val thumbPosition =
                if (totalPages <= 1) 0 else (grid.tableIndex * (trackHeight - thumbSize)) / (totalPages - 1)
            for (i in 0 until trackHeight) {
                if (i in thumbPosition until (thumbPosition + thumbSize)) {
                    _scrollbar.append(th.dim(th.secondary("█")))
                } else {
                    _scrollbar.append(th.dim("│"))
                }
                _scrollbar.append("\n")
            }
        }
        _scrollbar.append(downTheme("↓"))
    }
}

class ReportGridGenerator(
    val invoices: List<InvoiceDTO>,
    maxHeight: Int,
) {
    private val timeSlice = TimeSlice(
        start = invoices.first().dueDate,
        end = invoices.last().dueDate
    )

    // we take rows from maxHeight because we need space for totals at the bottom and descriptors at the top
    val maxHeightAdjusted = maxHeight - 10
    var sumTotal = 0.0
    var sumExVat = 0.0
    var sumVat = 0.0
    var tableIndex = 0
    lateinit var tables: List<Table>

    private val zoomLevel: ZoomLevel
        get() = timeSlice.zoomLevel

    val table: Table
        get() = tables[tableIndex]


    val hasNext: Boolean
        get() = tableIndex < tables.size - 1

    val hasPrevious: Boolean
        get() = tableIndex > 0

    init {
        refresh()
    }

    fun scrollUp() {
        tableIndex = max(tableIndex - 1, 0)
    }

    fun scrollDown() {
        tableIndex = min(tableIndex + 1, tables.size - 1)
    }

    fun zoomToYear() {
        timeSlice.zoomToYear()
        refresh()
    }

    fun zoomToMonth() {
        timeSlice.zoomToMonth()
        refresh()
    }

    fun prevPeriod() {
        timeSlice.prevPeriod()
        refresh()
    }

    fun nextPeriod() {
        timeSlice.nextPeriod()
        refresh()
    }

    fun refresh() {
        tableIndex = 0
        val invoices = getInvoicesInTimeSlice()
        sumTotal = invoices.sumOf { it.total }
        sumExVat = invoices.sumOf { it.total - it.vatAmount }
        sumVat = invoices.sumOf { it.vatAmount }
        tables = createTables(
            createChunks(
                invoices, rowHeight = ROW_HEIGHT
            )
        )
    }

    fun exportToCSV() {

    }

    private fun getInvoicesInTimeSlice(): List<InvoiceDTO> {
        return invoices.filter { timeSlice.periodStart <= it.dueDate && timeSlice.periodEnd >= it.dueDate }
    }

    private fun createChunks(
        source: List<InvoiceDTO>,
        rowHeight: Int
    ): List<List<InvoiceDTO>> {
        return source.chunked(maxHeightAdjusted / rowHeight)
    }

    data class Table(val widget: Widget, val height: Int)

    private fun createTables(chunks: List<List<InvoiceDTO>>): List<Table> {
        val tables = mutableListOf<Table>()
        val options = mutableListOf(
            "${th.primary("e)")} Export csv",
        )
        when (zoomLevel) {
            ZoomLevel.MONTH -> options.add("${th.primary("y)")} Yearly")
            ZoomLevel.YEAR -> options.add("${th.primary("m)")} Monthly")
        }
        options.add("${th.error("q)")} Cancel")

        val periodOptions = arrayOf(
            th.primary("←) "),
            th.primary(" →)")
        )
        if (timeSlice.periodStart <= timeSlice.start) {
            periodOptions[0] = ""
        }
        if (timeSlice.periodEnd >= timeSlice.end) {
            periodOptions[1] = ""
        }

        for (chunk in chunks) {
            val items = chunk.map { it }

            var length = 0
            val pan = Panel(
                bottomTitle = Text(options.joinToString(separator = th.secondary(" / "))),
                bottomTitleAlign = TextAlign.RIGHT,
                borderStyle = th.secondary,
                content = table {
                    cellBorders = Borders.BOTTOM_RIGHT
                    borderType = BorderType.ROUNDED
                    borderStyle = th.secondary
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
                        cellBorders = Borders.BOTTOM
                    }
                    header {
                        row {
                            length++
                            cellBorders = Borders.BOTTOM
                            val timeExplanation = when (zoomLevel) {
                                ZoomLevel.YEAR -> "${timeSlice.periodStart.year} Full Year"
                                ZoomLevel.MONTH -> "${timeSlice.periodStart.year} ${
                                    timeSlice.periodStart.month.toString().lowercase()
                                        .replaceFirstChar { it.titlecase(getDefault()) }
                                }"
                            }
                            cell(periodOptions.joinToString(timeExplanation)) {
                                columnSpan = 4
                                align = TextAlign.LEFT
                            }
                            // This is weird, but it works. Tables.size is technically correct at this point
                            // in time, since we are filling the list with tables as we go. on the next chunk the
                            // table.size will grow, and continue to be correct. tableIndex will not work for anything
                            // here, since the index is 0 while the grids are being created
                            cell("Page [${tables.size + 1}/${chunks.size}]")
                        }
                        row {
                            length++
                            cell("Invoice id")
                            cell("Recipient")
                            cell("Vat")
                            cell("Total")
                            cell("Due Date")
                        }
                    }
                    body {
                        items.forEach { item ->
                            row {
                                length++
                                cell(item.id)
                                cell(item.recipient.companyName)
                                cell(item.vatAmount)
                                cell(item.total)
                                cell(item.dueDate.format(DateTimeFormatter.ofPattern("d MMM")))
                            }
                        }
                    }
                    footer {
                        row {
                            length++
                            cell("Total: $sumTotal - Total Ex. Vat: $sumExVat - Vat: $sumVat") {
                                columnSpan = 5
                                cellBorders = Borders.NONE
                                padding = Padding(0, 0, 1, 0)
                            }
                        }
                    }
                }
            )
            length *= ROW_HEIGHT
            tables.add(Table(pan, length))

        }
        return tables
    }

    companion object {
        private const val ROW_HEIGHT = 2
    }
}


class ReportManager(private val writer: Writer, private val reader: InputReader) {
    // these are ordered by dueDate further upstream
    val allInvoices = runBlocking { getAllInvoices() }
    val generator = ReportGridGenerator(allInvoices, writer.t.size.height)




    suspend fun getAllInvoices(): List<InvoiceDTO> {
        return writer.withLoading(
            {
                Repository.findAllInvoices(filter = "paid")
            }
        )
    }

    suspend fun mainMenu() {
        navigationLoop {
            val actions: Map<String, suspend () -> Unit> =
                mapOf(
                    "y" to { generator.zoomToYear() },
                    "m" to { generator.zoomToMonth() },
                    "e" to { generator.exportToCSV() },
                    "ArrowLeft" to { generator.prevPeriod() },
                    "h" to { generator.prevPeriod() },
                    "ArrowRight" to { generator.nextPeriod() },
                    "l" to { generator.nextPeriod() },
                    "ArrowDown" to { generator.scrollDown() },
                    "j" to { generator.scrollDown() },
                    "ArrowUp" to { generator.scrollUp() },
                    "k" to { generator.scrollUp() },
                    "q" to { exit() }
                )

            val table = generator.table
            val scrollbar = ScrollbarGenerator(generator).scrollbar

            MordantScene(writer).apply {
                addRow(table.widget, scrollbar)
                display()
            }
            val choice = reader.getKeyIn(actions.keys)
            actions[choice]!!.invoke()
        }
    }
}
