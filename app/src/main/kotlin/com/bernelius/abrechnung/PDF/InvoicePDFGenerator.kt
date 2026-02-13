package com.bernelius.abrechnung.PDF

import com.bernelius.abrechnung.config.ConfigManager
import com.bernelius.abrechnung.config.loadLanguage
import com.bernelius.abrechnung.dateprovider.ofPattern
import com.bernelius.abrechnung.models.InvoiceDTO
import com.bernelius.abrechnung.models.UserConfigDTO
import com.bernelius.abrechnung.utils.Outcome
import com.bernelius.abrechnung.utils.hexColor
import org.openpdf.text.Document
import org.openpdf.text.Element
import org.openpdf.text.Font
import org.openpdf.text.PageSize
import org.openpdf.text.Paragraph
import org.openpdf.text.Phrase
import org.openpdf.text.Rectangle
import org.openpdf.text.pdf.BaseFont
import org.openpdf.text.pdf.PdfPCell
import org.openpdf.text.pdf.PdfPTable
import org.openpdf.text.pdf.PdfWriter
import java.awt.Color
import java.io.FileOutputStream
import java.io.IOException

private const val REGULAR_FONT_PATH = "fonts/inter/Inter_18pt-Regular.ttf"
private const val BOLD_FONT_PATH = "fonts/inter/Inter_24pt-Bold.ttf"

fun invoiceToPDF(
    outputPath: String,
    me: UserConfigDTO,
    invoice: InvoiceDTO,
): Outcome {
    try {
        generatePdfLogic(outputPath, me, invoice)
        return Outcome.Success(outputPath)
    } catch (e: Exception) {
        val userFriendlyMessage =
            when (e) {
                is IOException -> {
                    "Could not save file to $outputPath. Please check permissions. Additional information: ${e.localizedMessage}"
                }

                is SecurityException -> {
                    "Permission denied by the system."
                }

                else -> {
                    "An unexpected error occurred: ${e.localizedMessage}"
                }
            }
        return Outcome.Error(userFriendlyMessage)
    }
}

private const val LINE_SPACING = 12f
private const val DEFAULT_PADDING = 5f

