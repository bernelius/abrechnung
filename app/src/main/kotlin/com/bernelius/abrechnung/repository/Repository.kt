package com.bernelius.abrechnung.repository

import com.bernelius.abrechnung.cache.InvoiceCache
import com.bernelius.abrechnung.cache.RecipientCache
import com.bernelius.abrechnung.cache.UserConfigCache
import com.bernelius.abrechnung.database.InvoiceItemsTable
import com.bernelius.abrechnung.database.InvoicesTable
import com.bernelius.abrechnung.database.RecipientsTable
import com.bernelius.abrechnung.database.UserConfigTable
import com.bernelius.abrechnung.database.toInvoiceItemDTO
import com.bernelius.abrechnung.database.toRecipientDTO
import com.bernelius.abrechnung.database.toUserConfigDTO
import com.bernelius.abrechnung.dateprovider.SystemDateProvider
import com.bernelius.abrechnung.models.InvoiceDTO
import com.bernelius.abrechnung.models.InvoiceInputDTO
import com.bernelius.abrechnung.models.InvoiceItemDTO
import com.bernelius.abrechnung.models.RecipientDTO
import com.bernelius.abrechnung.models.UserConfigDTO
import com.zaxxer.hikari.util.IsolationLevel
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert

object Repository {
    suspend fun saveInvoice(invoice: InvoiceInputDTO): Int =
        suspendTransaction {
            val insertedId =
                InvoicesTable
                    .insertReturning {
                        it[invoiceDate] = invoice.invoiceDate
                        it[dueDate] = invoice.dueDate
                        it[vatRate] = invoice.vatRate
                        it[status] = "pending"
                        it[currency] = invoice.currency
                        it[recipientId] = invoice.recipient!!.id
                    }.single()[InvoicesTable.id]

            for (item in invoice.invoiceItems) {
                InvoiceItemsTable.insert {
                    it[invoiceId] = insertedId
                    it[description] = item.description
                    it[quantity] = item.quantity
                    it[unitPrice] = item.unitPrice
                    it[discount] = item.discount
                }
            }
            InvoiceCache.invalidateAll()
            insertedId
        }


    suspend fun findInvoiceById(invoiceId: Int): InvoiceDTO? =
        InvoiceCache.getOrFetch("all") { fetchAllInvoicesFromDb() }
            .find { it.id == invoiceId }

    suspend fun deleteInvoice(invoiceId: Int) {
        suspendTransaction {
            InvoicesTable.deleteWhere { InvoicesTable.id eq invoiceId }
        }
    }

    suspend fun markInvoiceAsPending(invoiceId: Int) {
        suspendTransaction {
            InvoicesTable.update({ InvoicesTable.id eq invoiceId }) {
                it[status] = "pending"
            }
        }
    }

    suspend fun markInvoiceAsPaid(invoiceId: Int) {
        suspendTransaction {
            InvoicesTable.update({ InvoicesTable.id eq invoiceId }) {
                it[status] = "paid"
            }
        }
    }

    suspend fun markInvoiceAsInvalid(invoiceId: Int) {
        suspendTransaction {
            InvoicesTable.update({ InvoicesTable.id eq invoiceId }) {
                it[status] = "invalid"
            }
        }
    }

    suspend fun findAllInvoices(
        filter: String?,
        negate: Boolean = false,
    ): List<InvoiceDTO> =
        InvoiceCache.getOrFetch("all") { fetchAllInvoicesFromDb() }
            .let { allInvoices ->
                when {
                    filter.isNullOrEmpty() -> allInvoices
                    negate -> allInvoices.filter { it.status != filter }
                    else -> allInvoices.filter { it.status == filter }
                }
            }

