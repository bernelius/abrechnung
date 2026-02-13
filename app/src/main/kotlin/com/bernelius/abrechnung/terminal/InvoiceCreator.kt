package com.bernelius.abrechnung.terminal

import com.bernelius.abrechnung.PDF.invoiceToPDF
import com.bernelius.abrechnung.config.ConfigManager
import com.bernelius.abrechnung.config.InvoiceConfig
import com.bernelius.abrechnung.config.loadLanguage
import com.bernelius.abrechnung.dateprovider.DateProvider
import com.bernelius.abrechnung.dateprovider.ofPattern
import com.bernelius.abrechnung.mail.sendMail
import com.bernelius.abrechnung.models.DatePattern
import com.bernelius.abrechnung.models.VatDateConfigDTO
import com.bernelius.abrechnung.models.EmailUserDTO
import com.bernelius.abrechnung.models.InvoiceDTO
import com.bernelius.abrechnung.models.InvoiceInputDTO
import com.bernelius.abrechnung.models.InvoiceItemDTO
import com.bernelius.abrechnung.models.RecipientDTO
import com.bernelius.abrechnung.repository.Repository
import com.bernelius.abrechnung.utils.Outcome
import com.bernelius.abrechnung.utils.renderLogo
import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.Whitespace
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.table.Borders
import com.github.ajalt.mordant.table.ColumnWidth
import com.github.ajalt.mordant.table.grid
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.widgets.Panel
import com.github.ajalt.mordant.widgets.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import com.bernelius.abrechnung.theme.Theme as th

fun createKeybinds(allRecipients: List<RecipientDTO>): CharArray {
    allRecipients.forEachIndexed { index, recipient ->
        recipient.keybind = mapIntToHotkey(index)
    }
    return allRecipients.map { it.keybind!! }.toCharArray()
}


