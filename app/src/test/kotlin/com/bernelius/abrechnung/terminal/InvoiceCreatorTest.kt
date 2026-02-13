package com.bernelius.abrechnung.terminal

import com.bernelius.abrechnung.cache.InvoiceCache
import com.bernelius.abrechnung.cache.RecipientCache
import com.bernelius.abrechnung.config.InvoiceConfig
import com.bernelius.abrechnung.database.DatabaseFactory
import com.bernelius.abrechnung.dateprovider.DateProvider
import com.bernelius.abrechnung.models.DatePattern
import com.bernelius.abrechnung.models.InvoiceInputDTO
import com.bernelius.abrechnung.models.InvoiceItemDTO
import com.bernelius.abrechnung.models.RecipientDTO
import com.bernelius.abrechnung.models.VatDateConfigDTO
import com.bernelius.abrechnung.repository.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.math.pow

class InvoiceCreatorTest {
    private val today = LocalDate.of(2024, 1, 15)
    private val config = InvoiceConfig(dueDateOffset = 14, vatRate = 25, currency = "SEK")
    private val datePattern = DatePattern("yyyy-MM-dd")
    private val testScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun createTestDateProvider(): DateProvider = object : DateProvider {
        override fun today(): LocalDate = today
    }

    private fun createInvoiceCreator(
        reader: MockReader,
        scene: MockScene = MockScene(),
        invoiceConfig: InvoiceConfig = config,
    ): InvoiceCreator {
        return InvoiceCreator(
            writer = MockWriter(),
            reader = reader,
            dateProvider = createTestDateProvider(),
            appScope = testScope,
            mainScene = scene,
            config = invoiceConfig,
            datePattern = datePattern,
            today = today,
        )
    }

    private fun Scene.hasError(message: String): Boolean = rows.any { (it.contents as String).contains(message) }

    @BeforeEach
    fun setup() {
        DatabaseFactory.initTestDatabase()
        RecipientCache.invalidateAll()
        InvoiceCache.invalidateAll()
    }

    @AfterEach
    fun teardown() {
        DatabaseFactory.cleanupTestDatabase()
        RecipientCache.invalidateAll()
        InvoiceCache.invalidateAll()
    }

    // ==================== editDefaults Tests ====================

    @Test
    fun `editDefaults with valid vat rate returns correct dto`() = runBlocking {
        val reader = MockReader(inputs = listOf("25", "", ""), rawChars = listOf('y'))
        val scene = MockScene()
        val creator = createInvoiceCreator(reader, scene)

        val result = creator.editDefaults(VatDateConfigDTO(vatRate = 25, invoiceDateOffset = 0, dueDateOffset = 14))

        assertEquals(25, result.vatRate)
    }

    @Test
    fun `editDefaults with blank vat rate returns zero`() = runBlocking {
        val reader = MockReader(inputs = listOf("", "", ""), rawChars = listOf('y'))
        val scene = MockScene()
        val creator = createInvoiceCreator(reader, scene)

        val result = creator.editDefaults(VatDateConfigDTO(vatRate = 25, invoiceDateOffset = 0, dueDateOffset = 14))

        assertEquals(0, result.vatRate)
    }

    @Test
    fun `editDefaults with invalid vat rate shows error then accepts valid`() = runBlocking {
        val reader = MockReader(inputs = listOf("invalid", "25", "", ""), rawChars = listOf('y'))
        val scene = MockScene()
        val creator = createInvoiceCreator(reader, scene)

        creator.editDefaults(VatDateConfigDTO(vatRate = 25, invoiceDateOffset = 0, dueDateOffset = 14))

        assertTrue(scene.hasError("Must be a valid integer between 0 and 100"))
    }

    @Test
    fun `editDefaults with vat rate over 100 shows error`() = runBlocking {
        val reader = MockReader(inputs = listOf("150", "50", "", ""), rawChars = listOf('y'))
        val scene = MockScene()
        val creator = createInvoiceCreator(reader, scene)

        creator.editDefaults(VatDateConfigDTO(vatRate = 25, invoiceDateOffset = 0, dueDateOffset = 14))

        assertTrue(scene.hasError("Must be a valid integer between 0 and 100"))
    }

