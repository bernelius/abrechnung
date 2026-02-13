package com.bernelius.abrechnung.PDF

import com.bernelius.abrechnung.database.DatabaseFactory
import com.bernelius.abrechnung.models.InvoiceDTO
import com.bernelius.abrechnung.models.InvoiceItemDTO
import com.bernelius.abrechnung.models.RecipientDTO
import com.bernelius.abrechnung.models.UserConfigDTO
import com.bernelius.abrechnung.repository.Repository
import com.bernelius.abrechnung.utils.Outcome
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class InvoicePDFGeneratorTest {
    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        DatabaseFactory.initTestDatabase()
        runBlocking {
            Repository.setUserConfig(
                UserConfigDTO(
                    name = "Test User",
                    address = "123 Test St",
                    postal = "12345",
                    email = "test@test.com",
                    accountNumber = "SE123456789",
                    orgNumber = "ORG123",
                ),
            )
        }
    }

    @org.junit.jupiter.api.AfterEach
    fun teardown() {
        DatabaseFactory.cleanupTestDatabase()
    }

    private val testUser =
        UserConfigDTO(
            name = "Test User",
            address = "123 Test St",
            postal = "12345",
            email = "test@test.com",
            accountNumber = "SE123456789",
            orgNumber = "ORG123",
        )

    private fun createTestInvoice(): InvoiceDTO =
        InvoiceDTO(
            id = 1,
            invoiceDate = java.time.LocalDate.of(2024, 1, 15),
            dueDate = java.time.LocalDate.of(2024, 2, 15),
            vatRate = 25,
            currency = "SEK",
            status = "pending",
            recipient =
                RecipientDTO(
                    id = 1,
                    companyName = "Client Corp",
                    address = "456 Client Ave",
                    postal = "67890",
                    email = "client@client.com",
                    orgNumber = "SE987654321",
                ),
            invoiceItems =
                listOf(
                    InvoiceItemDTO(
                        description = "Consulting Services",
                        quantity = 10,
                        unitPrice = 100.0,
                        discount = 0,
                    ),
                    InvoiceItemDTO(
                        description = "Software License",
                        quantity = 1,
                        unitPrice = 500.0,
                        discount = 10,
                    ),
                ),
        )

    @Test
    fun `invoiceToPDF returns success with valid path`() {
        val outputPath = tempDir.resolve("invoice.pdf").toString()
        val invoice = createTestInvoice()

        val result = invoiceToPDF(outputPath, testUser, invoice)

        assertTrue(result is Outcome.Success)
    }

    @Test
    fun `invoiceToPDF returns error for invalid directory`() {
        val outputPath = "/nonexistent/directory/invoice.pdf"
        val invoice = createTestInvoice()

        val result = invoiceToPDF(outputPath, testUser, invoice)

        assertTrue(result is Outcome.Error)
        assertTrue((result as Outcome.Error).message.contains("Could not save file"))
    }

    @Test
    fun `invoiceToPDF creates readable PDF file`() {
        val outputPath = tempDir.resolve("test-invoice.pdf").toString()
        val invoice = createTestInvoice()

        invoiceToPDF(outputPath, testUser, invoice)

        val file = File(outputPath)
        assertTrue(file.exists())
        assertTrue(file.length() > 0)
    }

    @Test
    fun `invoiceToPDF handles many invoice items`() {
        val outputPath = tempDir.resolve("many-items.pdf").toString()
        val invoice =
            InvoiceDTO(
                id = 1,
                invoiceDate = java.time.LocalDate.of(2024, 1, 15),
                dueDate = java.time.LocalDate.of(2024, 2, 15),
                vatRate = 25,
                currency = "SEK",
                status = "pending",
                recipient = createTestInvoice().recipient,
                invoiceItems =
                    (1..10).map { i ->
                        InvoiceItemDTO(
                            description = "Service $i",
                            quantity = i,
                            unitPrice = 100.0 * i,
                            discount = if (i % 3 == 0) 10 else 0,
                        )
                    },
            )

        val result = invoiceToPDF(outputPath, testUser, invoice)

        assertTrue(result is Outcome.Success)
        assertTrue(File(outputPath).length() > 0)
    }

    @Test
    fun `invoiceToPDF handles long company name without crashing`() {
        val outputPath = tempDir.resolve("long-name.pdf").toString()
        val longName = "A Very Long Company Name That Might Wrap To Multiple Lines In The PDF Document Which Is Completely Fine"
        val invoice =
            createTestInvoice().copy(
                recipient = createTestInvoice().recipient.copy(companyName = longName),
            )

        val result = invoiceToPDF(outputPath, testUser, invoice)

        assertTrue(result is Outcome.Success)
    }

    @Test
    fun `invoiceToPDF handles long address without crashing`() {
        val outputPath = tempDir.resolve("long-address.pdf").toString()
        val longAddress =
            "123 Extremely Long Street Name That Goes On And On And Might Need To Wrap Across Multiple Lines In The Generated PDF Document"
        val invoice =
            createTestInvoice().copy(
                recipient = createTestInvoice().recipient.copy(address = longAddress),
            )

        val result = invoiceToPDF(outputPath, testUser, invoice)

        assertTrue(result is Outcome.Success)
    }

    @Test
    fun `invoiceToPDF handles unicode characters in client data`() {
        val outputPath = tempDir.resolve("unicode.pdf").toString()
        val invoice =
            createTestInvoice().copy(
                recipient =
                    RecipientDTO(
                        id = 1,
                        companyName = "日本語クライアント株式会社",
                        address = "東京駅 1-2-3",
                        postal = "100-0001",
                        email = "クライアント@example.com",
                        orgNumber = "JP1234567890123",
                    ),
            )

        val result = invoiceToPDF(outputPath, testUser, invoice)

        assertTrue(result is Outcome.Success)
    }

    @Test
    fun `invoiceToPDF handles different currency`() {
        val outputPath = tempDir.resolve("eur-currency.pdf").toString()
        val invoice = createTestInvoice().copy(currency = "EUR")

        val result = invoiceToPDF(outputPath, testUser, invoice)

        assertTrue(result is Outcome.Success)
    }

    @Test
    fun `invoiceToPDF handles zero discount`() {
        val outputPath = tempDir.resolve("no-discount.pdf").toString()
        val invoice =
            createTestInvoice().copy(
                invoiceItems =
                    listOf(
                        InvoiceItemDTO(
                            description = "Item with no discount",
                            quantity = 1,
                            unitPrice = 100.0,
                            discount = 0,
                        ),
                    ),
            )

        val result = invoiceToPDF(outputPath, testUser, invoice)

        assertTrue(result is Outcome.Success)
    }

    @Test
    fun `invoiceToPDF handles high discount`() {
        val outputPath = tempDir.resolve("high-discount.pdf").toString()
        val invoice =
            createTestInvoice().copy(
                invoiceItems =
                    listOf(
                        InvoiceItemDTO(
                            description = "Discounted Item",
                            quantity = 1,
                            unitPrice = 1000.0,
                            discount = 50,
                        ),
                    ),
            )

        val result = invoiceToPDF(outputPath, testUser, invoice)

        assertTrue(result is Outcome.Success)
    }

    @Test
    fun `invoiceToPDF handles zero VAT`() {
        val outputPath = tempDir.resolve("no-vat.pdf").toString()
        val invoice = createTestInvoice().copy(vatRate = 0)

        val result = invoiceToPDF(outputPath, testUser, invoice)

        assertTrue(result is Outcome.Success)
    }

    @Test
    fun `invoiceToPDF handles high VAT`() {
        val outputPath = tempDir.resolve("high-vat.pdf").toString()
        val invoice = createTestInvoice().copy(vatRate = 100)

        val result = invoiceToPDF(outputPath, testUser, invoice)

        assertTrue(result is Outcome.Success)
    }

    @Test
    fun `invoice with empty recipient orgNumber renders successfully`() {
        val outputPath = tempDir.resolve("no-org-num.pdf").toString()
        val invoice =
            createTestInvoice().copy(
                recipient = createTestInvoice().recipient.copy(orgNumber = null),
            )

        val result = invoiceToPDF(outputPath, testUser, invoice)

        assertTrue(result is Outcome.Success)
    }

    @Test
    fun `invoice with empty postal renders successfully`() {
        val outputPath = tempDir.resolve("no-postal.pdf").toString()
        val invoice =
            createTestInvoice().copy(
                recipient = createTestInvoice().recipient.copy(postal = ""),
            )

        val result = invoiceToPDF(outputPath, testUser, invoice)

        assertTrue(result is Outcome.Success)
    }

    @Test
    fun `invoiceToPDF handles special characters in description`() {
        val outputPath = tempDir.resolve("special-chars.pdf").toString()
        val invoice =
            createTestInvoice().copy(
                invoiceItems =
                    listOf(
                        InvoiceItemDTO(
                            description = "Service with \"quotes\" & <brackets> & ampersand&",
                            quantity = 1,
                            unitPrice = 100.0,
                            discount = 0,
                        ),
                    ),
            )

        val result = invoiceToPDF(outputPath, testUser, invoice)

        assertTrue(result is Outcome.Success)
    }
}
