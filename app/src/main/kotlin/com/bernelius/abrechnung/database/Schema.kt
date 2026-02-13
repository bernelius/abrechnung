package com.bernelius.abrechnung.database

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.javatime.date

object UserConfigTable : Table(name = "user_config") {
    val id = integer("id").default(1)
    val name = varchar("name", length = 50)
    val address = varchar("address", length = 80)
    val postal = varchar("postal", length = 80)
    val email = varchar("email", length = 255)
    internal val encryptedAccountNumber = varchar("account_number", length = 255)
    var orgNumber = varchar("org_number", length = 20).nullable()
    val smtpHost = varchar("smtp_host", length = 255).nullable()
    val smtpPort = varchar("smtp_port", length = 5).nullable()
    val smtpUser = varchar("smtp_user", length = 255).nullable()
    internal val encryptedEmailPassword = varchar("email_password", length = 255).nullable()

    var emailPassword by OptionalEncryptedDelegate(encryptedEmailPassword)
    var accountNumber by RequiredEncryptedDelegate(encryptedAccountNumber)

    override val primaryKey = PrimaryKey(id)

    fun UpdateBuilder<*>.setAccount(value: String) {
        this[encryptedAccountNumber] = SecureVault.encrypt(value)
    }

    fun UpdateBuilder<*>.setPassword(value: String?) {
        this[encryptedEmailPassword] = value?.let { SecureVault.encrypt(it) }
    }
}

object RecipientsTable : Table() {
    val id = integer("id").autoIncrement()
    val companyName = varchar("company_name", length = 255).uniqueIndex()
    val address = varchar("address", length = 255)
    val postal = varchar("postal", length = 255)
    val email = varchar("email", length = 255)
    val orgNumber = varchar("org_number", length = 20).nullable().uniqueIndex()

    override val primaryKey = PrimaryKey(id)
}

object InvoicesTable : Table() {
    val id = integer("id").autoIncrement()
    val invoiceDate = date("invoice_date")
    val dueDate = date("due_date")
    val vatRate = integer("vat_rate").default(0)
    val currency = varchar("currency", length = 10)
    val status = varchar("status", length = 20).default("pending")

    val recipientId = integer("recipient_id").references(RecipientsTable.id)

    override val primaryKey = PrimaryKey(id)

    init {
        check { this@InvoicesTable.status inList listOf("pending", "paid", "failed", "invalid") }
    }
}

object InvoiceItemsTable : Table(name = "invoice_items") {
    val id = integer("id").autoIncrement()
    val description = varchar("description", length = 255)
    val quantity = integer("quantity")
    val unitPrice = double("unit_price")
    val discount = integer("discount").default(0)

    val invoiceId = integer("invoice_id").references(InvoicesTable.id)

    override val primaryKey = PrimaryKey(id)
}