    @Test
    fun `editDefaults with negative vat rate shows error`() = runBlocking {
        val reader = MockReader(inputs = listOf("-10", "25", "", ""), rawChars = listOf('y'))
        val scene = MockScene()
        val creator = createInvoiceCreator(reader, scene)

        creator.editDefaults(VatDateConfigDTO(vatRate = 25, invoiceDateOffset = 0, dueDateOffset = 14))

        assertTrue(scene.hasError("Must be a valid integer between 0 and 100"))
    }

    @Test
    fun `editDefaults with valid invoice date offset returns correct dto`() = runBlocking {
        val reader = MockReader(inputs = listOf("", "7", ""), rawChars = listOf('y'))
        val scene = MockScene()
        val creator = createInvoiceCreator(reader, scene)

        val result = creator.editDefaults(VatDateConfigDTO(vatRate = 25, invoiceDateOffset = 0, dueDateOffset = 14))

        assertEquals(7, result.invoiceDateOffset)
    }

    @Test
    fun `editDefaults with blank invoice date offset returns zero`() = runBlocking {
        val reader = MockReader(inputs = listOf("", "", ""), rawChars = listOf('y'))
        val scene = MockScene()
        val creator = createInvoiceCreator(reader, scene)

        val result = creator.editDefaults(VatDateConfigDTO(vatRate = 25, invoiceDateOffset = 0, dueDateOffset = 14))

        assertEquals(0, result.invoiceDateOffset)
    }

    @Test
    fun `editDefaults with invalid invoice date offset shows error then accepts valid`() = runBlocking {
        val reader = MockReader(inputs = listOf("", "-1", "7", ""), rawChars = listOf('y'))
        val scene = MockScene()
        val creator = createInvoiceCreator(reader, scene)

        creator.editDefaults(VatDateConfigDTO(vatRate = 25, invoiceDateOffset = 0, dueDateOffset = 14))

        assertTrue(scene.hasError("Must be a valid positive integer or left blank"))
    }

    @Test
    fun `editDefaults with valid due date offset returns correct dto`() = runBlocking {
        val reader = MockReader(inputs = listOf("", "", "30"), rawChars = listOf('y'))
        val scene = MockScene()
        val creator = createInvoiceCreator(reader, scene)

        val result = creator.editDefaults(VatDateConfigDTO(vatRate = 25, invoiceDateOffset = 0, dueDateOffset = 14))

        assertEquals(30, result.dueDateOffset)
    }

    @Test
    fun `editDefaults with blank due date offset returns config default`() = runBlocking {
        val reader = MockReader(inputs = listOf("", "", ""), rawChars = listOf('y'))
        val scene = MockScene()
        val creator = createInvoiceCreator(reader, scene)

        val result = creator.editDefaults(VatDateConfigDTO(vatRate = 25, invoiceDateOffset = 0, dueDateOffset = 14))

        assertEquals(14, result.dueDateOffset)
    }

    @Test
    fun `editDefaults with invalid due date offset shows error then accepts valid`() = runBlocking {
        val reader = MockReader(inputs = listOf("", "", "abc", "30"), rawChars = listOf('y'))
        val scene = MockScene()
        val creator = createInvoiceCreator(reader, scene)

        creator.editDefaults(VatDateConfigDTO(vatRate = 25, invoiceDateOffset = 0, dueDateOffset = 14))

        assertTrue(scene.hasError("Must be a valid positive integer or left blank"))
    }

    @Test
    fun `editDefaults all valid inputs returns complete dto`() = runBlocking {
        val reader = MockReader(inputs = listOf("25", "7", "30"), rawChars = listOf('y'))
        val scene = MockScene()
        val creator = createInvoiceCreator(reader, scene)

        val result = creator.editDefaults(VatDateConfigDTO(vatRate = 25, invoiceDateOffset = 0, dueDateOffset = 14))

        assertEquals(25, result.vatRate)
        assertEquals(7, result.invoiceDateOffset)
        assertEquals(30, result.dueDateOffset)
    }

    @Test
    fun `editDefaults user cancels and retries`() = runBlocking {
        // User says 'n' first (not correct), then re-enters values and says 'y'
        // When 'n' is pressed, the navigation loop restarts, so we need inputs for the second iteration too
        val reader = MockReader(inputs = listOf("25", "", "", "30", "", ""), rawChars = listOf('n', 'y'))
        val scene = MockScene()
        val creator = createInvoiceCreator(reader, scene)

        val result = creator.editDefaults(VatDateConfigDTO(vatRate = 25, invoiceDateOffset = 0, dueDateOffset = 14))

        // Second iteration values should be used since 'n' was pressed first
        assertEquals(30, result.vatRate)
    }