private fun generatePdfLogic(
    outputPath: String,
    me: UserConfigDTO,
    invoice: InvoiceDTO,
) {
    val mainConfig = ConfigManager.loadConfig()
    val currency = mainConfig.invoiceConfig.currency
    val config = ConfigManager.loadLanguage(mainConfig.invoiceConfig.language)

    val myName = me.name
    val myEmail = me.email
    val myAddress = me.address
    val myPostal = me.postal
    val myBankAccount = me.accountNumber
    val myOrgNum = me.orgNumber ?: ""

    val baseRegular = BaseFont.createFont(REGULAR_FONT_PATH, BaseFont.IDENTITY_H, BaseFont.EMBEDDED)
    val baseBold = BaseFont.createFont(BOLD_FONT_PATH, BaseFont.IDENTITY_H, BaseFont.EMBEDDED)

    val titleFont = Font(baseBold, 20f, Font.NORMAL)
    val boldFontSmall = Font(baseBold, 8f, Font.NORMAL)
    val regularFontSmall = Font(baseRegular, 8f, Font.NORMAL)
    val whiteBoldFontSmall = Font(baseBold, 8f, Font.NORMAL, Color.WHITE)
    val redFontSmall = Font(baseRegular, 8f, Font.NORMAL, hexColor("#EE4B2B"))
    val redFontBold = Font(baseBold, 8f, Font.NORMAL, hexColor("#EE4B2B"))

    val document = Document(PageSize.A4, 50f, 50f, 50f, 50f)
    PdfWriter.getInstance(document, FileOutputStream(outputPath))

    document.open()

    document.use { document ->
        // --- Header Table ---
        val headerTable = PdfPTable(floatArrayOf(75f, 25f)).apply { widthPercentage = 100f }

        headerTable.addCell(createNoBorderCell(myName, titleFont, Element.ALIGN_LEFT))
        headerTable.addCell(createNoBorderCell(config.headline, titleFont, Element.ALIGN_RIGHT))

        document.add(headerTable)

        // Sender Info
        val senderInfo =
            StringBuilder("$myEmail\n$myAddress\n")
                .apply {
                    if (myPostal.isNotEmpty()) append("$myPostal\n")
                    if (myOrgNum.isNotEmpty()) append("${config.orgNumber}: $myOrgNum\n")
                }.toString()

        val senderTable = PdfPTable(floatArrayOf(100f)).apply { widthPercentage = 100f }
        val senderPara = Paragraph(senderInfo, regularFontSmall)
        senderPara.leading = LINE_SPACING
        senderTable.addCell(PdfPCell()).apply {
            setPadding(DEFAULT_PADDING)
            verticalAlignment = Element.ALIGN_MIDDLE
            border = Rectangle.NO_BORDER
            addElement(senderPara)
        }
        document.add(senderTable)
        document.add(Paragraph("\n"))

        // --- Client Info Table ---
        val clientTable = PdfPTable(floatArrayOf(62f, 16f, 22f)).apply { widthPercentage = 100f }

        // Table Headers
        listOf("${config.billTo}:", "${config.invoiceNumber}:", "${config.invoiceDate}:").forEach {
            clientTable.addCell(
                PdfPCell(Phrase(it, whiteBoldFontSmall)).apply {
                    backgroundColor = Color.BLACK
                    verticalAlignment = Element.ALIGN_MIDDLE
                    setPadding(DEFAULT_PADDING)
                    border = Rectangle.BOX
                },
            )
        }

        // Client Row 1
        val clientStringBuilder = StringBuilder()
        clientStringBuilder.append("${invoice.recipient.companyName}\n")
        clientStringBuilder.append("${invoice.recipient.email}\n")
        clientStringBuilder.append("${invoice.recipient.address}\n")
        if (invoice.recipient.postal.isNotEmpty()) clientStringBuilder.append("${invoice.recipient.postal}\n")
        if (!invoice.recipient.orgNumber.isNullOrEmpty()) {
            clientStringBuilder.append(
                "${config.orgNumber}: ${invoice.recipient.orgNumber}\n",
            )
        }

        val clientPara = Paragraph(clientStringBuilder.toString(), regularFontSmall)
        clientPara.setLeading(LINE_SPACING)
        clientTable.addCell(
            PdfPCell().apply {
                rowspan = 6
                // verticalAlignment = Element.ALIGN_MIDDLE
                paddingLeft = DEFAULT_PADDING
                border = Rectangle.NO_BORDER
                addElement(clientPara)
            },
        )

        clientTable.addCell(
            PdfPCell(Phrase(invoice.id.toString(), boldFontSmall)).apply {
                rowspan = 6
                horizontalAlignment = Element.ALIGN_CENTER
                // verticalAlignment = Element.ALIGN_MIDDLE
                border = Rectangle.NO_BORDER
            },
        )

        clientTable.addCell(
            PdfPCell(Phrase(invoice.invoiceDate.format(ofPattern(config.datePattern)), regularFontSmall)).apply {
                rowspan = 2
                horizontalAlignment = Element.ALIGN_RIGHT
                verticalAlignment = Element.ALIGN_MIDDLE
                border = Rectangle.BOX
                setPadding(DEFAULT_PADDING)
                paddingTop = DEFAULT_PADDING * 3
            },
        )

        clientTable.addCell(
            PdfPCell(Phrase("${config.dueDate}:", whiteBoldFontSmall)).apply {
                backgroundColor = Color.BLACK
                setPadding(DEFAULT_PADDING)
                verticalAlignment = Element.ALIGN_MIDDLE
                border = Rectangle.BOX
            },
        )

        clientTable
            .addCell(PdfPCell(Phrase(invoice.dueDate.format(ofPattern(config.datePattern)), redFontSmall)))
            .apply {
                rowspan = 2
                horizontalAlignment = Element.ALIGN_RIGHT
                verticalAlignment = Element.ALIGN_MIDDLE
                border = Rectangle.BOX
                setPadding(DEFAULT_PADDING)
                paddingTop = DEFAULT_PADDING * 3
            }

        document.add(clientTable)
        document.add(Paragraph("\n"))

        // --- Invoice Items Table ---
        val itemsTable = PdfPTable(floatArrayOf(62f, 6f, 9f, 11f, 12f)).apply { widthPercentage = 100f }

        listOf(
            config.description,
            config.quantity,
            config.price,
            config.discount,
            "${config.total} ($currency)",
        ).forEach {
            itemsTable.addCell(
                PdfPCell(Phrase(it, whiteBoldFontSmall)).apply {
                    backgroundColor = Color.BLACK
                    verticalAlignment = Element.ALIGN_MIDDLE
                    setPadding(DEFAULT_PADDING)
                },
            )
        }

        invoice.invoiceItems.forEach { item ->
            itemsTable.addCell(
                PdfPCell(Phrase(item.description, regularFontSmall)).apply {
                    setPadding(DEFAULT_PADDING)
                },
            )
            itemsTable.addCell(createCell(item.quantity.toString(), regularFontSmall, Element.ALIGN_RIGHT))
            itemsTable.addCell(createCell("%.2f".format(item.unitPrice), regularFontSmall, Element.ALIGN_RIGHT))
            itemsTable.addCell(createCell("${item.discount}%", regularFontSmall, Element.ALIGN_RIGHT))
            itemsTable.addCell(createCell("%.2f".format(item.subtotal), regularFontSmall, Element.ALIGN_RIGHT))
        }
        document.add(itemsTable)

        // --- Totals ---
        val totalsTable =
            PdfPTable(floatArrayOf(70f, 30f)).apply {
                widthPercentage = 38f
                horizontalAlignment = Element.ALIGN_RIGHT
            }

        addSimpleRow(totalsTable, "${config.subtotal}:", "%.2f".format(invoice.subtotal), regularFontSmall)
        addSimpleRow(
            totalsTable,
            "${config.vat} (${invoice.vatRate}%):",
            "%.2f".format(invoice.vatAmount),
            regularFontSmall,
        )
        addSimpleRow(totalsTable, "${config.total}:", "%.2f".format(invoice.total), boldFontSmall)

        document.add(totalsTable)
        document.add(Paragraph("\n"))

        // --- Call To Action (Yellow Box) ---
        val ctaTable = PdfPTable(floatArrayOf(62f, 19f, 19f)).apply { widthPercentage = 100f }

        ctaTable.addCell(
            PdfPCell(Phrase(config.paymentInformation, Font(baseBold, 20f, Font.NORMAL))).apply {
                colspan = 3
                backgroundColor = hexColor("#fbe870")
                verticalAlignment = Element.ALIGN_MIDDLE
                border = Rectangle.NO_BORDER
                setPadding(14f)
            },
        )

        ctaTable.addCell(
            PdfPCell(Phrase(config.paymentNote, boldFontSmall)).apply {
                rowspan = 4
                border = Rectangle.NO_BORDER
                verticalAlignment = Element.ALIGN_MIDDLE
                paddingLeft = 14f
            },
        )

        addCtaRow(ctaTable, "${config.invoiceNumber}:", invoice.id.toString(), boldFontSmall, regularFontSmall)
        addCtaRow(
            ctaTable,
            "${config.amount}:",
            currency + " " + "%.2f".format(invoice.total),
            boldFontSmall,
            regularFontSmall,
        )
        addCtaRow(ctaTable, "${config.toBankAccount}:", myBankAccount, boldFontSmall, regularFontSmall)
        addCtaRow(
            ctaTable,
            "${config.dueDate}:",
            invoice.dueDate.format(ofPattern(config.datePattern)),
            redFontBold,
            regularFontSmall,
        )

        document.add(ctaTable)
    }
}

