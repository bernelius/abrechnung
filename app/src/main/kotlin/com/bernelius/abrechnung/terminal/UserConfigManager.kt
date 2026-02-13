package com.bernelius.abrechnung.terminal

import com.bernelius.abrechnung.cache.UserConfigCache
import com.bernelius.abrechnung.models.UserConfigDTO
import com.bernelius.abrechnung.models.isNotBlank
import com.bernelius.abrechnung.repository.Repository
import com.bernelius.abrechnung.utils.Outcome
import com.bernelius.abrechnung.utils.renderLogo
import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.table.ColumnWidth
import com.github.ajalt.mordant.table.grid
import com.github.ajalt.mordant.widgets.Padding
import com.github.ajalt.mordant.widgets.Panel
import com.github.ajalt.mordant.widgets.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.bernelius.abrechnung.theme.Theme as th

class UserConfigManager(
    private val writer: Writer,
    private val reader: InputReader,
    private var userConfig: UserConfigDTO = UserConfigDTO(),
) {
    private val originalUserConfig = userConfig.copy()

    // bugfix: flag to avoid canceling edit when editing a newly created config (first run issue)
    private var noCancel: Boolean = false

    suspend fun start(init: Boolean = false): UserConfigDTO {
        if (init) {
            noCancel = true
            return firstTimeSetup()
        } else {
            return mainMenu()
        }
    }

    fun linearUserConfigGrid(
        user: UserConfigDTO,
        padding: Boolean = true,
        current: Char? = null,
    ): Widget =
        grid {
            column(0) {
                align = TextAlign.LEFT
                width = ColumnWidth(priority = 1, width = 20)
            }
            column(1) {
                align = TextAlign.LEFT
                width = ColumnWidth(priority = 2, width = 46)
            }
            if (padding) {
                row("", "")
            }
            val nameRow =
                if (current == 'n') {
                    th.primary(" • ")
                } else {
                    "   "
                } + "Name:"
            row(nameRow, user.name)
            val addressRow =
                if (current == 'a') {
                    th.primary(" • ")
                } else {
                    "   "
                } + "Address:"
            row(addressRow, user.address)
            val postalRow =
                if (current == 'd') {
                    th.primary(" • ")
                } else {
                    "   "
                } + "Zip/City:"
            row(postalRow, user.postal)
            val emailRow =
                if (current == 'e') {
                    th.primary(" • ")
                } else {
                    "   "
                } + "e-mail addr:"
            row(emailRow, user.email)
            val accountNumberRow =
                if (current == 'b') {
                    th.primary(" • ")
                } else {
                    "   "
                } + "Account #:"
            row(accountNumberRow, user.accountNumber)
            val orgNumberRow =
                if (current == 'o') {
                    th.primary(" • ")
                } else {
                    "   "
                } + "Org Num:"
            row(orgNumberRow, user.orgNumber ?: "")
            if (!user.smtpHost.isNullOrBlank()) {
                row()
            }
            val smtpHostRow =
                if (current == 's') {
                    th.primary(" • ")
                } else {
                    "   "
                } + "SMTP Host:"
            user.smtpHost.takeUnless { it.isNullOrBlank() }?.let { row(smtpHostRow, it) }
            val smtpPortRow =
                if (current == 'm') {
                    th.primary(" • ")
                } else {
                    "   "
                } + "SMTP Port:"
            user.smtpPort.takeUnless { it.isNullOrBlank() }?.let { row(smtpPortRow, it) }
            val smtpUserRow =
                if (current == 't') {
                    th.primary(" • ")
                } else {
                    "   "
                } + "SMTP User:"
            user.smtpUser.takeUnless { it.isNullOrBlank() }?.let { row(smtpUserRow, it) }
            val emailPasswordRow =
                if (current == 'p') {
                    th.primary(" • ")
                } else {
                    "   "
                } + "e-mail passwd:"
            if (user.emailPassword != null || current == 'p') {
                val passwordString = if (user.emailPassword != null) "Password registered" else ""
                row(emailPasswordRow, passwordString)
            }
            if (padding) {
                row()
            }
        }

    fun userConfigGrid(user: UserConfigDTO): Widget {
        val nameBind = th.primary("n) ")
        val addressBind = th.primary("a) ")
        val postalBind = th.primary("d) ")
        val emailBind = th.primary("e) ")
        val accountNumberBind = th.primary("b) ")
        val orgNumberBind = th.primary("o) ")
        val smtpHostBind = th.primary("s) ")
        val smtpPortBind = th.primary("m) ")
        val smtpUserBind = th.primary("t) ")
        val emailPasswordBind = th.primary("p) ")
        val theGrid =
            grid {
                column(0) {
                    TextAlign.LEFT
                    width = ColumnWidth(priority = 1, width = 20)
                }
                column(1) {
                    TextAlign.LEFT
                    width = ColumnWidth(priority = 2, width = 46)
                }
                row("${nameBind}Name:", user.name)
                row("${addressBind}Address:", user.address)
                row("${postalBind}Zip/City:", user.postal)
                row("${emailBind}e-mail addr:", user.email)
                row("${accountNumberBind}Account #:", user.accountNumber)
                row("${orgNumberBind}Org Num:", user.orgNumber ?: "")
                row()
                row("${smtpHostBind}SMTP Host:", user.smtpHost ?: "")
                row("${smtpPortBind}SMTP Port:", user.smtpPort ?: "")
                row("${smtpUserBind}SMTP User:", user.smtpUser ?: "")
                val passwordString = if (user.emailPassword != null) "Password registered" else ""
                row("${emailPasswordBind}e-mail passwd:", passwordString)
            }
        val title =
            th.success("w) ") +
                    "Write (save) changes" +
                    if (noCancel) {
                        ""
                    } else {
                        th.secondary(" / ") +
                                th.error("c) ") +
                                "Cancel"
                    }
        return Panel(
            theGrid,
            borderStyle = th.secondary,
            bottomTitle =
                Text(
                    title,
                ),
            bottomTitleAlign = TextAlign.RIGHT,
        )
    }

    fun doubleValidatePassword(
        scene: Scene,
        idx: Int,
        keybinds: Boolean = false,
    ) {
        val gridFunction: () -> Widget =
            if (keybinds) {
                { userConfigGrid(userConfig) }
            } else {
                { linearUserConfigGrid(userConfig) }
            }
        val message = "What is your email password"
        var varMsg = message
        while (true) {
            var emailPassword =
                validationLoop(
                    message = varMsg,
                    prefill = "",
                    reader = reader,
                    scene = scene,
                    onSuccess = {},
                    *UserConfigDTO.emailPasswordValidators,
                    isNotBlank,
                    maskInput = true,
                )

            var emailPassword2 =
                validationLoop(
                    message = "Confirm: $message",
                    prefill = "",
                    reader = reader,
                    scene = scene,
                    onSuccess = {},
                    *UserConfigDTO.emailPasswordValidators,
                    maskInput = true,
                )
            if (emailPassword == emailPassword2) {
                userConfig.emailPassword = emailPassword
                scene.replaceRow(idx, gridFunction())
                scene.display()
                return
            }
            varMsg = "Passwords do not match. Please try again."
        }
    }

    fun changeCompanyName(
        scene: Scene,
        idx: Int,
        keybinds: Boolean = false,
    ) {
        val gridFunction: () -> Widget =
            if (keybinds) {
                { userConfigGrid(userConfig) }
            } else {
                { linearUserConfigGrid(userConfig) }
            }
        validationLoop(
            message = "What is your (company) name? This name will be displayed in the sender field in your invoices.",
            prefill = userConfig.name,
            reader = reader,
            scene = scene,
            onSuccess = { newValue ->
                userConfig.name = newValue.trim()
                scene.replaceRow(idx, gridFunction())
            },
            *UserConfigDTO.nameValidators,
        )
    }

    fun changeAddress(
        scene: Scene,
        idx: Int,
        keybinds: Boolean = false,
    ) {
        val gridFunction: () -> Widget =
            if (keybinds) {
                { userConfigGrid(userConfig) }
            } else {
                { linearUserConfigGrid(userConfig) }
            }
        validationLoop(
            message = "What is your business address? Example: Main Street 60A",
            prefill = userConfig.address,
            reader = reader,
            scene = scene,
            onSuccess = { newValue ->
                userConfig.address = newValue.trim()
                scene.replaceRow(idx, gridFunction())
            },
            *UserConfigDTO.addressValidators,
        )
    }

    fun changePostal(
        scene: Scene,
        idx: Int,
        keybinds: Boolean = false,
    ) {
        val gridFunction: () -> Widget =
            if (keybinds) {
                { userConfigGrid(userConfig) }
            } else {
                { linearUserConfigGrid(userConfig) }
            }
        validationLoop(
            message = "What is the postal code and city where your business is registered? Example: 4644 Oslo",
            prefill = userConfig.postal,
            reader = reader,
            scene = scene,
            onSuccess = { newValue ->
                userConfig.postal = newValue.trim()
                scene.replaceRow(idx, gridFunction())
            },
            *UserConfigDTO.postalValidators,
        )
    }

    fun changeEmail(
        scene: Scene,
        idx: Int,
        keybinds: Boolean = false,
    ) {
        val gridFunction: () -> Widget =
            if (keybinds) {
                { userConfigGrid(userConfig) }
            } else {
                { linearUserConfigGrid(userConfig) }
            }
        validationLoop(
            message = "What is your e-mail address?",
            prefill = userConfig.email,
            reader = reader,
            scene = scene,
            onSuccess = { newValue ->
                userConfig.email = newValue.trim()
                scene.replaceRow(idx, gridFunction())
            },
            *UserConfigDTO.emailValidators,
        )
    }

    fun changeAccountNumber(
        scene: Scene,
        idx: Int,
        keybinds: Boolean = false,
    ) {
        val gridFunction: () -> Widget =
            if (keybinds) {
                { userConfigGrid(userConfig) }
            } else {
                { linearUserConfigGrid(userConfig) }
            }
        validationLoop(
            message = "What is the bank account number that your payments should be sent to? Example: 7922.76.31005",
            prefill = userConfig.accountNumber,
            reader = reader,
            scene = scene,
            onSuccess = { newValue ->
                userConfig.accountNumber = newValue.trim()
                scene.replaceRow(idx, gridFunction())
            },
            *UserConfigDTO.accountNumberValidators,
        )
    }

    fun changeOrgNumber(
        scene: Scene,
        idx: Int,
        keybinds: Boolean = false,
    ) {
        val gridFunction: () -> Widget =
            if (keybinds) {
                { userConfigGrid(userConfig) }
            } else {
                { linearUserConfigGrid(userConfig) }
            }
        validationLoop(
            message = "[Optional] What is your organization number?",
            prefill = userConfig.orgNumber ?: "",
            reader = reader,
            scene = scene,
            onSuccess = { newValue ->
                userConfig.orgNumber = newValue.trim()
                scene.replaceRow(idx, gridFunction())
            },
            *UserConfigDTO.orgNumberValidators,
        )
    }

    fun changeSmtpHost(
        scene: Scene,
        idx: Int,
        keybinds: Boolean = false,
    ): String {
        val gridFunction: () -> Widget =
            if (keybinds) {
                { userConfigGrid(userConfig) }
            } else {
                { linearUserConfigGrid(userConfig) }
            }
        return validationLoop(
            message = "[Optional] What is your SMTP host? Example (for gmail): smtp.gmail.com",
            prefill = userConfig.smtpHost ?: "",
            reader = reader,
            scene = scene,
            onSuccess = { newValue ->
                userConfig.smtpHost = newValue.trim()
                scene.replaceRow(idx, gridFunction())
            },
            *UserConfigDTO.smtpHostValidators,
        )
    }

    fun changeSmtpPort(
        scene: Scene,
        idx: Int,
        keybinds: Boolean = false,
    ) {
        val gridFunction: () -> Widget =
            if (keybinds) {
                { userConfigGrid(userConfig) }
            } else {
                { linearUserConfigGrid(userConfig) }
            }
        validationLoop(
            message = "What is your SMTP port? Example (for gmail): 587",
            prefill = userConfig.smtpPort ?: "",
            reader = reader,
            scene = scene,
            onSuccess = { newValue ->
                userConfig.smtpPort = newValue.trim()
                scene.replaceRow(idx, gridFunction())
            },
            *UserConfigDTO.smtpPortValidators,
        )
    }

    fun changeSmtpUser(
        scene: Scene,
        idx: Int,
        keybinds: Boolean = false,
    ) {
        val gridFunction: () -> Widget =
            if (keybinds) {
                { userConfigGrid(userConfig) }
            } else {
                { linearUserConfigGrid(userConfig) }
            }
        validationLoop(
            message =
                "What is your SMTP user? This is usually the same as your e-mail address,\n" +
                        "unless you want to send invoices from a different one than the one shown on your invoices.",
            prefill = userConfig.smtpUser ?: userConfig.email,
            reader = reader,
            scene = scene,
            onSuccess = { newValue ->
                userConfig.smtpUser = newValue.trim()
                scene.replaceRow(idx, gridFunction())
            },
            *UserConfigDTO.smtpUserValidators,
        )
    }

    private suspend fun mainMenu(): UserConfigDTO {
        val scene = MordantScene(writer)
        scene.addRow(renderLogo("Konfiguration"))
        var grid = userConfigGrid(userConfig)
        val idx = scene.addRow(grid)

        scene.display()
        navigationLoop {
            val actions: Map<Char, suspend () -> Unit> =
                mapOf(
                    'n' to { changeCompanyName(scene, idx, keybinds = true) },
                    'a' to { changeAddress(scene, idx, keybinds = true) },
                    'd' to { changePostal(scene, idx, keybinds = true) },
                    'e' to { changeEmail(scene, idx, keybinds = true) },
                    'b' to { changeAccountNumber(scene, idx, keybinds = true) },
                    'o' to { changeOrgNumber(scene, idx, keybinds = true) },
                    's' to { changeSmtpHost(scene, idx, keybinds = true) },
                    'm' to { changeSmtpPort(scene, idx, keybinds = true) },
                    't' to { changeSmtpUser(scene, idx, keybinds = true) },
                    'p' to { doubleValidatePassword(scene, idx, keybinds = true) },
                    'w' to {
                        val outcome = saveUserConfig()
                        if (outcome is Outcome.Error) {
                            val errScene = MordantScene(writer).apply {
                                addRow("Error: ${outcome.message}")
                                addRow("Press enter to continue...")
                                display()
                            }
                            reader.waitForEnter()
                        }
                        exit()
                    },
                    if (noCancel) {
                        'c' to { }
                    } else {
                        'c' to {
                            userConfig = originalUserConfig
                            exit()
                        }
                    },
                )
            var choice = reader.getRawCharIn(*actions.keys.toCharArray())
            if (choice != 'c' || !noCancel) {
                scene.replaceRow(idx, linearUserConfigGrid(userConfig, current = choice))
                scene.display()
            }
            actions[choice]?.invoke()
        }
        return userConfig
    }

    private suspend fun saveUserConfig(): Outcome {
        try {
            userConfig = writer.withLoading({
                Repository.setUserConfig(userConfig)
                UserConfigCache.invalidateAll()
                Repository.getUserConfig()
            }, message = "saving config")
            return Outcome.Success("Saved")
        } catch (e: Exception) {
            return Outcome.Error("Error: ${e.message}")
        }
    }

    private suspend fun firstTimeSetup(): UserConfigDTO {
        val scene = MordantScene(writer)
        scene.addRow(renderLogo("Wilkommen", style = th.info))
        var grid = linearUserConfigGrid(userConfig)

        val idx = scene.addRow(grid)
        scene.display()
        changeCompanyName(scene, idx)
        changeAddress(scene, idx)
        changePostal(scene, idx)
        changeEmail(scene, idx)
        changeAccountNumber(scene, idx)
        changeOrgNumber(scene, idx)

        scene.replaceRow(
            idx,
            Panel(
                """
                The next questions pertain to email delivery.
                You can skip them if you prefer to send invoices manually.
                If you leave the SMTP host field empty, the rest of the email questions will be skipped.

                The information you need can be found in your email provider's settings.
                """.trimIndent(),
                borderStyle = th.info,
                bottomTitle = "...Press enter to continue...",
                padding = Padding(1, 3, 1, 3),
            ),
        )
        scene.display()
        reader.waitForEnter()
        scene.replaceRow(idx, linearUserConfigGrid(userConfig))

        var smtpHost = changeSmtpHost(scene, idx)

        if (smtpHost.isNotBlank()) {
            changeSmtpPort(scene, idx)
            changeSmtpUser(scene, idx)

            doubleValidatePassword(scene = scene, idx = idx)
        }

        scene.replaceRow(
            idx,
            Panel(
                linearUserConfigGrid(userConfig, false),
                borderStyle = th.secondary,
                bottomTitle = isCorrectYesNo,
                bottomTitleAlign = TextAlign.RIGHT,
            ),
        )
        scene.display()

        var choice = reader.getRawCharIn('y', 'n')
        when (choice) {
            'y' -> {
                saveUserConfig()
                return userConfig
            }

            'n' -> {
                scene.replaceRow(idx, userConfigGrid(userConfig))
                return this.start()
            }

            else -> {
                throw IllegalStateException()
            }
        }
    }
}
