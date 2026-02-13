package com.bernelius.abrechnung.config

import com.bernelius.abrechnung.models.DatePattern

val builtinLanguages =
    listOf(
        Language(
            name = "en",
            headline = "INVOICE",
            orgNumber = "Org. number",
            billTo = "Bill to",
            invoiceNumber = "Invoice Number",
            invoiceDate = "Invoice Date",
            datePattern = DatePattern("dd.MM.yyyy"),
            dueDate = "Due Date",
            description = "Description",
            quantity = "Qty",
            discount = "Discount",
            price = "Price",
            subtotal = "Subtotal",
            total = "Total",
            amount = "Amount",
            toBankAccount = "To bank account",
            vat = "VAT",
            paymentInformation = "Payment Information",
            paymentNote = "Please include the invoice number as a comment when making the payment.",
        ),
        Language(
            name = "no",
            headline = "FAKTURA",
            orgNumber = "Org. nummer",
            billTo = "Til",
            invoiceNumber = "Fakturanummer",
            invoiceDate = "Fakturadato",
            datePattern = DatePattern("dd.MM.yyyy"),
            dueDate = "Forfallsdato",
            description = "Beskrivelse",
            quantity = "Antall",
            discount = "Rabatt",
            price = "Pris",
            subtotal = "Delsum",
            total = "Total",
            amount = "Beløp",
            toBankAccount = "Til bankkonto",
            vat = "MVA",
            paymentInformation = "Betalingsinformasjon",
            paymentNote = "Vennligst inkludér fakturanummeret som kommentar på betalingen.",
        ),
    )
