package com.bernelius.abrechnung.terminal

import com.bernelius.abrechnung.config.ConfigManager
import com.bernelius.abrechnung.theme.reloadTheme
import com.bernelius.abrechnung.utils.renderLogo
import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.table.grid
import com.github.ajalt.mordant.widgets.Panel
import com.github.ajalt.mordant.widgets.Text
import com.bernelius.abrechnung.theme.Theme as th

class SettingsManager(
    private val writer: Writer,
    private val reader: InputReader,
    private var configManager: ConfigManager,
) {
    fun themePanel(
        boundThemes: Map<Char, String>,
        currentTheme: String,
    ): Panel =
        Panel(
            grid {
                for (theme in boundThemes) {
                    var keyTheme = th.primary
                    var themeNameTheme = th.norm
                    var key = " ${theme.key} "
                    if (theme.value == currentTheme) {
                        keyTheme = th.success
                        themeNameTheme = th.success
                        key = " • "
                    }
                    row(keyTheme(key), themeNameTheme(theme.value))
                }
            },
            title = Text(th.secondary("Themes")),
            bottomTitle = Text("${th.error("q)")} Quit"),
            bottomTitleAlign = TextAlign.RIGHT,
        )

    fun getThemeMap(): Map<Char, String> =
        ConfigManager.themes
            .mapIndexed { index, theme ->
                mapIntToHotkey(index) to theme
            }.toMap()

    private fun setTheme(theme: String) {
        val config = configManager.loadConfig()
        config.terminalConfig.theme = theme
        configManager.saveConfig(config)
        reloadTheme()
    }

    fun mainMenu() {
        val scene = MordantScene(writer)
        val logoIdx = scene.addRow(renderLogo("Systemzwang"))
        var currentTheme = ConfigManager.loadConfig().terminalConfig.theme
        var themesWithBinds = getThemeMap()
        var themePanel = themePanel(themesWithBinds, currentTheme)
        val panelIdx = scene.addRow()

        val themeOptions = themesWithBinds.keys.toCharArray()
        navigationLoop {
            scene.replaceRow(logoIdx, renderLogo("Systemzwang"))
            scene.replaceRow(panelIdx, themePanel(themesWithBinds, currentTheme))
            scene.display()
            when (val choice = reader.getRawCharIn(*themeOptions, 'q')) {
                'q' -> {
                    exit()
                }

                else -> {
                    val newTheme = themesWithBinds[choice]!!
                    try {
                        setTheme(newTheme)
                        currentTheme = newTheme
                    } catch (e: Exception) {
                        // log.logError(e)
                    }
                }
            }
        }
    }
}