    /*
    * fetch all invoices from db
    * order by dueDate
    */
    private suspend fun fetchAllInvoicesFromDb(): List<InvoiceDTO> =
        suspendTransaction {
            val invoiceRows = InvoicesTable.selectAll().orderBy(InvoicesTable.dueDate).toList()

            if (invoiceRows.isEmpty()) return@suspendTransaction emptyList()

            val recipientIds = invoiceRows.map { it[InvoicesTable.recipientId] }.distinct()
            val recipientsMap =
                RecipientsTable
                    .selectAll()
                    .where { RecipientsTable.id inList recipientIds }
                    .associate { it[RecipientsTable.id] to it.toRecipientDTO() }

            val invoiceIds = invoiceRows.map { it[InvoicesTable.id] }
            val itemsMap =
                InvoiceItemsTable
                    .selectAll()
                    .where { InvoiceItemsTable.invoiceId inList invoiceIds }
                    .groupBy { it[InvoiceItemsTable.invoiceId] }
                    .mapValues { entry -> entry.value.map { it.toInvoiceItemDTO() } }

            invoiceRows.map { row ->
                InvoiceDTO(
                    id = row[InvoicesTable.id],
                    invoiceDate = row[InvoicesTable.invoiceDate],
                    dueDate = row[InvoicesTable.dueDate],
                    vatRate = row[InvoicesTable.vatRate],
                    status = row[InvoicesTable.status],
                    currency = row[InvoicesTable.currency],
                    recipient = recipientsMap.getValue(row[InvoicesTable.recipientId]),
                    invoiceItems = itemsMap[row[InvoicesTable.id]].orEmpty(),
                )
            }
        }

    suspend fun addRecipient(recipient: RecipientDTO): Int =
        suspendTransaction {
            RecipientsTable.insert {
                it[companyName] = recipient.companyName
                it[address] = recipient.address
                it[postal] = recipient.postal
                it[email] = recipient.email
                it[orgNumber] = recipient.orgNumber
            }
            RecipientsTable.selectAll().last()[RecipientsTable.id]
        }

    suspend fun updateRecipient(recipient: RecipientDTO): Int =
        suspendTransaction {
            RecipientsTable.update({ RecipientsTable.id eq recipient.id }) {
                it[companyName] = recipient.companyName
                it[address] = recipient.address
                it[postal] = recipient.postal
                it[email] = recipient.email
                it[orgNumber] = recipient.orgNumber
            }
            RecipientsTable.selectAll().last()[RecipientsTable.id]
        }

    suspend fun findRecipientByIdOrNull(recipientId: Int): RecipientDTO? =
        RecipientCache.getOrFetch("all") { fetchAllRecipientsFromDb() }
            .find { it.id == recipientId }

    suspend fun findRecipientById(recipientId: Int): RecipientDTO =
        findRecipientByIdOrNull(recipientId) ?: error("Recipient $recipientId not found")

    suspend fun findAllRecipientsSortFrequency(): List<RecipientDTO> =
        RecipientCache.getOrFetch("all") { fetchAllRecipientsSortedFromDb() }

    private suspend fun fetchAllRecipientsSortedFromDb(): List<RecipientDTO> =
        suspendTransaction {
            val invoiceCount = InvoicesTable.id.count().alias("invoice_count")
            val lastUsed = InvoicesTable.invoiceDate.max().alias("last_used")

            (RecipientsTable leftJoin InvoicesTable)
                .select(RecipientsTable.columns + invoiceCount + lastUsed)
                .groupBy(RecipientsTable.id)
                .orderBy(invoiceCount, SortOrder.DESC)
                .orderBy(lastUsed, SortOrder.DESC)
                .map { row ->
                    row.toRecipientDTO()
                }
        }

    private suspend fun fetchAllRecipientsFromDb(): List<RecipientDTO> =
        suspendTransaction {
            RecipientsTable.selectAll().map { it.toRecipientDTO() }
        }

    suspend fun findRelatedInvoiceItems(invoiceId: Int): List<InvoiceItemDTO> =
        suspendTransaction {
            InvoiceItemsTable
                .selectAll()
                .where { InvoiceItemsTable.invoiceId eq invoiceId }
                .map { it.toInvoiceItemDTO() }
        }

    suspend fun getUserConfig(): UserConfigDTO =
        UserConfigCache.getOrFetch("user") {
            suspendTransaction {
                val config = UserConfigTable.selectAll().firstOrNull()
                val userDto = config?.toUserConfigDTO() ?: UserConfigDTO()
                return@suspendTransaction userDto
            }
        }

    suspend fun setUserConfig(userConfig: UserConfigDTO) {
        suspendTransaction {
            UserConfigTable.upsert(where = { UserConfigTable.id eq 1 }) {
                it[id] = 1
                it[name] = userConfig.name
                it[address] = userConfig.address
                it[postal] = userConfig.postal
                it[email] = userConfig.email
                it.setAccount(userConfig.accountNumber)
                it[orgNumber] = userConfig.orgNumber
                it[smtpHost] = userConfig.smtpHost
                it[smtpPort] = userConfig.smtpPort
                it[smtpUser] = userConfig.smtpUser
                it.setPassword(userConfig.emailPassword)
            }
        }
    }
}
