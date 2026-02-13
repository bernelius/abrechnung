package com.bernelius.abrechnung.config

import com.bernelius.abrechnung.models.DatePattern
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.serializer
import java.io.File

fun ConfigManager.loadLanguage(languageName: String): Language {
    val languageFile = File(configFile.parentFile, "languages/$languageName.toml")
    if (!languageFile.exists()) {
        throw IllegalArgumentException("Language $languageName not found.")
    }
    val languageContents = languageFile.readText()
    return toml.decodeFromString<Language>(serializer(), languageContents)
}

internal fun saveLanguage(
    language: Language,
    languageFile: File,
) {
    val str = toml.encodeToString<Language>(serializer(), language)
    languageFile.writeText(str)
}

@Serializable
data class Language(
    val name: String,
    val headline: String,
    val orgNumber: String,
    val billTo: String,
    val invoiceNumber: String,
    val invoiceDate: String,
    // datePattern: the date pattern to use for the invoice date.
    // The default is dd.MM.yyyy, which means day, month, year, in that order.
    // If you use an invalid date pattern, the app will warn you on startup.
    // see https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html
    val datePattern: DatePattern,
    val dueDate: String,
    val description: String,
    val quantity: String,
    val discount: String,
    val price: String,
    val subtotal: String,
    val total: String,
    val amount: String,
    val toBankAccount: String,
    val vat: String,
    val paymentInformation: String,
    val paymentNote: String,
)
