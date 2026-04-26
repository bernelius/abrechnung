package com.bernelius.abrechnung.config

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.TomlOutputConfig
import com.akuleshov7.ktoml.annotations.TomlComments
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.serializer
import java.io.File

val toml =
    Toml(
        TomlInputConfig(
            ignoreUnknownNames = true,
        ),
        TomlOutputConfig(
            ignoreDefaultValues = false,
        ),
    )

fun initThemes(themeFolder: File): List<String> {
    if (!themeFolder.exists()) {
        themeFolder.mkdirs()
    }
    var themes: List<String> =
        themeFolder
            .listFiles()
            ?.filter { it.isFile }
            ?.filter { it.name.endsWith(".toml") }
            ?.map { it.name.removeSuffix(".toml") }
            ?: emptyList()
    var themesToExport: List<ConfigTheme> = emptyList()
    for (theme in builtinThemes) {
        if (!themes.contains(theme.name)) {
            saveTheme(theme, File(themeFolder, theme.name + ".toml"))
            themes = themes + theme.name
        }
    }
    return themes
}

fun initLanguages(languageFolder: File): List<String> {
    if (!languageFolder.exists()) {
        languageFolder.mkdirs()
    }
    var langs: List<String> =
        languageFolder
            .listFiles()
            ?.filter { it.isFile }
            ?.filter { it.name.endsWith(".toml") }
            ?.map { it.name.removeSuffix(".toml") }
            ?: emptyList()
    var langsToExport: List<ConfigTheme> = emptyList()
    for (lang in builtinLanguages) {
        if (!langs.contains(lang.name)) {
            saveLanguage(lang, File(languageFolder, lang.name + ".toml"))
            langs = langs + lang.name
        }
    }
    return langs
}

object ConfigManager {
    val xdgConfigHome =
        System.getenv("XDG_CONFIG_HOME")?.let { File(it) }
            ?: File(System.getProperty("user.home"), ".config")
    val configFile = File(xdgConfigHome, "abrechnung/config.toml")
    internal val themeFolder = File(xdgConfigHome, "abrechnung/themes/")
    val themes = initThemes(themeFolder)
    internal val languageFolder = File(xdgConfigHome, "abrechnung/languages/")
    val languages = initLanguages(languageFolder)

    fun loadConfig(): UserConfig {
        if (!configFile.exists()) {
            configFile.parentFile?.mkdirs()
            val defaultAppConfig = UserConfig()
            saveConfig(defaultAppConfig)
            return defaultAppConfig
        }

        val configContents = configFile.readText()
        return toml.decodeFromString<UserConfig>(serializer(), configContents)
    }

    fun saveConfig(config: UserConfig) {
        val str = toml.encodeToString<UserConfig>(serializer(), config)
        configFile.writeText(str)
    }
}

@Serializable
data class UserConfig(
    val terminalConfig: TerminalConfig = TerminalConfig(builtinThemes.first().name),
    val invoiceConfig: InvoiceConfig = InvoiceConfig(),
)

@Serializable
data class TerminalConfig(
    var theme: String = builtinThemes.first().name,
    @TomlComments(
        "outerHotkeys:",
        "places the visual indicators for the hotkeys outside the text",
        "purely cosmetic. Default: true"
    )
    var outerHotkeys: Boolean = true,
    @TomlComments(
        "enableExternalEditor:",
        "uses the EDITOR environment variable to open the current buffer in an external",
        "editor, useful for multiline fields like email body",
        "available by pressing Ctrl+E during most text editing tasks.",
        "default: false"
    )
    var enableExternalEditor: Boolean = false,
)

@Serializable
data class InvoiceConfig(
    @TomlComments(
        "dueDateOffset: default due date for new invoices, measured in days from invoice date",
        "the default of 14 days means that the due date will be two weeks from whatever",
        "date is set as the invoice date (usually the current date)",
    )
    val dueDateOffset: Int = 14,
    @TomlComments(
        "vatRate: default vat rate on new invoices, in percent",
    )
    val vatRate: Int = 0,
    @TomlComments(
        "currency: currency to use for invoices",
    )
    val currency: String = "NOK",
    @TomlComments(
        "language: the language to display on the invoice",
        "You can define your own language by copying the en.toml file",
        "contained in the languages folder and modifying the words.",
    )
    val language: String = builtinLanguages.first().name,
)