class InvoiceCreator(
    private val writer: Writer,
    private val reader: InputReader,
    private val dateProvider: DateProvider,
    private val appScope: CoroutineScope,
    private val mainScene: Scene = MordantScene(writer),
    private val config: InvoiceConfig = ConfigManager.loadConfig().invoiceConfig,
    private val datePattern: DatePattern = ConfigManager.loadLanguage(config.language).datePattern,
    private val today: LocalDate = dateProvider.today()
) {
    fun createVatGrid(
        vatDateConfig: VatDateConfigDTO,
        padding: Boolean = true,
    ): Widget {
        val resolved = vatDateConfig.resolve(today)
        return grid {
            column(0) {
                width = ColumnWidth(priority = 2, width = 15)
                align = TextAlign.RIGHT
            }
            column(1) {
                width = ColumnWidth(priority = 1, width = 15)
                align = TextAlign.LEFT
            }
            if (padding) row()
            row {
                cell("VAT rate:")
                cell("${resolved.vatRate}%")
            }
            row {
                cell("Invoice date:")
                cell("${resolved.invoiceDate.format(ofPattern(datePattern))}")
            }
            row {
                cell("Due date:")
                cell("${resolved.dueDate.format(ofPattern(datePattern))}")
            }
            if (padding) row()
        }
    }

    suspend fun changeDueDateOffset(vatDateConfig: VatDateConfigDTO, idx: Int) {
        validationLoop(
            message = "What is the due date offset? (in days)",
            prefill = "",
            reader = reader,
            scene = mainScene,
            onSuccess = { newValue ->
                vatDateConfig.dueDateOffset =
                    if (newValue.isBlank()) {
                        config.dueDateOffset.toLong()
                    } else {
                        newValue.toLong()
                    }

                mainScene.replaceRow(
                    idx,
                    createVatGrid(
                        vatDateConfig,
                    ),
                )
            },
            *VatDateConfigDTO.dueDateOffsetValidators,
        )
    }

    suspend fun changeInvoiceDateOffset(vatDateConfig: VatDateConfigDTO, idx: Int) {
        validationLoop(
            message = "What is the invoice date offset? (in days)",
            prefill = "",
            reader = reader,
            scene = mainScene,
            onSuccess = { newValue ->
                vatDateConfig.invoiceDateOffset =
                    if (newValue.isBlank()) {
                        0
                    } else {
                        newValue.toLong()
                    }
                mainScene.replaceRow(
                    idx,
                    createVatGrid(
                        vatDateConfig
                    ),
                )
            },
            *VatDateConfigDTO.invoiceDateOffsetValidators,
        )
    }

    suspend fun changeVatRate(vatDateConfig: VatDateConfigDTO, idx: Int) {
        validationLoop(
            message = "What is the VAT rate? (in percent)",
            prefill = "",
            reader = reader,
            scene = mainScene,
            onSuccess = { newValue ->
                vatDateConfig.vatRate =
                    if (newValue.isBlank()) {
                        0
                    } else {
                        newValue.toInt()
                    }
                mainScene.replaceRow(
                    idx,
                    createVatGrid(
                        vatDateConfig,
                    ),
                )
            },
            *VatDateConfigDTO.vatRateValidators,
        )
    }

    suspend fun editDefaults(
        vatDateConfig: VatDateConfigDTO,
    ): VatDateConfigDTO {
        var idx =
            mainScene.addRow(
                createVatGrid(
                    vatDateConfig
                ),
            )

        navigationLoop {
            changeVatRate(vatDateConfig, idx)
            changeInvoiceDateOffset(vatDateConfig, idx)
            changeDueDateOffset(vatDateConfig, idx)

            mainScene.replaceRow(
                idx,
                Panel(
                    createVatGrid(
                        vatDateConfig,
                        padding = false,
                    ),
                    bottomTitle = isCorrectYesNo,
                    bottomTitleAlign = TextAlign.RIGHT,
                    borderStyle = th.secondary,
                ),
            )
            mainScene.display()
            var choice = reader.getRawCharIn('y', 'n')
            when (choice) {
                'y' -> {
                    mainScene.removeLast()
                    exit()
                }

                'n' -> {
                    mainScene.replaceRow(
                        idx,
                        createVatGrid(
                            vatDateConfig,
                        ),
                    )
                }
            }
        }
        return vatDateConfig
    }

    suspend fun changeQuantity(idx: Int, invoice: InvoiceInputDTO, invoiceItem: InvoiceItemDTO) {
        validationLoop(
            message = "What is the quantity of units?",
            prefill = "",
            reader = reader,
            scene = mainScene,
            onSuccess = { newValue ->
                invoiceItem.quantity = newValue.toInt()
                mainScene.replaceRow(
                    idx,
                    invoiceItemTable(invoice.invoiceItems, invoiceItem, config.currency),
                )
            },
            *InvoiceItemDTO.quantityValidators,
        )
    }

    suspend fun changeUnitPrice(idx: Int, invoice: InvoiceInputDTO, invoiceItem: InvoiceItemDTO) {
        validationLoop(
            message = "What is the unit price of the invoice item? In ${config.currency}",
            prefill = "",
            reader = reader,
            scene = mainScene,
            onSuccess = { newValue ->
                invoiceItem.unitPrice = newValue.toDouble()
                mainScene.replaceRow(
                    idx,
                    invoiceItemTable(invoice.invoiceItems, invoiceItem, config.currency),
                )
            },
            *InvoiceItemDTO.unitPriceValidators,
        )
    }

    suspend fun changeDescription(idx: Int, invoice: InvoiceInputDTO, invoiceItem: InvoiceItemDTO) {
        validationLoop(
            message = "Description of invoice item:",
            prefill = invoiceItem.description,
            reader = reader,
            scene = mainScene,
            onSuccess = { newValue ->
                invoiceItem.description = newValue
                mainScene.replaceRow(
                    idx,
                    invoiceItemTable(invoice.invoiceItems, invoiceItem, config.currency),
                )
            },
            *InvoiceItemDTO.descriptionValidators,
        )
    }

    suspend fun addDiscount(idx: Int, invoice: InvoiceInputDTO, invoiceItem: InvoiceItemDTO) {
        validationLoop(
            message = "What is the discount? (in percent)",
            prefill = "",
            reader = reader,
            scene = mainScene,
            onSuccess = { newValue ->
                invoiceItem.discount = newValue.toInt()
                mainScene.replaceRow(
                    idx,
                    invoiceItemTable(invoice.invoiceItems, invoiceItem, config.currency),
                )
            },
            *InvoiceItemDTO.discountValidators,
        )
    }

    suspend fun generateInvoice() {
        val allRecipients =
            writer.withLoading({ Repository.findAllRecipientsSortFrequency() }, message = "fetching details")
        if (allRecipients.isEmpty()) {
            return exitToMainMenu(writer, reader, "No recipients registered yet.")
        }
        val allRecipientKeybinds = createKeybinds(allRecipients)

        mainScene.addRow(renderLogo("Abrechnung!", th.tertiary, th.primaryFont))
        mainScene.addRow(recipientChoiceGrid(allRecipients))
        mainScene.addRow()
        mainScene.addRow("Choose your target.")
        mainScene.display()
        mainScene.removeLast(3)
        var choice = reader.getRawCharIn(*allRecipientKeybinds)
        val recipient = allRecipients.find { it.keybind == choice } ?: error("FATAL: Could not find recipient")

        val vatDateConfig = VatDateConfigDTO(config.vatRate, 0, config.dueDateOffset.toLong())
        val invoice =
            InvoiceInputDTO(
                invoiceDate = today.plusDays(vatDateConfig.invoiceDateOffset),
                dueDate = today.plusDays(vatDateConfig.dueDateOffset),
                vatRate = config.vatRate,
                currency = config.currency,
                invoiceItems = emptyList(),
                recipient = recipient,
            )

        val recipientLine = "To: ${recipient.companyName}"
        mainScene.addRow(recipientLine)

        var idx =
            mainScene.addRow(
                Panel(
                    createVatGrid(
                        vatDateConfig,
                        padding = false
                    ),
                    bottomTitle = isCorrectYesNo,
                    bottomTitleAlign = TextAlign.RIGHT,
                    borderStyle = th.secondary,
                ),
            )

        mainScene.display()

        choice = reader.getRawCharIn('y', 'n')
        mainScene.removeLast(2)
        when (choice) {
            'y' -> {}

            'n' -> {
                editDefaults(vatDateConfig)
                invoice.vatRate = vatDateConfig.vatRate
                invoice.invoiceDate = today.plusDays(vatDateConfig.invoiceDateOffset)
                invoice.dueDate = invoice.invoiceDate.plusDays(vatDateConfig.dueDateOffset)
            }
        }
        mainScene.addRow(recipientLine)
        var discountRound = false
        var invoiceItem = InvoiceItemDTO()
        while (true) {
            idx = mainScene.addRow(invoiceItemTable(invoice.invoiceItems, invoiceItem, config.currency))
            if (discountRound) {
                addDiscount(idx, invoice, invoiceItem)
            } else {
                changeDescription(idx, invoice, invoiceItem)

                changeUnitPrice(idx, invoice, invoiceItem)
                changeQuantity(idx, invoice, invoiceItem)


            }

            val panel =
                Panel(
                    invoiceItemTable(
                        invoice.invoiceItems,
                        invoiceItem,
                        config.currency,
                        pad = false,
                        style = th.secondary,
                    ),
                    borderStyle = th.secondary,
                )

            val explanations =
                Text(
                    """
                ${th.success("y) ")}Yes, and there are no more items.
                ${th.success("m) ")}Yes, and add one more item.
                ${th.info("d) ")}Add a discount.
                ${th.error("n) ")}No, I made a mistake.
                ${th.error("c) ")}No, cancel this last item and generate the invoice.

                ${th.error("q) ")}Quit to main menu.
                """.trimIndent(),
                    align = TextAlign.LEFT,
                )

            mainScene.replaceRow(idx, panel)
            mainScene.addRow(th.secondary("↑"))
            mainScene.addRow(th.secondary("Is this correct?"))
            mainScene.addRow()
            mainScene.addRow(explanations)
            mainScene.display()
            var choice = reader.getRawCharIn('y', 'm', 'd', 'n', 'c', 'q')
            discountRound = false
            mainScene.removeLast(5)
            when (choice) {
                'y' -> {
                    invoice.invoiceItems = invoice.invoiceItems.plus(invoiceItem)
                    break
                }

                'm' -> {
                    invoice.invoiceItems = invoice.invoiceItems.plus(invoiceItem)
                    invoiceItem = InvoiceItemDTO()
                    continue
                }

                'd' -> {
                    discountRound = true
                    continue
                }

                'n' -> {
                    continue
                }

                'c' -> {
                    if (invoice.invoiceItems.isEmpty()) {
                        return exitToMainMenu(
                            writer,
                            reader,
                            Text(th.error("No invoice items found. Aborting creation.")),
                        )
                    }
                    break
                }

                'q' -> {
                    return exitToMainMenu(writer, reader, th.error("Cancelled by user."))
                }
            }
        }
        mainScene.clear()
        mainScene.display()
        val completeInvoice = try {
            writer.withLoading({
                Repository.saveInvoice(invoice).let { Repository.findInvoiceById(it) }
            }, message = "saving invoice", scene = mainScene)
        } catch (e: Exception) {
            return exitToMainMenu(writer, reader, Text(th.error("${e.message}")))
        }
        if (completeInvoice == null) {
            return exitToMainMenu(
                writer,
                reader,
                Text(th.error("FATAL: Could not save invoice. Aborting creation."))
            )
        }

        val outputPath =
            "output/invoice_${completeInvoice.id}_${completeInvoice.recipient.companyName}.pdf".replace(" ", "_")

        val me = writer.withLoading({ Repository.getUserConfig() }, message = "resolving identity")
        val result = invoiceToPDF(outputPath, me, completeInvoice)
        if (result is Outcome.Error) {
            appScope.launch { Repository.deleteInvoice(completeInvoice.id) }
            return exitToMainMenu(writer, reader, th.error("ERROR: ${result.message}\nInvoice deleted from db."))
        } else {
            val port = me.smtpPort // compiler complaining about var smart cast
            val smtpUser = if (me.smtpUser.isNullOrBlank()) me.email else me.smtpUser!!
            if (me.smtpHost != null && port != null && me.emailPassword != null) {
                val emailUser =
                    EmailUserDTO(smtpUser, me.emailPassword.toString(), me.smtpHost.toString(), port.toInt())
                mainScene.display()
                val result =
                    writer.withLoading({
                        sendMail(
                            emailUser,
                            outputPath,
                            recipient,
                            "Invoice for ${completeInvoice.recipient.companyName}",
                            ""
                        )
                    }, "sending invoice to ${recipient.email}")
                if (result is Outcome.Error) {
                    return exitToMainMenu(
                        writer,
                        reader,
                        th.error("ERROR: ${result.message}\nPlease send invoice manually.")
                    )
                }
                return exitToMainMenu(
                    writer,
                    reader,
                    th.success("Invoice successfully sent to ${recipient.email}.")
                )
            }
            return exitToMainMenu(writer, reader, th.success("Invoice output to $outputPath."))
        }
    }

}


