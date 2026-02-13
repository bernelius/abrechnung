package com.bernelius.abrechnung.theme

import com.bernelius.abrechnung.config.ConfigManager
import com.bernelius.abrechnung.config.loadTheme
import com.bernelius.abrechnung.terminalConfig
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.TextStyles

var configTheme = ConfigManager.loadTheme(terminalConfig.theme)

enum class TitleFont {
    PRIMARY,
    SECONDARY,
    TERTIARY,
}

enum class Style {
    PRIMARY,
    SECONDARY,
    TERTIARY,
    SUCCESS,
    ERROR,
    WARNING,
    INFO,
}

private fun mapStyle(style: Style): TextStyle =
    when (style) {
        Style.PRIMARY -> TextColors.rgb(configTheme.primary)
        Style.SECONDARY -> TextColors.rgb(configTheme.secondary)
        Style.TERTIARY -> TextColors.rgb(configTheme.tertiary)
        Style.SUCCESS -> TextColors.rgb(configTheme.success) + TextStyles.bold
        Style.ERROR -> TextColors.rgb(configTheme.error) + TextStyles.bold
        Style.WARNING -> TextColors.rgb(configTheme.warning)
        Style.INFO -> TextColors.rgb(configTheme.info)
    }

private fun mapTitleFont(font: TitleFont = TitleFont.PRIMARY): String =
    when (font) {
        TitleFont.PRIMARY -> "Big Money-ne"

        // terminalConfig.primaryFigFont
        TitleFont.SECONDARY -> "RubiFont"

        // terminalConfig.secondaryFigFont
        TitleFont.TERTIARY -> "ANSI Compact"
    }

object Theme {
    val norm = TextStyle()
    val dim = TextStyle(dim = true)
    val strikethrough = TextStyle(strikethrough = true)
    val underline = TextStyle(underline = true)
    val bold = TextStyle(bold = true)
    var primary = mapStyle(Style.PRIMARY)
    var secondary = mapStyle(Style.SECONDARY)
    var tertiary = mapStyle(Style.TERTIARY)
    var success = mapStyle(Style.SUCCESS)
    var error = mapStyle(Style.ERROR)
    var warning = mapStyle(Style.WARNING)
    var info = mapStyle(Style.INFO)
    val primaryFont = mapTitleFont(TitleFont.PRIMARY)
    val secondaryFont = mapTitleFont(TitleFont.SECONDARY)
    val tertiaryFont = mapTitleFont(TitleFont.TERTIARY)
}

fun reloadTheme() {
    terminalConfig = ConfigManager.loadConfig().terminalConfig
    configTheme = ConfigManager.loadTheme(terminalConfig.theme)
    ConfigManager.loadTheme(terminalConfig.theme)

    Theme.primary = mapStyle(Style.PRIMARY)
    Theme.secondary = mapStyle(Style.SECONDARY)
    Theme.tertiary = mapStyle(Style.TERTIARY)
    Theme.success = mapStyle(Style.SUCCESS)
    Theme.error = mapStyle(Style.ERROR)
    Theme.warning = mapStyle(Style.WARNING)
    Theme.info = mapStyle(Style.INFO)
}
