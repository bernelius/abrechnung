package com.bernelius.abrechnung.terminal

import com.bernelius.abrechnung.models.UserConfigDTO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserConfigManagerTest {
    private fun createManager(
        reader: MockReader,
        userConfig: UserConfigDTO = UserConfigDTO(),
    ): UserConfigManager = UserConfigManager(MockWriter(), reader, userConfig)

    private fun Scene.hasError(message: String): Boolean = rows.any { (it.contents as String).contains(message) }

    @Test
    fun `changeCompanyName valid input updates dto`() {
        val scene = MockScene()
        val userConfig = UserConfigDTO()
        val manager = createManager(MockReader("ValidCorp"), userConfig)

        manager.changeCompanyName(scene, 0)

        assertEquals("ValidCorp", userConfig.name)
    }

    @Test
    fun `changeCompanyName blank input shows error then accepts after retry`() {
        val scene = MockScene()
        val manager = createManager(MockReader("", "ValidCorp"))

        manager.changeCompanyName(scene, 0)

        assertTrue(scene.hasError("cannot be left blank"))
    }

    @Test
    fun `changeCompanyName blank then valid shows two errors`() {
        val scene = MockScene()
        val manager = createManager(MockReader("", "", "ValidCorp"))

        manager.changeCompanyName(scene, 0)

        val errorCount = scene.rows.count { (it.contents as String).contains("cannot be left blank") }
        assertEquals(2, errorCount)
    }

    @Test
    fun `changeAddress valid input updates dto`() {
        val scene = MockScene()
        val userConfig = UserConfigDTO()
        val manager = createManager(MockReader("123 Main St"), userConfig)

        manager.changeAddress(scene, 0)

        assertEquals("123 Main St", userConfig.address)
    }

    @Test
    fun `changeAddress blank input shows error`() {
        val scene = MockScene()
        val manager = createManager(MockReader("", "123 Main St"))

        manager.changeAddress(scene, 0)

        assertTrue(scene.hasError("cannot be left blank"))
    }

    @Test
    fun `changePostal valid input updates dto`() {
        val scene = MockScene()
        val userConfig = UserConfigDTO()
        val manager = createManager(MockReader("1234 Oslo"), userConfig)

        manager.changePostal(scene, 0)

        assertEquals("1234 Oslo", userConfig.postal)
    }

    @Test
    fun `changePostal blank input shows error`() {
        val scene = MockScene()
        val manager = createManager(MockReader("", "1234 Oslo"))

        manager.changePostal(scene, 0)

        assertTrue(scene.hasError("cannot be left blank"))
    }

    @Test
    fun `changeEmail valid input updates dto`() {
        val scene = MockScene()
        val userConfig = UserConfigDTO()
        val manager = createManager(MockReader("test@example.com"), userConfig)

        manager.changeEmail(scene, 0)

        assertEquals("test@example.com", userConfig.email)
    }

    @Test
    fun `changeEmail invalid input shows error`() {
        val scene = MockScene()
        val manager = createManager(MockReader("notanemail", "test@example.com"))

        manager.changeEmail(scene, 0)

        assertTrue(scene.hasError("Must be a valid email"))
    }

    @Test
    fun `changeAccountNumber valid input updates dto`() {
        val scene = MockScene()
        val userConfig = UserConfigDTO()
        val manager = createManager(MockReader("7922.76.31005"), userConfig)

        manager.changeAccountNumber(scene, 0)

        assertEquals("7922.76.31005", userConfig.accountNumber)
    }

    @Test
    fun `changeAccountNumber blank input shows error`() {
        val scene = MockScene()
        val manager = createManager(MockReader("", "7922.76.31005"))

        manager.changeAccountNumber(scene, 0)

        assertTrue(scene.hasError("cannot be left blank"))
    }

    @Test
    fun `doubleValidatePassword matching passwords updates dto`() {
        val scene = MockScene()
        val userConfig = UserConfigDTO()
        val manager = createManager(MockReader("password", "password"), userConfig)

        manager.doubleValidatePassword(scene, 0)

        assertEquals("password", userConfig.emailPassword)
    }

    @Test
    fun `doubleValidatePassword mismatched passwords shows error and retries`() {
        val scene = MockScene()
        val manager = createManager(MockReader("password1", "password2", "password3", "password3"))

        manager.doubleValidatePassword(scene, 0)

        assertTrue(scene.hasError("Passwords do not match"))
    }

    @Test
    fun `doubleValidatePassword blank password shows error`() {
        val scene = MockScene()
        val manager = createManager(MockReader("", "password", "password"))

        manager.doubleValidatePassword(scene, 0)

        assertTrue(scene.hasError("cannot be left blank"))
    }
}