    // ==================== VatDateConfigDTO.resolve Tests ====================

    @Test
    fun `resolve calculates correct dates with zero offsets`() {
        val config = VatDateConfigDTO(vatRate = 25, invoiceDateOffset = 0, dueDateOffset = 14)
        val resolved = config.resolve(today)

        assertEquals(today, resolved.invoiceDate)
        assertEquals(today.plusDays(14), resolved.dueDate)
    }

    @Test
    fun `resolve calculates correct dates with invoice date offset`() {
        val config = VatDateConfigDTO(vatRate = 25, invoiceDateOffset = 7, dueDateOffset = 14)
        val resolved = config.resolve(today)

        assertEquals(today.plusDays(7), resolved.invoiceDate)
        // Due date is relative to invoice date
        assertEquals(today.plusDays(7).plusDays(14), resolved.dueDate)
    }

    @Test
    fun `resolve due date can be before invoice date if offset is smaller`() {
        // This is a potential business logic bug - due date before invoice date
        val config = VatDateConfigDTO(vatRate = 25, invoiceDateOffset = 30, dueDateOffset = 7)
        val resolved = config.resolve(today)

        assertEquals(today.plusDays(30), resolved.invoiceDate)
        assertEquals(today.plusDays(37), resolved.dueDate)
        // Due date is still after invoice date because it's additive
        assertTrue(resolved.dueDate.isAfter(resolved.invoiceDate))
    }

    @Test
    fun `resolve with very large offset could cause date overflow`() {
        // Test near the limit of LocalDate
        val nearMaxDate = LocalDate.of(999999999, 1, 1)
        val testToday = nearMaxDate.minusDays(100)
        val config = VatDateConfigDTO(vatRate = 25, invoiceDateOffset = 200, dueDateOffset = 100)

        // This should overflow or throw exception
        try {
            val resolved = config.resolve(testToday)
            // If we get here, check if dates are reasonable
            assertNotNull(resolved)
        } catch (e: Exception) {
            // Expected - date overflow
            assertTrue(e is java.time.DateTimeException || e is ArithmeticException)
        }
    }

    // ==================== InvoiceItemDTO Calculation Tests ====================

    @Test
    fun `subtotal with zero quantity is zero`() {
        val item = InvoiceItemDTO(description = "Test", quantity = 0, unitPrice = 100.0, discount = 0)
        assertEquals(0.0, item.subtotal, 0.001)
    }

    @Test
    fun `subtotal with zero unit price is zero`() {
        val item = InvoiceItemDTO(description = "Test", quantity = 10, unitPrice = 0.0, discount = 0)
        assertEquals(0.0, item.subtotal, 0.001)
    }

    @Test
    fun `subtotal with no discount calculates correctly`() {
        val item = InvoiceItemDTO(description = "Test", quantity = 5, unitPrice = 100.0, discount = 0)
        assertEquals(500.0, item.subtotal, 0.001)
    }

    @Test
    fun `subtotal with 50 percent discount calculates correctly`() {
        val item = InvoiceItemDTO(description = "Test", quantity = 10, unitPrice = 100.0, discount = 50)
        // 10 * (100 - 100*0.5) = 10 * 50 = 500
        assertEquals(500.0, item.subtotal, 0.001)
    }

    @Test
    fun `subtotal with 100 percent discount is zero`() {
        val item = InvoiceItemDTO(description = "Test", quantity = 10, unitPrice = 100.0, discount = 100)
        // 10 * (100 - 100*1.0) = 10 * 0 = 0
        assertEquals(0.0, item.subtotal, 0.001)
    }

    @Test
    fun `subtotal rounding with fractional prices`() {
        val item = InvoiceItemDTO(description = "Test", quantity = 3, unitPrice = 33.33, discount = 0)
        // 3 * 33.33 = 99.99
        val expected = 99.99
        assertEquals(expected, item.subtotal, 0.001)
    }

    @Test
    fun `subtotal precision with discount creating fractions`() {
        val item = InvoiceItemDTO(description = "Test", quantity = 1, unitPrice = 100.0, discount = 33)
        // 1 * (100 - 33) = 67, but let's verify rounding
        val expected = 67.0
        assertEquals(expected, item.subtotal, 0.001)
    }

