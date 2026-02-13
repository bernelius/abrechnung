package com.bernelius.abrechnung.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import java.io.File

fun ConfigManager.loadTheme(themeName: String): ConfigTheme {
    val themeFile = File(themeFolder, "$themeName.toml")
    if (!themeFile.exists()) {
        throw IllegalArgumentException("Theme $themeName not found.")
    }
    val themeContents = themeFile.readText()
    return toml.decodeFromString<ConfigTheme>(serializer(), themeContents)
}

private fun ConfigManager.saveTheme(
    theme: ConfigTheme,
    themeName: String,
) {
    val themeFile = File(themeFolder, "$themeName.toml")
    saveTheme(theme, themeFile)
}

internal fun saveTheme(
    theme: ConfigTheme,
    themeFile: File,
) {
    val str = toml.encodeToString<ConfigTheme>(serializer(), theme)
    themeFile.writeText(str)
}

@Serializable
data class ConfigTheme(
    val name: String,
    val primary: String,
    val secondary: String,
    val tertiary: String,
    val success: String,
    val error: String,
    val warning: String,
    val info: String,
)