// --- Helpers ---

private fun createNoBorderCell(
    text: String,
    font: Font,
    align: Int = Element.ALIGN_LEFT,
): PdfPCell =
    PdfPCell(Phrase(text, font)).apply {
        border = Rectangle.NO_BORDER
        verticalAlignment = Element.ALIGN_MIDDLE
        horizontalAlignment = align
        paddingLeft = DEFAULT_PADDING
    }

private fun createCell(
    text: String,
    font: Font,
    align: Int,
): PdfPCell =
    PdfPCell(Phrase(text, font)).apply {
        horizontalAlignment = align
        verticalAlignment = Element.ALIGN_MIDDLE
        setPadding(DEFAULT_PADDING)
    }

private fun addSimpleRow(
    table: PdfPTable,
    label: String,
    value: String,
    font: Font,
) {
    table.addCell(
        PdfPCell(Phrase(label, font)).apply {
            border = Rectangle.NO_BORDER
            verticalAlignment = Element.ALIGN_MIDDLE
            setPadding(DEFAULT_PADDING)
        },
    )
    table.addCell(
        PdfPCell(Phrase(value, font)).apply {
            border = Rectangle.NO_BORDER
            horizontalAlignment = Element.ALIGN_RIGHT
            verticalAlignment = Element.ALIGN_MIDDLE
            setPadding(DEFAULT_PADDING)
        },
    )
}

private fun addCtaRow(
    table: PdfPTable,
    label: String,
    value: String,
    valFont: Font,
    lblFont: Font,
) {
    table.addCell(
        PdfPCell(Phrase(label, lblFont)).apply {
            border = Rectangle.NO_BORDER
            verticalAlignment = Element.ALIGN_MIDDLE
            setPadding(DEFAULT_PADDING)
        },
    )
    table.addCell(
        PdfPCell(Phrase(value, valFont)).apply {
            border = Rectangle.NO_BORDER
            verticalAlignment = Element.ALIGN_MIDDLE
            horizontalAlignment = Element.ALIGN_RIGHT
            setPadding(DEFAULT_PADDING)
        },
    )
}