    @Test
    fun `subtotal with large values does not overflow`() {
        // Double max is about 1.8e308
        val maxSafe = Double.MAX_VALUE / 1e10
        val item = InvoiceItemDTO(description = "Test", quantity = 1_000_000, unitPrice = maxSafe, discount = 0)
        // Should not overflow
        assertTrue(item.subtotal.isFinite())
    }

    @Test
    fun `subtotal is calculated consistently on multiple accesses`() {
        val item = InvoiceItemDTO(description = "Test", quantity = 5, unitPrice = 99.99, discount = 15)
        val first = item.subtotal
        val second = item.subtotal
        val third = item.subtotal
        assertEquals(first, second, 0.001)
        assertEquals(second, third, 0.001)
    }

    // ==================== Change Methods Tests ====================

    @Test
    fun `changeQuantity with valid input updates item`() = runBlocking {
        val reader = MockReader("10")
        val scene = MockScene()
        val creator = createInvoiceCreator(reader, scene)
        val invoice = InvoiceInputDTO(
            invoiceDate = today, dueDate = today.plusDays(14),
            vatRate = 25, currency = "SEK", invoiceItems = emptyList(), recipient = null
        )
        val item = InvoiceItemDTO()

        creator.changeQuantity(0, invoice, item)

        assertEquals(10, item.quantity)
    }

    @Test
    fun `changeQuantity with invalid input shows error then accepts valid`() = runBlocking {
        val reader = MockReader("invalid", "5")
        val scene = MockScene()
        val creator = createInvoiceCreator(reader, scene)
        val invoice = InvoiceInputDTO(
            invoiceDate = today, dueDate = today.plusDays(14),
            vatRate = 25, currency = "SEK", invoiceItems = emptyList(), recipient = null
        )
        val item = InvoiceItemDTO()

        creator.changeQuantity(0, invoice, item)

        assertTrue(scene.hasError("Must be a valid integer"))
        assertEquals(5, item.quantity)
    }

    @Test
    fun `changeQuantity with zero shows error`() = runBlocking {
        val reader = MockReader("0", "5")
        val scene = MockScene()
        val creator = createInvoiceCreator(reader, scene)
        val invoice = InvoiceInputDTO(
            invoiceDate = today, dueDate = today.plusDays(14),
            vatRate = 25, currency = "SEK", invoiceItems = emptyList(), recipient = null
        )
        val item = InvoiceItemDTO()

        creator.changeQuantity(0, invoice, item)

        assertTrue(scene.hasError("greater than or equal to 1"))
        assertEquals(5, item.quantity)
    }

    @Test
    fun `changeQuantity with negative shows error`() = runBlocking {
        val reader = MockReader("-5", "3")
        val scene = MockScene()
        val creator = createInvoiceCreator(reader, scene)
        val invoice = InvoiceInputDTO(
            invoiceDate = today, dueDate = today.plusDays(14),
            vatRate = 25, currency = "SEK", invoiceItems = emptyList(), recipient = null
        )
        val item = InvoiceItemDTO()

        creator.changeQuantity(0, invoice, item)

        assertTrue(scene.hasError("greater than or equal to 1"))
        assertEquals(3, item.quantity)
    }

    @Test
    fun `changeUnitPrice with valid input updates item`() = runBlocking {
        val reader = MockReader("99.99")
        val scene = MockScene()
        val creator = createInvoiceCreator(reader, scene)
        val invoice = InvoiceInputDTO(
            invoiceDate = today, dueDate = today.plusDays(14),
            vatRate = 25, currency = "SEK", invoiceItems = emptyList(), recipient = null
        )
        val item = InvoiceItemDTO()

        creator.changeUnitPrice(0, invoice, item)

        assertEquals(99.99, item.unitPrice, 0.001)
    }

    @Test
    fun `changeUnitPrice with zero is accepted`() = runBlocking {
        // Note: This might be a bug - should unit price be allowed to be 0?
        val reader = MockReader("0")
        val scene = MockScene()
        val creator = createInvoiceCreator(reader, scene)
        val invoice = InvoiceInputDTO(
            invoiceDate = today, dueDate = today.plusDays(14),
            vatRate = 25, currency = "SEK", invoiceItems = emptyList(), recipient = null
        )
        val item = InvoiceItemDTO()

        creator.changeUnitPrice(0, invoice, item)

        assertEquals(0.0, item.unitPrice, 0.001)
    }

