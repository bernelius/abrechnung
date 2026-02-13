package com.bernelius.abrechnung.database

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SecureVaultTest {
    @Test
    fun `encrypt produces non-null non-empty output`() {
        val result = SecureVault.encrypt("test password")
        assertTrue(result.isNotBlank())
    }

    @Test
    fun `encrypt produces different output each time (random IV)`() {
        val plaintext = "same content"
        val encrypted1 = SecureVault.encrypt(plaintext)
        val encrypted2 = SecureVault.encrypt(plaintext)
        assertNotEquals(encrypted1, encrypted2)
    }

    @Test
    fun `decrypt recovers original plaintext`() {
        val original = "my secret password 123!@#"
        val encrypted = SecureVault.encrypt(original)
        val decrypted = SecureVault.decrypt(encrypted)
        assertEquals(original, decrypted)
    }

    @Test
    fun `decrypt returns null for null input`() {
        assertNull(SecureVault.decrypt(null))
    }

    @Test
    fun `decrypt returns null for blank input`() {
        assertNull(SecureVault.decrypt(""))
        assertNull(SecureVault.decrypt("   "))
    }

    @Test
    fun `decrypt returns null for too-short input`() {
        assertNull(SecureVault.decrypt("abc"))
    }

    @Test
    fun `decrypt returns null for invalid base64`() {
        assertNull(SecureVault.decrypt("not-valid-base64!!!"))
    }

    @Test
    fun `roundtrip with special characters`() {
        val specialChars = "P@ssw0rd!#\$%^&*()_+-=[]{}|;':\",./<>?"
        val encrypted = SecureVault.encrypt(specialChars)
        val decrypted = SecureVault.decrypt(encrypted)
        assertEquals(specialChars, decrypted)
    }

    @Test
    fun `roundtrip with unicode characters`() {
        val unicode = "Hëllö Wörld 你好 🔐"
        val encrypted = SecureVault.encrypt(unicode)
        val decrypted = SecureVault.decrypt(encrypted)
        assertEquals(unicode, decrypted)
    }

    @Test
    fun `roundtrip with empty string`() {
        val encrypted = SecureVault.encrypt("")
        val decrypted = SecureVault.decrypt(encrypted)
        assertEquals("", decrypted)
    }

    @Test
    fun `roundtrip with very long string`() {
        val longString = "a".repeat(10000)
        val encrypted = SecureVault.encrypt(longString)
        val decrypted = SecureVault.decrypt(encrypted)
        assertEquals(longString, decrypted)
    }

    @Test
    fun `encrypted output is valid base64`() {
        val encrypted = SecureVault.encrypt("test")
        val decoded =
            java.util.Base64
                .getDecoder()
                .decode(encrypted)
        assertNotNull(decoded)
        assertTrue(decoded.size > 12)
    }
}
