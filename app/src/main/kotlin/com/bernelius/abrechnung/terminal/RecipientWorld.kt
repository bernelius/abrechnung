package com.bernelius.abrechnung.terminal

import com.bernelius.abrechnung.cache.RecipientCache
import com.bernelius.abrechnung.models.RecipientDTO
import com.bernelius.abrechnung.models.Validator
import com.bernelius.abrechnung.repository.Repository
import com.bernelius.abrechnung.utils.renderLogo
import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.widgets.Panel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.bernelius.abrechnung.theme.Theme as th

suspend fun addRecipientWithCacheUpdate(recipient: RecipientDTO) {
    Repository.addRecipient(recipient)
    RecipientCache.invalidate("all")
    Repository.findAllRecipientsSortFrequency()
}

suspend fun updateRecipientWithCacheUpdate(recipient: RecipientDTO) {
    Repository.updateRecipient(recipient)
    RecipientCache.invalidate("all")
    Repository.findAllRecipientsSortFrequency()
}

fun singleRecipientGrid(
    recipient: RecipientDTO,
    padding: Boolean = true,
): Widget {
    val rows =
        listOfNotNull(
            if (padding) listOf("", "") else null,
            listOf("Name:", recipient.companyName),
            listOf("Addr:", recipient.address),
            listOf("Zip:", recipient.postal),
            listOf("@:", recipient.email),
            listOf("Org #:", recipient.orgNumber ?: ""),
            if (padding) listOf("", "") else null,
        )

    return createGrid(
        *rows.toTypedArray(),
        alignments = listOf(TextAlign.RIGHT, TextAlign.LEFT),
        sizes = listOf(8, 46),
    )
}

val recipientLogos = listOf("Das Subjekt", "Empfaengerwelt", "Kundenreich")