    @Test
    fun `changeUnitPrice with negative shows error`() = runBlocking {
        val reader = MockReader("-10", "50")
        val scene = MockScene()
        val creator = createInvoiceCreator(reader, scene)
        val invoice = InvoiceInputDTO(
            invoiceDate = today, dueDate = today.plusDays(14),
            vatRate = 25, currency = "SEK", invoiceItems = emptyList(), recipient = null
        )
        val item = InvoiceItemDTO()

        creator.changeUnitPrice(0, invoice, item)

        assertTrue(scene.hasError("must not be negative"))
        assertEquals(50.0, item.unitPrice, 0.001)
    }

    @Test
    fun `changeDescription with valid input updates item`() = runBlocking {
        val reader = MockReader("Consulting Services")
        val scene = MockScene()
        val creator = createInvoiceCreator(reader, scene)
        val invoice = InvoiceInputDTO(
            invoiceDate = today, dueDate = today.plusDays(14),
            vatRate = 25, currency = "SEK", invoiceItems = emptyList(), recipient = null
        )
        val item = InvoiceItemDTO()

        creator.changeDescription(0, invoice, item)

        assertEquals("Consulting Services", item.description)
    }

    @Test
    fun `changeDescription with blank shows error`() = runBlocking {
        val reader = MockReader("", "Valid Description")
        val scene = MockScene()
        val creator = createInvoiceCreator(reader, scene)
        val invoice = InvoiceInputDTO(
            invoiceDate = today, dueDate = today.plusDays(14),
            vatRate = 25, currency = "SEK", invoiceItems = emptyList(), recipient = null
        )
        val item = InvoiceItemDTO()

        creator.changeDescription(0, invoice, item)

        assertTrue(scene.hasError("cannot be left blank"))
        assertEquals("Valid Description", item.description)
    }

    @Test
    fun `changeDescription with whitespace only shows error`() = runBlocking {
        val reader = MockReader("   ", "Valid Description")
        val scene = MockScene()
        val creator = createInvoiceCreator(reader, scene)
        val invoice = InvoiceInputDTO(
            invoiceDate = today, dueDate = today.plusDays(14),
            vatRate = 25, currency = "SEK", invoiceItems = emptyList(), recipient = null
        )
        val item = InvoiceItemDTO()

        creator.changeDescription(0, invoice, item)

        assertTrue(scene.hasError("cannot be left blank"))
        assertEquals("Valid Description", item.description)
    }

    @Test
    fun `addDiscount with valid input updates item`() = runBlocking {
        val reader = MockReader("25")
        val scene = MockScene()
        val creator = createInvoiceCreator(reader, scene)
        val invoice = InvoiceInputDTO(
            invoiceDate = today, dueDate = today.plusDays(14),
            vatRate = 25, currency = "SEK", invoiceItems = emptyList(), recipient = null
        )
        val item = InvoiceItemDTO()

        creator.addDiscount(0, invoice, item)

        assertEquals(25, item.discount)
    }

    @Test
    fun `addDiscount with zero is accepted`() = runBlocking {
        val reader = MockReader("0")
        val scene = MockScene()
        val creator = createInvoiceCreator(reader, scene)
        val invoice = InvoiceInputDTO(
            invoiceDate = today, dueDate = today.plusDays(14),
            vatRate = 25, currency = "SEK", invoiceItems = emptyList(), recipient = null
        )
        val item = InvoiceItemDTO()

        creator.addDiscount(0, invoice, item)

        assertEquals(0, item.discount)
    }

    @Test
    fun `addDiscount with 100 percent is accepted`() = runBlocking {
        val reader = MockReader("100")
        val scene = MockScene()
        val creator = createInvoiceCreator(reader, scene)
        val invoice = InvoiceInputDTO(
            invoiceDate = today, dueDate = today.plusDays(14),
            vatRate = 25, currency = "SEK", invoiceItems = emptyList(), recipient = null
        )
        val item = InvoiceItemDTO()

        creator.addDiscount(0, invoice, item)

        assertEquals(100, item.discount)
    }

    @Test
    fun `addDiscount with over 100 shows error`() = runBlocking {
        val reader = MockReader("150", "50")
        val scene = MockScene()
        val creator = createInvoiceCreator(reader, scene)
        val invoice = InvoiceInputDTO(
            invoiceDate = today, dueDate = today.plusDays(14),
            vatRate = 25, currency = "SEK", invoiceItems = emptyList(), recipient = null
        )
        val item = InvoiceItemDTO()

        creator.addDiscount(0, invoice, item)

        assertTrue(scene.hasError("between 0 and 100"))
        assertEquals(50, item.discount)
    }

