package com.bernelius.abrechnung.database

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.reflect.KProperty

class RequiredEncryptedDelegate(
    private val column: Column<String>,
) {
    operator fun getValue(
        thisRef: Any?,
        property: KProperty<*>,
    ): String {
        val row = thisRef as? ResultRow ?: return ""
        val encryptedValue = row[column]

        return SecureVault.decrypt(encryptedValue)
            ?: throw IllegalStateException("Decryption failed! Is your ABRECHNUNG_KEY correct?")
    }

    operator fun setValue(
        thisRef: Any?,
        property: KProperty<*>,
        value: String,
    ) {
        val updateBuilder = thisRef as? UpdateBuilder<*> ?: return
        updateBuilder[column] = SecureVault.encrypt(value)
    }
}

class OptionalEncryptedDelegate(
    private val column: Column<String?>,
) {
    operator fun getValue(
        thisRef: Any?,
        property: KProperty<*>,
    ): String? {
        val row = thisRef as? ResultRow ?: return null
        val encryptedValue = row[column] ?: return null
        return SecureVault.decrypt(encryptedValue)
    }

    operator fun setValue(
        thisRef: Any?,
        property: KProperty<*>,
        value: String?,
    ) {
        val updateBuilder = thisRef as? UpdateBuilder<*> ?: return
        updateBuilder[column] = value?.let { SecureVault.encrypt(it) }
    }
}

object SecureVault {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private val UTF8 = Charsets.UTF_8

    private val encryptionKey: SecretKeySpec by lazy {
        val password =
            System.getenv("ABRECHNUNG_KEY")?.toCharArray()
                ?: "NotSecretKey0000".toCharArray()

        val salt =
            System.getenv("ABRECHNUNG_SALT")?.toByteArray(UTF8)
                ?: "DefaultOpenSourceSalt".toByteArray(UTF8)

        try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec = PBEKeySpec(password, salt, 600000, 256)
            val tmp = factory.generateSecret(spec)

            SecretKeySpec(tmp.encoded, "AES")
        } finally {
            password.fill('0')
        }
    }

    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)

        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, spec)

        val encrypted = cipher.doFinal(plainText.toByteArray(UTF8))

        return Base64.getEncoder().encodeToString(iv + encrypted)
    }

    fun decrypt(encryptedText: String?): String? {
        if (encryptedText.isNullOrBlank()) return null

        return try {
            val decoded = Base64.getDecoder().decode(encryptedText)
            if (decoded.size < 28) return null // 12 (IV) + 16 (GCM Tag minimum)

            val iv = decoded.copyOfRange(0, 12)
            val ciphertext = decoded.copyOfRange(12, decoded.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, spec)

            String(cipher.doFinal(ciphertext), UTF8)
        } catch (e: Exception) {
            // A failure here often means someone tampered with the data.
            System.err.println("Decryption failed: ${e.message}")
            null
        }
    }
}
