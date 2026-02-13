package com.bernelius.abrechnung

import com.bernelius.abrechnung.audioplayer.CrossfadingAudioPlayer
import com.bernelius.abrechnung.config.ConfigManager
import com.bernelius.abrechnung.config.TerminalConfig
import com.bernelius.abrechnung.database.DatabaseFactory
import com.bernelius.abrechnung.dateprovider.SystemDateProvider
import com.bernelius.abrechnung.repository.Repository
import com.bernelius.abrechnung.models.InvoiceDTO
import com.bernelius.abrechnung.models.RecipientDTO
import com.bernelius.abrechnung.models.UserConfigDTO
import com.bernelius.abrechnung.terminal.HelpScene
import com.bernelius.abrechnung.terminal.InvoiceManager
import com.bernelius.abrechnung.terminal.MordantScene
import com.bernelius.abrechnung.terminal.MordantUI
import com.bernelius.abrechnung.terminal.RecipientManager
import com.bernelius.abrechnung.terminal.SettingsManager
import com.bernelius.abrechnung.terminal.UserConfigManager
import com.bernelius.abrechnung.terminal.InvoiceCreator
import com.bernelius.abrechnung.terminal.navigationLoop
import com.bernelius.abrechnung.terminal.stdMenuRow
import com.bernelius.abrechnung.utils.exitProgram
import com.bernelius.abrechnung.utils.renderLogo
import com.bernelius.abrechnung.theme.Theme as th
import com.bernelius.abrechnung.logging.configureLogging
import com.github.ajalt.mordant.table.grid
import com.github.ajalt.mordant.widgets.Padding
import com.github.ajalt.mordant.widgets.Panel
import com.github.ajalt.mordant.widgets.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.postgresql.util.PSQLException
import org.sqlite.date.ExceptionUtils

data class StartupData(
    val overdueState: Boolean,
    val credentialsConfig: UserConfigDTO,
)

suspend fun loadStartupData() = coroutineScope {
    val configDeferred = async { Repository.getUserConfig() }
    val recipientsDeferred = async { Repository.findAllRecipientsSortFrequency() }
    val invoicesDeferred = async { Repository.findAllInvoices(filter = "pending") }

    StartupData(
        overdueState = invoicesDeferred.await().firstOrNull().let {
            if (it == null) false else {
                it.dueDate < dateProvider.today()
            }
        },
        credentialsConfig = configDeferred.await(),
    )
}

object Songs {
    const val ABRECHNUNG = "music/ABRECHNUNG.ogg"
    const val EINSTELLUNG = "music/EINSTELLUNG.ogg"
    const val HILFE = "music/HILFE.ogg"
    const val FINSTERNIS = "music/FINSTERNIS.ogg"
}

val audioPlayer = CrossfadingAudioPlayer()
val dateProvider = SystemDateProvider()
var terminalConfig = ConfigManager.loadConfig().terminalConfig


suspend fun main() {
    configureLogging()
    val ui = MordantUI()
    var scene = MordantScene(ui).apply { display() }
    lateinit var startupData: StartupData
    lateinit var appScope: CoroutineScope
    ui.withLoading(block = {
        DatabaseFactory.init()
        audioPlayer.start()
        startupData = loadStartupData()
        appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }, message = "initializing")


    var mainSong = if (startupData.overdueState) Songs.FINSTERNIS else Songs.ABRECHNUNG
    var credentialsConfig = startupData.credentialsConfig

    val actions: Map<Char, suspend () -> Unit> =
        mapOf(
            'g' to {
                InvoiceCreator(
                    writer = ui,
                    reader = ui,
                    dateProvider = dateProvider,
                    appScope = appScope
                ).generateInvoice()
            },
            'm' to {
                InvoiceManager(
                    writer = ui,
                    reader = ui,
                    dateProvider = dateProvider,
                    appScope = appScope,
                ).listUnpaidInvoices()
            },
            'r' to { RecipientManager(writer = ui, reader = ui).registrationMenu() },
            'u' to { RecipientManager(writer = ui, reader = ui).updateRecipientMenu() },
            's' to {
                audioPlayer.play(Songs.EINSTELLUNG)
                SettingsManager(writer = ui, reader = ui, configManager = ConfigManager).mainMenu()
            },
            'c' to {
                audioPlayer.play(Songs.EINSTELLUNG)
                UserConfigManager(
                    writer = ui,
                    reader = ui,
                    userConfig = credentialsConfig,
                ).start()
            },
            'h' to {
                audioPlayer.play(Songs.HILFE)
                HelpScene(writer = ui, reader = ui).helpDisplay()
            },
            'q' to { exitProgram() },
        )

    val logo = renderLogo("Abrechnung?", fontName = th.primaryFont)
    val logoWidth = logo.split("\n")[0].length
    if (ui.size.width < logoWidth) {
        scene.addRow("This terminal window is too small. There will be be problems with the output.")
        scene.addRow(th.success("o) ") + "Okay. How bad could it be?")
        scene.addRow(th.error("q) " + "Quit."))
        scene.display()
        val char = ui.getRawCharIn('o', 'q')
        when (char) {
            'o' -> scene.clear()
            'q' -> exitProgram()
        }
    }

    navigationLoop {
        var scene = MordantScene(ui)

        while (!credentialsConfig.isValid()) {
            try {
                HelpScene(writer = ui, reader = ui).helpDisplay(intro = true)
            } catch (e: MordantUI.NavigationException) {
                exitProgram()
            }
            try {
                credentialsConfig = UserConfigManager(
                    writer = ui,
                    reader = ui,
                ).start(init = true)
            } catch (e: MordantUI.NavigationException) {
                exit()
            }
        }

        audioPlayer.play(mainSong)
        val menu =
            Panel(
                grid {
                    stdMenuRow('g', "generate invoice")
                    stdMenuRow('m', "manage unpaid invoices")
                    stdMenuRow('r', "register new recipient")
                    stdMenuRow('u', "update recipient information")
                    stdMenuRow('c', "configuration of self")
                    stdMenuRow('s', "app settings")
                    stdMenuRow('h', "help")
                    stdMenuRow('q', "quit", th.error)
                },
                title = Text(th.secondary("What do you want to do?")),
                padding = Padding(1, 3, 1, 3),
            )

        val logoRow = scene.addRow(renderLogo("Abrechnung?", th.primary, th.primaryFont))
        val menuRow = scene.addRow(menu)
        scene.display()

        try {
            val key: Char = ui.getRawCharIn(actions.keys)
            try {
                coroutineScope { actions[key]!!.invoke() }
            } catch (e: MordantUI.NavigationException) {
            }
        } catch (e: MordantUI.NavigationException) {
            exit()
        }
    }
}