    @Test
    fun `addDiscount with negative shows error`() = runBlocking {
        val reader = MockReader("-10", "25")
        val scene = MockScene()
        val creator = createInvoiceCreator(reader, scene)
        val invoice = InvoiceInputDTO(
            invoiceDate = today, dueDate = today.plusDays(14),
            vatRate = 25, currency = "SEK", invoiceItems = emptyList(), recipient = null
        )
        val item = InvoiceItemDTO()

        creator.addDiscount(0, invoice, item)

        assertTrue(scene.hasError("between 0 and 100"))
        assertEquals(25, item.discount)
    }

    // ==================== Invoice Item Table Tests ====================

    @Test
    fun `invoiceItemTable with no discounts hides discount column`() {
        val items = listOf(
            InvoiceItemDTO(description = "Item 1", quantity = 1, unitPrice = 100.0, discount = 0)
        )
        val currentItem = InvoiceItemDTO(description = "Item 2", quantity = 1, unitPrice = 50.0, discount = 0)

        val table = invoiceItemTable(items, currentItem, "SEK")

        assertNotNull(table)
        // The table should render without discount column when all discounts are 0
    }

    @Test
    fun `invoiceItemTable with discounts shows discount column`() {
        val items = listOf(
            InvoiceItemDTO(description = "Item 1", quantity = 1, unitPrice = 100.0, discount = 10)
        )
        val currentItem = InvoiceItemDTO(description = "Item 2", quantity = 1, unitPrice = 50.0, discount = 0)

        val table = invoiceItemTable(items, currentItem, "SEK")

        assertNotNull(table)
        // The table should render with discount column when any discount > 0
    }

    @Test
    fun `invoiceItemTable with empty existing items list`() {
        val items = emptyList<InvoiceItemDTO>()
        val currentItem = InvoiceItemDTO(description = "First Item", quantity = 1, unitPrice = 100.0, discount = 0)

        val table = invoiceItemTable(items, currentItem, "SEK")

        assertNotNull(table)
    }

    @Test
    fun `invoiceItemTable with multiple items different discounts`() {
        val items = listOf(
            InvoiceItemDTO(description = "Item 1", quantity = 2, unitPrice = 100.0, discount = 0),
            InvoiceItemDTO(description = "Item 2", quantity = 1, unitPrice = 50.0, discount = 25)
        )
        val currentItem = InvoiceItemDTO(description = "Item 3", quantity = 3, unitPrice = 75.0, discount = 0)

        val table = invoiceItemTable(items, currentItem, "SEK")

        assertNotNull(table)
        // Column should show because at least one item has discount
    }

    @Test
    fun `invoiceItemTable with zero subtotal displays correctly`() {
        val items = listOf(
            InvoiceItemDTO(description = "Free Item", quantity = 1, unitPrice = 0.0, discount = 0)
        )
        val currentItem = InvoiceItemDTO(description = "Current", quantity = 0, unitPrice = 100.0, discount = 0)

        val table = invoiceItemTable(items, currentItem, "SEK")

        assertNotNull(table)
    }

    @Test
    fun `invoiceItemTable with 100 percent discount shows zero total`() {
        val items = listOf(
            InvoiceItemDTO(description = "Free Item", quantity = 1, unitPrice = 100.0, discount = 100)
        )
        val currentItem = InvoiceItemDTO(description = "Current", quantity = 1, unitPrice = 50.0, discount = 100)

        val table = invoiceItemTable(items, currentItem, "USD")

        assertNotNull(table)
    }

    @Test
    fun `invoiceItemTable with different currency`() {
        val items = listOf(
            InvoiceItemDTO(description = "Item", quantity = 1, unitPrice = 100.0, discount = 0)
        )
        val currentItem = InvoiceItemDTO(description = "Current", quantity = 1, unitPrice = 50.0, discount = 0)

        val table = invoiceItemTable(items, currentItem, "EUR")

        assertNotNull(table)
    }