class RecipientManager(
    private val writer: Writer,
    private val reader: InputReader,
) {
    lateinit var allRecipients: List<RecipientDTO>

    var recipient = RecipientDTO()

    suspend fun getAllRecipients(): List<RecipientDTO> {
        return writer.withLoading({ Repository.findAllRecipientsSortFrequency() }, message = "fetching recipients")
    }

    fun getAllRecipientNames(): Set<String> {
        return allRecipients.map { it.companyName.lowercase() }.toSet()
    }

    fun getAllOrgNumbers(): Set<String?> {
        return allRecipients.map { it.orgNumber?.lowercase() }.toSet()
    }

    fun changeCompanyName(
        scene: Scene,
        idx: Int,
        blockedNames: Collection<String>,
    ) {
        validationLoop(
            message = "What is the name of the recipient?",
            prefill = recipient.companyName,
            reader = reader,
            scene = scene,
            onSuccess = { newValue ->
                recipient.companyName = newValue
                scene.replaceRow(idx, singleRecipientGrid(recipient))
            },
            *RecipientDTO.companyNameValidators,
            Validator({ !blockedNames.contains(it.lowercase()) }, "Name already taken"),
        )
    }

    fun changeAddress(
        scene: Scene,
        idx: Int,
    ) {
        validationLoop(
            message = "What is the address of the recipient?",
            prefill = recipient.address,
            reader = reader,
            scene = scene,
            onSuccess = { newValue ->
                recipient.address = newValue
                scene.replaceRow(idx, singleRecipientGrid(recipient))
            },
            *RecipientDTO.addressValidators,
        )
    }

    fun changePostal(
        scene: Scene,
        idx: Int,
    ) {
        validationLoop(
            message = "What is the postal code and city of the recipient?",
            prefill = recipient.postal,
            reader = reader,
            scene = scene,
            onSuccess = { newValue ->
                recipient.postal = newValue
                scene.replaceRow(idx, singleRecipientGrid(recipient))
            },
            *RecipientDTO.postalValidators,
        )
    }

    fun changeEmail(
        scene: Scene,
        idx: Int,
    ) {
        validationLoop(
            message = "What is the email address of the recipient?",
            prefill = recipient.email,
            reader = reader,
            scene = scene,
            onSuccess = { newValue ->
                recipient.email = newValue
                scene.replaceRow(idx, singleRecipientGrid(recipient))
            },
            *RecipientDTO.emailValidators,
        )
    }

    fun changeOrgNumber(
        scene: Scene,
        idx: Int,
        blockedNumbers: Collection<String?>,
    ) {
        validationLoop(
            message = "What is the Organization Number of the recipient? (optional)",
            prefill = recipient.orgNumber ?: "",
            reader = reader,
            scene = scene,
            onSuccess = { newValue ->
                recipient.orgNumber = newValue
                scene.replaceRow(idx, singleRecipientGrid(recipient))
            },
            Validator(
                { it.isBlank() || !blockedNumbers.contains(it) },
                "This Organization Number is already registered to another recipient.",
            ),
        )
    }

    suspend fun registrationMenu() {
        allRecipients = getAllRecipients()
        val allRecipientNames = getAllRecipientNames()
        val allOrgNumbers = getAllOrgNumbers()

        var scene = MordantScene(writer)
        scene.addRow(renderLogo(recipientLogos.random(), fontName = th.secondaryFont, style = th.primary))


        var grid = singleRecipientGrid(recipient)

        val idx = scene.addRow(grid)
        navigationLoop {
            changeCompanyName(scene, idx, allRecipientNames)
            changeAddress(scene, idx)
            changePostal(scene, idx)
            changeEmail(scene, idx)
            changeOrgNumber(scene, idx, allOrgNumbers)
            scene.replaceRow(
                idx,
                Panel(
                    singleRecipientGrid(recipient, padding = false),
                    bottomTitle = isCorrectYesNo,
                    bottomTitleAlign = TextAlign.RIGHT,
                    borderStyle = th.secondary,
                ),
            )
            scene.addRow()
            scene.addRow()

            scene.display()
            var rawChar = reader.getRawCharIn('y', 'n')
            when (rawChar) {
                'y' -> {
                    try {
                        writer.withLoading(
                            { addRecipientWithCacheUpdate(recipient) },
                            message = "saving recipient"
                        )
                        return exitToMainMenu(
                            writer,
                            reader,
                            th.success("Recipient registered: ${recipient.companyName}.")
                        )
                    } catch (e: Exception) {
                        return exitToMainMenu(writer, reader, th.error("Errror: ${e.message}"))
                    }
                }

                'n' -> {
                    scene.replaceRow(idx, singleRecipientGrid(recipient))
                }
            }
        }
    }

    suspend fun updateRecipientMenu() {
        val scene = MordantScene(writer)
        allRecipients = getAllRecipients()
        val allRecipientNames = getAllRecipientNames()
        val allOrgNumbers = getAllOrgNumbers()

        scene.addRow(renderLogo(recipientLogos.random(), th.primary, th.secondaryFont))
        if (allRecipients.isEmpty()) {
            return exitToMainMenu(writer, reader, "No recipients registered yet.")
        }
        val allRecipientKeybinds = createKeybinds(allRecipients)

        scene.addRow(recipientChoiceGrid(allRecipients))
        scene.addRow()
        scene.addRow("Choose your target.")
        scene.display()
        scene.removeLast(3)
        val choice = reader.getRawCharIn(*allRecipientKeybinds, 'q')
        if (choice == 'q') {
            return
        }
        recipient =
            allRecipients.find { it.keybind == choice }
                ?: return exitToMainMenu(writer, reader, "ERROR: Could not find recipient")
        val otherRecipientNames = allRecipientNames - recipient.companyName.lowercase()

        var idx = scene.addRow(singleRecipientGrid(recipient))
        scene.display()
        navigationLoop {
            changeCompanyName(scene, idx, blockedNames = otherRecipientNames)
            changeAddress(scene, idx)
            changePostal(scene, idx)
            changeEmail(scene, idx)
            val otherOrgNumbers =
                if (recipient.orgNumber.isNullOrBlank()) allOrgNumbers else allOrgNumbers - recipient.orgNumber
            changeOrgNumber(scene, idx, blockedNumbers = otherOrgNumbers)

            scene.replaceRow(
                idx,
                Panel(
                    singleRecipientGrid(recipient, padding = false),
                    bottomTitle = isCorrectYesNo,
                    bottomTitleAlign = TextAlign.RIGHT,
                    borderStyle = th.secondary,
                ),
            )
            scene.addRow()
            scene.addRow()
            scene.display()
            scene.removeLast(2)
            var rawChar = reader.getRawCharIn('y', 'n')
            when (rawChar) {
                'y' -> {
                    try {
                        writer.withLoading(
                            { updateRecipientWithCacheUpdate(recipient) },
                            message = "updating information"
                        )
                        scene.addRow("Recipient updated: ${recipient.companyName}.")
                    } catch (e: Exception) {
                        scene.addRow(th.error("Error: ${e.message}"))
                    }
                    scene.addRow(pressEnterMainMenu)
                    scene.display()
                    reader.waitForEnter()
                    exit()
                }

                'n' -> {
                    scene.replaceRow(idx, singleRecipientGrid(recipient))
                }
            }
        }
    }
}
