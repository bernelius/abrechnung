package com.bernelius.abrechnung.repository

import com.bernelius.abrechnung.cache.InvoiceCache
import com.bernelius.abrechnung.cache.RecipientCache
import com.bernelius.abrechnung.cache.UserConfigCache
import com.bernelius.abrechnung.database.DatabaseFactory
import com.bernelius.abrechnung.models.RecipientDTO
import com.bernelius.abrechnung.models.UserConfigDTO
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RepositoryTest {
    @BeforeEach
    fun setup() {
        UserConfigCache.invalidateAll()
        RecipientCache.invalidateAll()
        InvoiceCache.invalidateAll()
        DatabaseFactory.initTestDatabase()
    }

    @org.junit.jupiter.api.AfterEach
    fun teardown() {
        DatabaseFactory.cleanupTestDatabase()
        UserConfigCache.invalidateAll()
        RecipientCache.invalidateAll()
        InvoiceCache.invalidateAll()
    }

    @Test
    fun `addRecipient returns id and can be retrieved`() = runBlocking {
        val recipient =
            RecipientDTO(
                companyName = "Test Corp",
                address = "123 Test St",
                postal = "12345",
                email = "test@test.com",
                orgNumber = "SE123456789",
            )

        val id = Repository.addRecipient(recipient)

        assertTrue(id > 0)

        val retrieved = Repository.findRecipientById(id)
        assertEquals("Test Corp", retrieved.companyName)
        assertEquals("test@test.com", retrieved.email)
    }

    @Test
    fun `updateRecipient modifies existing record`() = runBlocking {
        val id =
            Repository.addRecipient(
                RecipientDTO(
                    companyName = "Original",
                    address = "Old Address",
                    postal = "00000",
                    email = "old@test.com",
                ),
            )

        Repository.updateRecipient(
            RecipientDTO(
                id = id,
                companyName = "Updated",
                address = "New Address",
                postal = "11111",
                email = "new@test.com",
            ),
        )

        val retrieved = Repository.findRecipientById(id)
        assertEquals("Updated", retrieved.companyName)
        assertEquals("new@test.com", retrieved.email)
    }

    @Test
    fun `findAllRecipientsSortFrequency returns all recipients`() = runBlocking {
        Repository.addRecipient(RecipientDTO(companyName = "Corp A", address = "A", postal = "1", email = "a@a.com"))
        Repository.addRecipient(RecipientDTO(companyName = "Corp B", address = "B", postal = "2", email = "b@b.com"))

        val all = Repository.findAllRecipientsSortFrequency()

        assertEquals(2, all.size)
    }

    @Test
    fun `findRecipientByIdOrNull returns null for nonexistent`() = runBlocking {
        assertNull(Repository.findRecipientByIdOrNull(99999))
    }

    @Test
    fun `saveInvoice and findInvoiceById`() = runBlocking {
        val recipientId =
            Repository.addRecipient(
                RecipientDTO(companyName = "Client", address = "St", postal = "1", email = "c@c.com"),
            )
        val recipient = Repository.findRecipientById(recipientId)

        val invoiceId =
            Repository.saveInvoice(
                com.bernelius.abrechnung.models.InvoiceInputDTO(
                    invoiceDate = LocalDate.of(2024, 1, 1),
                    dueDate = LocalDate.of(2024, 1, 31),
                    vatRate = 25,
                    currency = "SEK",
                    invoiceItems =
                        listOf(
                            com.bernelius.abrechnung.models.InvoiceItemDTO(
                                description = "Consulting",
                                quantity = 10,
                                unitPrice = 100.0,
                                discount = 0,
                            ),
                        ),
                    recipient = recipient,
                ),
            )

        val invoice = Repository.findInvoiceById(invoiceId)!!

        assertNotNull(invoice)
        assertEquals(25, invoice.vatRate)
        assertEquals("SEK", invoice.currency)
        assertEquals("pending", invoice.status)
        assertEquals(1, invoice.invoiceItems.size)
        assertEquals("Consulting", invoice.invoiceItems[0].description)
    }

    @Test
    fun `markInvoiceAsPaid changes status`() = runBlocking {
        val recipientId =
            Repository.addRecipient(
                RecipientDTO(companyName = "Client", address = "St", postal = "1", email = "c@c.com"),
            )
        val invoiceId =
            Repository.saveInvoice(
                com.bernelius.abrechnung.models.InvoiceInputDTO(
                    invoiceDate = LocalDate.of(2024, 1, 1),
                    dueDate = LocalDate.of(2024, 1, 31),
                    vatRate = 25,
                    currency = "SEK",
                    invoiceItems = emptyList(),
                    recipient = Repository.findRecipientById(recipientId),
                ),
            )

        Repository.markInvoiceAsPaid(invoiceId)

        val invoice = Repository.findInvoiceById(invoiceId)!!
        assertEquals("paid", invoice.status)
    }

    @Test
    fun `deleteInvoice removes invoice`() = runBlocking {
        val recipientId =
            Repository.addRecipient(
                RecipientDTO(companyName = "Client", address = "St", postal = "1", email = "c@c.com"),
            )
        val invoiceId =
            Repository.saveInvoice(
                com.bernelius.abrechnung.models.InvoiceInputDTO(
                    invoiceDate = LocalDate.of(2024, 1, 1),
                    dueDate = LocalDate.of(2024, 1, 31),
                    vatRate = 25,
                    currency = "SEK",
                    invoiceItems = emptyList(),
                    recipient = Repository.findRecipientById(recipientId),
                ),
            )

        Repository.deleteInvoice(invoiceId)

        assertNull(Repository.findInvoiceById(invoiceId))
    }

    @Test
    fun `findAllInvoices can filter by status`() = runBlocking {
        val recipientId =
            Repository.addRecipient(
                RecipientDTO(companyName = "Client", address = "St", postal = "1", email = "c@c.com"),
            )
        val recipient = Repository.findRecipientById(recipientId)

        val id1 =
            Repository.saveInvoice(
                com.bernelius.abrechnung.models.InvoiceInputDTO(
                    invoiceDate = LocalDate.of(2024, 1, 1),
                    dueDate = LocalDate.of(2024, 1, 31),
                    vatRate = 25,
                    currency = "SEK",
                    invoiceItems = emptyList(),
                    recipient = recipient,
                ),
            )
        val id2 =
            Repository.saveInvoice(
                com.bernelius.abrechnung.models.InvoiceInputDTO(
                    invoiceDate = LocalDate.of(2024, 1, 1),
                    dueDate = LocalDate.of(2024, 1, 31),
                    vatRate = 25,
                    currency = "SEK",
                    invoiceItems = emptyList(),
                    recipient = recipient,
                ),
            )

        Repository.markInvoiceAsPaid(id1)

        val pending = Repository.findAllInvoices("pending")
        val paid = Repository.findAllInvoices("paid")

        assertEquals(1, pending.size)
        assertEquals(id2, pending[0].id)
        assertEquals(1, paid.size)
        assertEquals(id1, paid[0].id)
    }

    @Test
    fun `setUserConfig and getUserConfig`() = runBlocking {
        val config =
            UserConfigDTO(
                name = "John Doe",
                address = "Main St 1",
                postal = "12345",
                email = "john@test.com",
                accountNumber = "SE123456789",
                orgNumber = "ORG123",
            )

        Repository.setUserConfig(config)

        val retrieved = Repository.getUserConfig()

        assertEquals("John Doe", retrieved.name)
        assertEquals("john@test.com", retrieved.email)
        assertEquals("SE123456789", retrieved.accountNumber)
    }

    @Test
    fun `getUserConfig returns default when not set`() = runBlocking {
        val config = Repository.getUserConfig()
        // When not set, returns a default UserConfigDTO with empty strings
        assertEquals("", config.name)
        assertEquals("", config.email)
    }

    @Test
    fun `findAllRecipientsSortFrequency orders by invoice count`() = runBlocking {
        val r1 = RecipientDTO(companyName = "Rare", address = "A", postal = "1", email = "r@r.com")
        val r2 = RecipientDTO(companyName = "Frequent", address = "B", postal = "2", email = "f@f.com")

        val id1 = Repository.addRecipient(r1)
        val id2 = Repository.addRecipient(r2)

        val recipient2 = Repository.findRecipientById(id2)
        repeat(3) {
            Repository.saveInvoice(
                com.bernelius.abrechnung.models.InvoiceInputDTO(
                    invoiceDate = LocalDate.now(),
                    dueDate = LocalDate.now().plusDays(30),
                    vatRate = 25,
                    currency = "SEK",
                    invoiceItems = emptyList(),
                    recipient = recipient2,
                ),
            )
        }

        // Invalidate cache to ensure fresh sorted data is fetched
        RecipientCache.invalidateAll()

        val sorted = Repository.findAllRecipientsSortFrequency()

        assertEquals(id2, sorted[0].id)
    }
}