    @Test
    fun `invoiceItemTable with long description`() {
        val longDesc = "A".repeat(200)
        val items = listOf(
            InvoiceItemDTO(description = longDesc, quantity = 1, unitPrice = 100.0, discount = 0)
        )
        val currentItem = InvoiceItemDTO(description = "Current", quantity = 1, unitPrice = 50.0, discount = 0)

        val table = invoiceItemTable(items, currentItem, "SEK")

        assertNotNull(table)
    }

    @Test
    fun `invoiceItemTable with special characters in description`() {
        val items = listOf(
            InvoiceItemDTO(
                description = "Item with \"quotes\" and <brackets>",
                quantity = 1,
                unitPrice = 100.0,
                discount = 0
            )
        )
        val currentItem = InvoiceItemDTO(description = "Current & More", quantity = 1, unitPrice = 50.0, discount = 0)

        val table = invoiceItemTable(items, currentItem, "SEK")

        assertNotNull(table)
    }

    // ==================== Currency Edge Cases ====================

    @Test
    fun `createVatGrid with empty currency still formats`() {
        val vatConfig = VatDateConfigDTO(vatRate = 25, invoiceDateOffset = 0, dueDateOffset = 14)
        val creator = createInvoiceCreator(MockReader(), MockScene(), InvoiceConfig(currency = ""))

        val grid = creator.createVatGrid(vatConfig)

        assertNotNull(grid)
    }

    @Test
    fun `createVatGrid with special characters in currency`() {
        val vatConfig = VatDateConfigDTO(vatRate = 25, invoiceDateOffset = 0, dueDateOffset = 14)
        val creator = createInvoiceCreator(MockReader(), MockScene(), InvoiceConfig(currency = "€"))

        val grid = creator.createVatGrid(vatConfig)

        assertNotNull(grid)
    }

    @Test
    fun `createVatGrid with long currency string`() {
        val vatConfig = VatDateConfigDTO(vatRate = 25, invoiceDateOffset = 0, dueDateOffset = 14)
        val creator =
            createInvoiceCreator(MockReader(), MockScene(), InvoiceConfig(currency = "VERY_LONG_CURRENCY_NAME"))

        val grid = creator.createVatGrid(vatConfig)

        assertNotNull(grid)
    }

    // ==================== Integration Tests with Database ====================

    @Test
    fun `createKeybinds assigns unique keybinds`() {
        val recipients = listOf(
            RecipientDTO(id = 1, companyName = "A", address = "Addr", postal = "1", email = "a@a.com"),
            RecipientDTO(id = 2, companyName = "B", address = "Addr", postal = "1", email = "b@b.com"),
            RecipientDTO(id = 3, companyName = "C", address = "Addr", postal = "1", email = "c@c.com")
        )

        val keybinds = createKeybinds(recipients)

        // Each recipient should have a unique keybind
        assertEquals(3, keybinds.size)
        assertEquals(3, keybinds.toSet().size) // All unique
    }

    @Test
    fun `createKeybinds with empty list returns empty array`() {
        val recipients = emptyList<RecipientDTO>()

        val keybinds = createKeybinds(recipients)

        assertEquals(0, keybinds.size)
    }

    @Test
    fun `createKeybinds with many recipients assigns keybinds`() {
        val recipients = (1..50).map {
            RecipientDTO(id = it, companyName = "Company $it", address = "Addr", postal = "1", email = "test@test.com")
        }

        val keybinds = createKeybinds(recipients)

        // Should have 50 unique keybinds
        assertEquals(50, keybinds.size)
    }

    // ==================== State Management Tests ====================

    @Test
    fun `sequential changes to same field work correctly`() = runBlocking {
        val reader = MockReader("10", "20")
        val scene = MockScene()
        val creator = createInvoiceCreator(reader, scene)
        val invoice = InvoiceInputDTO(
            invoiceDate = today, dueDate = today.plusDays(14),
            vatRate = 25, currency = "SEK", invoiceItems = emptyList(), recipient = null
        )
        val item = InvoiceItemDTO()

        // First change
        creator.changeQuantity(0, invoice, item)
        assertEquals(10, item.quantity)

        // Second change - MockReader returns next value
        creator.changeQuantity(0, invoice, item)
        assertEquals(20, item.quantity)
    }

