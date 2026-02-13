package com.bernelius.abrechnung.terminal

import com.bernelius.abrechnung.database.DatabaseFactory
import com.bernelius.abrechnung.models.RecipientDTO
import com.bernelius.abrechnung.repository.Repository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecipientManagerTest {
    private fun createManager(reader: MockReader): RecipientManager = RecipientManager(MockWriter(), reader)

    private fun Scene.hasError(message: String): Boolean = rows.any { (it.contents as String).contains(message) }

    @BeforeEach
    fun setupDatabase() {
        DatabaseFactory.initTestDatabase()
    }

    @AfterEach
    fun cleanupDatabase() {
        DatabaseFactory.cleanupTestDatabase()
    }

    @Test
    fun `changeCompanyName valid input updates dto`() = runBlocking {
        val scene = MockScene()
        val manager = createManager(MockReader("NewCompany"))

        manager.changeCompanyName(scene, 0, blockedNames = emptySet())

        assertEquals("NewCompany", manager.recipient.companyName)
    }

    @Test
    fun `changeCompanyName blank input shows error then accepts after retry`() = runBlocking {
        val scene = MockScene()
        val manager = createManager(MockReader("", "NewCompany"))

        manager.changeCompanyName(scene, 0, blockedNames = emptySet())

        assertTrue(scene.hasError("cannot be left blank"))
    }

    @Test
    fun `changeCompanyName blank then blank then valid shows two errors`() = runBlocking {
        val scene = MockScene()
        val manager = createManager(MockReader("", "", "NewCompany"))

        manager.changeCompanyName(scene, 0, blockedNames = emptySet())

        val errorCount = scene.rows.count { (it.contents as String).contains("cannot be left blank") }
        assertEquals(2, errorCount)
    }

    @Test
    fun `changeCompanyName duplicate name is blocked`() = runBlocking {
        Repository.addRecipient(
            RecipientDTO(
                companyName = "ExistingCorp",
                address = "123 Main St",
                postal = "1234 Oslo",
                email = "existing@example.com",
            ),
        )

        val scene = MockScene()
        val manager = createManager(MockReader("ExistingCorp", "NewCompany"))

        manager.changeCompanyName(scene, 0, blockedNames = setOf("existingcorp"))

        assertTrue(scene.hasError("Name already taken"))
    }

    @Test
    fun `changeAddress valid input updates dto`() = runBlocking {
        val scene = MockScene()
        val manager = createManager(MockReader("456 Side Ave"))

        manager.changeAddress(scene, 0)

        assertEquals("456 Side Ave", manager.recipient.address)
    }

    @Test
    fun `changeAddress blank input shows error`() = runBlocking {
        val scene = MockScene()
        val manager = createManager(MockReader("", "456 Side Ave"))

        manager.changeAddress(scene, 0)

        assertTrue(scene.hasError("cannot be left blank"))
    }

    @Test
    fun `changePostal valid input updates dto`() = runBlocking {
        val scene = MockScene()
        val manager = createManager(MockReader("5678 Bergen"))

        manager.changePostal(scene, 0)

        assertEquals("5678 Bergen", manager.recipient.postal)
    }

    @Test
    fun `changePostal blank input shows error`() = runBlocking {
        val scene = MockScene()
        val manager = createManager(MockReader("", "5678 Bergen"))

        manager.changePostal(scene, 0)

        assertTrue(scene.hasError("cannot be left blank"))
    }

    @Test
    fun `changeEmail valid input updates dto`() = runBlocking {
        val scene = MockScene()
        val manager = createManager(MockReader("recipient@example.com"))

        manager.changeEmail(scene, 0)

        assertEquals("recipient@example.com", manager.recipient.email)
    }

    @Test
    fun `changeEmail blank input shows error`() = runBlocking {
        val scene = MockScene()
        val manager = createManager(MockReader("", "recipient@example.com"))

        manager.changeEmail(scene, 0)

        assertTrue(scene.hasError("cannot be left blank"))
    }

    @Test
    fun `changeEmail invalid format shows error`() = runBlocking {
        val scene = MockScene()
        val manager = createManager(MockReader("notanemail", "recipient@example.com"))

        manager.changeEmail(scene, 0)

        assertTrue(scene.hasError("Must be a valid email"))
    }

    @Test
    fun `changeOrgNumber valid input updates dto`() = runBlocking {
        val scene = MockScene()
        val manager = createManager(MockReader("123456789"))

        manager.changeOrgNumber(scene, 0, blockedNumbers = emptySet())

        assertEquals("123456789", manager.recipient.orgNumber)
    }

    @Test
    fun `changeOrgNumber blank input updates dto with empty string`() = runBlocking {
        val scene = MockScene()
        val manager = createManager(MockReader(""))

        manager.changeOrgNumber(scene, 0, blockedNumbers = emptySet())

        assertEquals("", manager.recipient.orgNumber)
    }

    @Test
    fun `changeOrgNumber duplicate number is blocked`() = runBlocking {
        val scene = MockScene()
        val manager = createManager(MockReader("EXISTING123", "NEW456"))

        manager.changeOrgNumber(scene, 0, blockedNumbers = setOf("EXISTING123"))

        assertTrue(scene.hasError("Organization Number is already registered"))
    }
}
