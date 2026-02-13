package com.bernelius.abrechnung.terminal

import com.bernelius.abrechnung.cache.InvoiceCache
import com.bernelius.abrechnung.dateprovider.DateProvider
import com.bernelius.abrechnung.dateprovider.ofPattern
import com.bernelius.abrechnung.models.DatePattern
import com.bernelius.abrechnung.models.InvoiceDTO
import com.bernelius.abrechnung.repository.Repository
import com.bernelius.abrechnung.utils.renderLogo
import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.table.Borders
import com.github.ajalt.mordant.table.ColumnWidth
import com.github.ajalt.mordant.table.grid
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.widgets.Padding
import com.github.ajalt.mordant.widgets.Panel
import com.github.ajalt.mordant.widgets.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import com.bernelius.abrechnung.theme.Theme as th

class InvoiceManager(
    private val writer: Writer,
    private val reader: InputReader,
    private val dateProvider: DateProvider,
    private val appScope: CoroutineScope,
) {
    suspend fun mainMenu() {
        val scene = MordantScene(writer)

        val menu =
            Panel(
                grid {
                    stdMenuRow('m', "manage unpaid invoices")
                    stdMenuRow('s', "send receipt of paid invoice")
                    stdMenuRow('q', "quit to main menu", th.error)
                },
                title = Text(th.secondary("What do you want to do?")),
                padding = Padding(1, 3, 1, 3),
            )

        scene.addRow(renderLogo("Abrechnung", fontName = th.primaryFont))
        scene.addRow(menu)
        navigationLoop {
            scene.display()
            when (val char = reader.getRawCharIn('m', 's', 'q')) {
                'm' -> {
                    listUnpaidInvoices()
                }

                's' -> { // listPaidInvoices()
                }

                'q' -> {
                    exit()
                }
            }
        }
    }

    fun formatInvoiceData(
        raw: Collection<InvoiceDTO>,
        today: LocalDate,
    ): List<InvoiceDisplayRow> =
        raw.sortedBy { it.dueDate }.map {
            InvoiceDisplayRow(
                null,
                it.id,
                it.recipient.companyName,
                it.dueDate.format(ofPattern(DatePattern("MMMM dd yyyy"))),
                today.until(it.dueDate, ChronoUnit.DAYS).toInt(),
                it.total,
                it.currency,
            )
        }

    fun createKeybinds(rows: Collection<InvoiceDisplayRow>): CharArray {
        rows.forEachIndexed { index, row ->
            row.keybind = mapIntToHotkey(index)
        }
        return rows.map { it.keybind!! }.toCharArray()
    }

    private fun invoicesPanel(rows: List<InvoiceDisplayRow>): Widget =
        Panel(
            borderStyle = th.secondary,
            title = Text(th.secondary("Pending invoices")),
            titleAlign = TextAlign.CENTER,
            bottomTitle = Text("${th.error("q)")} Quit"),
            bottomTitleAlign = TextAlign.RIGHT,
            content =
                table {
                    borderStyle = th.secondary
                    tableBorders = Borders.NONE
                    cellBorders = Borders.ALL
                    column(0) { width = ColumnWidth(priority = 1) }
                    column(1) { width = ColumnWidth(priority = 1) }
                    column(2) {
                        width = ColumnWidth(priority = 2)
                        align = TextAlign.RIGHT
                    }
                    column(3) {
                        width = ColumnWidth(priority = 1)
                        align = TextAlign.RIGHT
                    }
                    column(4) { width = ColumnWidth(priority = 1) }
                    column(5) { width = ColumnWidth(priority = 2) }
                    header {
                        row {
                            cell("") { cellBorders = Borders.BOTTOM }
                            cell("Recipient") {
                                align = TextAlign.LEFT
                                cellBorders = Borders.BOTTOM
                            }
                            cell("Due") { align = TextAlign.LEFT }
                            cell("Amount") { align = TextAlign.LEFT }
                        }
                    }
                    body {
                        align = TextAlign.LEFT
                        cellBorders = Borders.TOP_BOTTOM
                        for (item in rows) {
                            row {
                                cell(th.primary("${item.keybind})"))
                                cell(item.recipientName)
                                val tint = if (item.remainingDays < 0) th.error else th.norm
                                val dayOrDays = if (abs(item.remainingDays) == 1) "day" else "days"
                                cell(tint("${item.remainingDays} $dayOrDays"))
                                cell("${"%.2f".format(item.amount)} ${item.currency}")
                            }
                        }
                    }
                },
        )

    suspend fun listUnpaidInvoices() {
        var pendingInvoices =
            writer.withLoading({ Repository.findAllInvoices("pending") }, message = "finding invoices")
        if (pendingInvoices.isEmpty()) {
            return exitToMainMenu(
                writer,
                reader,
                message = "No unpaid invoices found. The papierarbeit is complete.",
                logoText = "Inhaltslos",
            )
        }
        val scene = MordantScene(writer)
        // TODO: pagination
        var page = 1
        val idx = scene.addRow()
        navigationLoop {
            val formattedInvoices: List<InvoiceDisplayRow> =
                formatInvoiceData(pendingInvoices, dateProvider.today())
            val keybinds = createKeybinds(formattedInvoices)
            scene.replaceRow(idx, invoicesPanel(formattedInvoices))
            scene.display()
            var char = reader.getRawCharIn(*keybinds, 'q')
            if (char == 'q') {
                exit()
            }
            val selection = formattedInvoices.find { it.keybind == char }
            if (selection != null) {
                var invoice = pendingInvoices.find { selection.invoiceId == it.id }
                if (invoice != null) {
                    val invoiceCopy = invoice.copy()
                    val modification = invoiceStatusModificationView(invoiceCopy, selection.remainingDays < 0)
                    if (modification && invoiceCopy.status != invoice.status) {
                        pendingInvoices = pendingInvoices - invoice
                        updateInvoice(invoiceCopy)
                        InvoiceCache.invalidateAll()
                        if (pendingInvoices.isEmpty()) exit()
                    }
                }
            }
        }
    }

    fun invoiceStatusModificationView(
        invoice: InvoiceDTO,
        isOverdue: Boolean,
    ): Boolean {
        val style = if (isOverdue) th.error else th.secondary
        val scene = MordantScene(writer)

        val anchor = scene.addRow()
        var modification = false
        navigationLoop {
            val pOption =
                when (invoice.status) {
                    "pending" -> StyledMenuOption('p', th.primary, "Mark as paid")
                    else -> StyledMenuOption('p', th.primary, "Pendify")
                }
            val options =
                listOf(
                    pOption,
                    StyledMenuOption('i', th.error, "Invalidate"),
                    StyledMenuOption('w', th.success, "Write (save changes)"),
                    StyledMenuOption('c', th.norm, "Cancel"),
                )

            scene.replaceRow(anchor, invoiceDetailsTable(invoice, isOverdue, options))
            scene.display()
            when (val char = reader.getRawCharIn(*options.map { it.key }.toCharArray())) {
                'p' -> {
                    togglePaidStatus(invoice)
                }

                'i' -> {
                    invalidateInvoice(invoice)
                }

                'w' -> {
                    modification = true
                    exit()
                }

                'c' -> {
                    exit()
                }
            }
        }
        return modification
    }

    private fun togglePaidStatus(invoice: InvoiceDTO) {
        if (invoice.status == "pending") {
            invoice.status = "paid"
        } else {
            invoice.status = "pending"
        }
    }

    private fun invalidateInvoice(invoice: InvoiceDTO) {
        invoice.status = "invalid"
    }

    private fun updateInvoice(invoice: InvoiceDTO) {
        when (invoice.status) {
            "paid" -> appScope.launch { Repository.markInvoiceAsPaid(invoice.id) }
            "pending" -> appScope.launch { Repository.markInvoiceAsPending(invoice.id) }
            "invalid" -> appScope.launch { Repository.markInvoiceAsInvalid(invoice.id) }
        }
    }

    private fun invoiceDetailsTable(
        invoice: InvoiceDTO,
        isOverdue: Boolean,
        options: List<StyledMenuOption>,
    ): Widget {
        val styleWrapper =
            when (invoice.status) {
                "paid" -> th.success
                "pending" -> if (isOverdue) th.error else th.norm
                "invalid" -> th.norm + th.strikethrough
                else -> th.norm
            }
        return table {
            body {
                cellBorders = Borders.NONE
                row {
                    cellBorders = Borders.BOTTOM
                    cell("To: " + invoice.recipient.companyName) {
                        columnSpan = 3
                        align = TextAlign.LEFT
                    }
                    cell("Due date:") {
                        align = TextAlign.RIGHT
                    }
                    cell(invoice.dueDate.format(ofPattern(DatePattern("MMMM dd yyyy")))) {
                        style = styleWrapper
                    }
                }
                row {
                    style = th.norm + th.dim
                    cellBorders = Borders.ALL
                    cell("Description")
                    cell("Price")
                    cell("Qty")
                    cell("Discount")
                    cell("Subtotal")
                }
                for (item in invoice.invoiceItems) {
                    cellBorders = Borders.ALL
                    row {
                        cell(item.description)
                        cell(item.unitPrice.toString())
                        cell(item.quantity.toString())
                        cell(item.discount.toString() + "%")
                        cell(item.subtotal.toString())
                    }
                }
                row {
                    cellBorders = Borders.NONE
                    cell("Total Price:") {
                        columnSpan = 4
                        align = TextAlign.RIGHT
                    }
                    cell(invoice.total.toString() + " " + invoice.currency)
                }
                row {
                    cellBorders = Borders.NONE
                    cell("Status:") {
                        columnSpan = 4
                        align = TextAlign.RIGHT
                    }
                    cell(styleWrapper(invoice.status))
                }
                row {
                    cellBorders = Borders.NONE
                    padding = Padding(1, 1, 1, 1)
                }
                for (option in options) {
                    row {
                        cellBorders = Borders.NONE
                        cell(option.style(option.key.toString() + ") ") + option.value) {
                            columnSpan = 5
                            align = TextAlign.LEFT
                        }
                    }
                }
            }
        }
    }

    class InvoiceDisplayRow(
        var keybind: Char?,
        var invoiceId: Int,
        var recipientName: String,
        var dueDate: String,
        val remainingDays: Int,
        val amount: Double,
        val currency: String,
    )
}
