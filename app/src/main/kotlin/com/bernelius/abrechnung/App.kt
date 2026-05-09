package com.bernelius.abrechnung

import com.bernelius.abrechnung.audioplayer.CrossfadingAudioPlayer
import com.bernelius.abrechnung.config.ConfigManager
import com.bernelius.abrechnung.database.DatabaseFactory
import com.bernelius.abrechnung.dateprovider.SystemDateProvider
import com.bernelius.abrechnung.logging.configureLogging
import com.bernelius.abrechnung.models.UserConfigDTO
import com.bernelius.abrechnung.repository.Repository
import com.bernelius.abrechnung.terminal.HelpScene
import com.bernelius.abrechnung.terminal.InvoiceCreator
import com.bernelius.abrechnung.terminal.InvoiceManager
import com.bernelius.abrechnung.terminal.MordantScene
import com.bernelius.abrechnung.terminal.MordantUI
import com.bernelius.abrechnung.terminal.RecipientManager
import com.bernelius.abrechnung.terminal.ReportManager
import com.bernelius.abrechnung.terminal.SettingsManager
import com.bernelius.abrechnung.terminal.UserConfigManager
import com.bernelius.abrechnung.terminal.navigationLoop
import com.bernelius.abrechnung.terminal.stdMenuRow
import com.bernelius.abrechnung.utils.exitProgram
import com.bernelius.abrechnung.utils.getLogDir
import com.bernelius.abrechnung.utils.renderLogo
import com.github.ajalt.mordant.table.grid
import com.github.ajalt.mordant.widgets.Padding
import com.github.ajalt.mordant.widgets.Panel
import com.github.ajalt.mordant.widgets.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.postgresql.util.PSQLException
import java.io.FileOutputStream
import java.io.PrintStream
import kotlin.system.exitProcess
import com.bernelius.abrechnung.theme.Theme as th

data class StartupData(
    val overdueState: Boolean,
    val credentialsConfig: UserConfigDTO,
)

suspend fun loadStartupData() =
    coroutineScope {
        val configDeferred = async { Repository.getUserConfig() }
        launch { Repository.findAllRecipientsSortFrequency() }
        val invoicesDeferred = async { Repository.findAllInvoices(filter = "pending") }

        StartupData(
            overdueState =
                invoicesDeferred.await().firstOrNull().let {
                    if (it == null) {
                        false
                    } else {
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

val dateProvider = SystemDateProvider()
var terminalConfig = ConfigManager.loadConfig().terminalConfig

suspend fun main() {
    try {
        MordantUI().use { ui ->
            /*
             * a full day of trying to get logback to cooperate in graalVM on startup left me with no option but to brute force this stream redirection.
             * not pretty, but it's better than getting spammed with log messages from hikari
             * (in stdout! what the crap... never found the root cause of this) when the app starts up.
             * the shadowJar worked fine with the ui.withLoading() version, so it's still an option for non-graal builds.
             * used to be ui.withLoading({ ...get all the lateinit vars... }) but now we do this instead.
             *
             * o beautiful spinner, thou shalt be missed.
             *
             * TODO: hack together a withloading version that also controls the output stream
             */

            val scene =
                MordantScene(ui).apply {
                    addRow(
                        Text("initializing..."),
                    )
                    display()
                }
            val originalOut = System.out
            val originalErr = System.err
            val logDir = getLogDir()
            val logFile = FileOutputStream("$logDir/abrechnung-stdout.log", false)
            System.setOut(PrintStream(logFile, true, "UTF-8"))
            configureLogging()

            val audioPlayer = CrossfadingAudioPlayer()

            try {
                DatabaseFactory.init()
            } catch (e: PSQLException) {
                System.setErr(originalErr)
                System.err.println(
                    "Could not connect to database. Please check your configuration. Full error: ${e.message}"
                )
                exitProcess(1)
            }
            audioPlayer.start()
            val startupData = loadStartupData()
            val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

            var mainSong = if (startupData.overdueState) Songs.FINSTERNIS else Songs.ABRECHNUNG
            var credentialsConfig = startupData.credentialsConfig

            val actions: Map<Char, suspend () -> Unit> =
                mapOf(
                    'g' to {
                        InvoiceCreator(
                            writer = ui,
                            reader = ui,
                            dateProvider = dateProvider,
                            appScope = appScope,
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
                    'r' to { ReportManager(writer = ui, reader = ui).mainMenu() },
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

            System.setOut(originalOut)

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

                while (!credentialsConfig.isValid()) {
                    try {
                        HelpScene(writer = ui, reader = ui).helpDisplay(intro = true)
                    } catch (_: ExitSignal) {
                        exitProgram()
                    }
                    try {
                        credentialsConfig =
                            UserConfigManager(
                                writer = ui,
                                reader = ui,
                            ).start(init = true)
                    } catch (_: ExitSignal) {
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
                            stdMenuRow('r', "reporting")
                            stdMenuRow('c', "configuration of self")
                            stdMenuRow('s', "app settings")
                            stdMenuRow('h', "help")
                            stdMenuRow('q', "quit", th.error)
                        },
                        title = Text(th.secondary("What do you want to do?")),
                        padding = Padding(1, 3, 1, 3),
                    )

                val scene = MordantScene(ui)
                scene.addRow(renderLogo("Abrechnung?", th.primary, th.primaryFont))
                scene.addRow(menu)
                scene.display()

                try {
                    val key: Char = ui.getRawCharIn(actions.keys)
                    try {
                        coroutineScope { actions[key]!!.invoke() }
                    } catch (_: ExitSignal) {
                    // without this, the app will exit out completely when ExitSignal is thrown
                    }
                } catch (_: ExitSignal) {
                    exit()
                }
            }
        }
    } catch (_: ProgramExitSignal) {
        exitProcess(0)
    }
}