fun invoiceItemTable(
    existingItems: List<InvoiceItemDTO>,
    currentItem: InvoiceItemDTO,
    currency: String,
    pad: Boolean = true,
    style: TextStyle? = null,
): Widget {
    val invoiceItems = existingItems.toMutableList()
    invoiceItems.add(currentItem)
    var discountExists = false
    for (item in invoiceItems) {
        if (item.discount > 0) {
            discountExists = true
        }
    }
    val descWidth = 40

    return table {
        cellBorders = Borders.NONE
        if (style != null) {
            borderStyle = style
        }
        column(0) {
            width = ColumnWidth.Fixed(descWidth)
        }
        column(1) {
            width = ColumnWidth.Fixed(10)
        }
        column(3) {
            width = ColumnWidth.Fixed(10)
        }
        header {
            if (pad) {
                row()
            }
            row {
                cellBorders = Borders.BOTTOM
                cell("Description")
                cell("Price")
                cell("Qty")
                if (discountExists) {
                    cell("Discount")
                }
                cell("Total")
            }
        }
        body {
            invoiceItems.forEach { invoiceItem ->
                row {
                    cellBorders = Borders.RIGHT
                    cell(Text(invoiceItem.description, whitespace = Whitespace.NORMAL, width = descWidth))
                    if (invoiceItem.unitPrice > 0) {
                        cell("$currency ${invoiceItem.unitPrice}")
                    } else {
                        cell("")
                    }
                    if (invoiceItem.quantity > 0) {
                        cell("${invoiceItem.quantity}")
                    } else {
                        cell("")
                    }
                    if (discountExists) {
                        if (invoiceItem.discount > 0) {
                            cell("${invoiceItem.discount}%")
                        } else {
                            cell("")
                        }
                    }
                    val subtotalContent = if (invoiceItem.subtotal > 0) "$currency ${invoiceItem.subtotal}" else ""
                    cell(subtotalContent) { cellBorders = Borders.NONE }
                }
            }
            if (pad) {
                row {
                    cellBorders = Borders.TOP
                    cell("")
                    cell("")
                    cell("")
                    cell("")
                    if (discountExists) {
                        cell("")
                    }
                }
            }
        }
    }
}