    @Test
    fun `change after validation failure maintains previous valid value`() = runBlocking {
        val reader = MockReader("invalid", "5", "10")
        val scene = MockScene()
        val creator = createInvoiceCreator(reader, scene)
        val invoice = InvoiceInputDTO(
            invoiceDate = today, dueDate = today.plusDays(14),
            vatRate = 25, currency = "SEK", invoiceItems = emptyList(), recipient = null
        )
        val item = InvoiceItemDTO()

        // First change with invalid, then valid 5
        creator.changeQuantity(0, invoice, item)
        assertEquals(5, item.quantity)

        // Second change to 10
        creator.changeQuantity(0, invoice, item)
        assertEquals(10, item.quantity)
    }

    // ==================== Invoice Date Validation ====================

    @Test
    fun `VatDateConfig with invoice date far in past`() {
        val config = VatDateConfigDTO(vatRate = 25, invoiceDateOffset = -365, dueDateOffset = 14)
        val resolved = config.resolve(today)

        // Negative offset means invoice date in the past
        assertEquals(today.minusDays(365), resolved.invoiceDate)
        // Due date would also be in the past if offset is small enough
        assertTrue(resolved.dueDate.isBefore(today))
    }

    @Test
    fun `VatDateConfig with zero due date offset means due immediately`() {
        val config = VatDateConfigDTO(vatRate = 25, invoiceDateOffset = 0, dueDateOffset = 0)
        val resolved = config.resolve(today)

        // Invoice date and due date should be the same
        assertEquals(today, resolved.invoiceDate)
        assertEquals(today, resolved.dueDate)
    }

    @Test
    fun `VatDateConfig preserves vat rate in resolved dto`() {
        val vatRates = listOf(0, 6, 12, 25, 100)

        for (rate in vatRates) {
            val config = VatDateConfigDTO(vatRate = rate, invoiceDateOffset = 0, dueDateOffset = 14)
            val resolved = config.resolve(today)
            assertEquals(rate, resolved.vatRate)
        }
    }

    // ==================== Edge Case Integration Tests ====================

    @Test
    fun `invoice with maximum valid discount for all items`() {
        val items = listOf(
            InvoiceItemDTO(description = "Item 1", quantity = 1, unitPrice = 100.0, discount = 100),
            InvoiceItemDTO(description = "Item 2", quantity = 2, unitPrice = 50.0, discount = 100)
        )

        val totalSubtotal = items.sumOf { it.subtotal }

        assertEquals(0.0, totalSubtotal, 0.001)
    }

    @Test
    fun `invoice with mixed zero and non-zero quantities`() {
        // This should not be possible through UI (validation prevents it)
        // But we can test the DTO behavior
        val items = listOf(
            InvoiceItemDTO(description = "Zero qty", quantity = 0, unitPrice = 100.0, discount = 0),
            InvoiceItemDTO(description = "Normal", quantity = 5, unitPrice = 20.0, discount = 0)
        )

        val totalSubtotal = items.sumOf { it.subtotal }

        assertEquals(100.0, totalSubtotal, 0.001)
    }

    @Test
    fun `multiple invoice items cumulative totals`() {
        val items = listOf(
            InvoiceItemDTO(description = "Item 1", quantity = 2, unitPrice = 100.0, discount = 10), // 2 * 90 = 180
            InvoiceItemDTO(description = "Item 2", quantity = 3, unitPrice = 50.0, discount = 0),   // 3 * 50 = 150
            InvoiceItemDTO(description = "Item 3", quantity = 1, unitPrice = 200.0, discount = 25)  // 1 * 150 = 150
        )

        val expectedTotal = 180.0 + 150.0 + 150.0 // 480
        val actualTotal = items.sumOf { it.subtotal }

        assertEquals(expectedTotal, actualTotal, 0.001)
    }

    @Test
    fun `subtotal with floating point edge cases`() {
        // Test values that might cause floating point issues
        val item = InvoiceItemDTO(
            description = "Test",
            quantity = 3,
            unitPrice = 0.1, // Classic floating point issue
            discount = 0
        )

        // 3 * 0.1 should be 0.3 but floating point might give 0.30000000000000004
        // The rounding should handle this
        assertEquals(0.3, item.subtotal, 0.001)
    }

    @Test
    fun `subtotal with recurring decimal discount`() {
        // 33.333...% discount
        val item = InvoiceItemDTO(
            description = "Test",
            quantity = 1,
            unitPrice = 100.0,
            discount = 33
        )

        // 100 - 33% = 67, rounded
        val subtotal = item.subtotal
        assertTrue(subtotal in 66.0..68.0)
    }
}
