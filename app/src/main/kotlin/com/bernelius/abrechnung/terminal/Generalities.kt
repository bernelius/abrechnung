package com.bernelius.abrechnung.terminal

import com.bernelius.abrechnung.models.RecipientDTO
import com.bernelius.abrechnung.models.Validator
import com.bernelius.abrechnung.terminalConfig
import com.bernelius.abrechnung.utils.Outcome
import com.bernelius.abrechnung.utils.renderLogo
import com.bernelius.abrechnung.utils.validateInput
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.table.GridBuilder
import com.github.ajalt.mordant.widgets.Text
import com.bernelius.abrechnung.theme.Theme as th

data class StyledMenuOption(
    val key: Char,
    val style: TextStyle,
    val value: String,
)

data class UnstyledMenuOption(
    val key: Char,
    val value: String,
)

inline fun navigationLoop(block: LoopScope.() -> Unit) {
    val scope = LoopScope()
    while (scope.running) {
        block(scope)
    }
}

class LoopScope(
    var running: Boolean = true,
) {
    fun exit() {
        running = false
    }
}

internal fun highlightKeyInText(
    style: TextStyle?,
    key: Char,
    text: String,
    capitalize: Boolean = true,
    ignoreCaseOnKeyComparison: Boolean = true,
): String {
    val theStyle = style ?: th.norm
    val out = StringBuilder()
    for (i in text.indices) {
        val l = text[i]
        if (l.equals(key, ignoreCase = ignoreCaseOnKeyComparison)) {
            val styledChar =
                l.toString().let {
                    if (capitalize) it.uppercase() else it
                }
            out.append(theStyle(styledChar) + text.substring(i + 1))
            break
        } else {
            out.append(l)
        }
    }
    return out.toString()
}

fun GridBuilder.stdMenuRow(
    key: Char,
    text: String,
    style: TextStyle = th.primary,
) {
    val highlightStyle = if (terminalConfig.outerHotkeys) null else style
    val styledText = highlightKeyInText(highlightStyle, key, text)
    return row {
        if (terminalConfig.outerHotkeys) {
            cell(style("$key)"))
        }
        cell(styledText)
    }
}

fun exitToMainMenu(
    writer: Writer,
    reader: InputReader,
    message: String,
    logoText: String? = null,
    logoFont: String? = null,
) = exitToMainMenu(writer, reader, Text(message), logoText, logoFont)

fun exitToMainMenu(
    writer: Writer,
    reader: InputReader,
    message: Widget?,
    logoText: String? = null,
    logoFont: String? = null,
) {
    val scene = MordantScene(writer)
    if (logoText != null) {
        scene.addRow(renderLogo(logoText, fontName = logoFont ?: th.secondaryFont))
    }
    if (message != null) {
        scene.addRow(message)
    }
    scene.addRow(pressEnterMainMenu)
    scene.display()
    reader.waitForEnter()
    return
}

fun validationLoop(
    message: String,
    prefill: String,
    reader: InputReader,
    scene: Scene,
    onSuccess: (String) -> Unit = {},
    vararg validators: Validator,
    maskInput: Boolean = false,
): String =
    validationLoop(
        message = Text(message),
        prefill = prefill,
        reader = reader,
        scene = scene,
        onSuccess = onSuccess,
        *validators,
        maskInput = maskInput,
    )

fun validationLoop(
    message: Widget,
    prefill: String,
    reader: InputReader,
    scene: Scene,
    onSuccess: (String) -> Unit = {},
    vararg validators: Validator,
    maskInput: Boolean = false,
): String {
    scene.addRow(message)
    var pos = scene.display()
    var result: Outcome? = null
    do {
        if (result is Outcome.Error) {
            scene.addRow(th.error(result.message))
            pos = scene.display()
            scene.removeLast()
        }
        result = validateInput(reader.readAndCenter(prefill = prefill, pos = pos, mask = maskInput), *validators)
    } while (result is Outcome.Error)
    val value = (result as Outcome.Success).value
    // Remove the prompt
    scene.removeLast()
    // usually, we replace the grid with a new one
    onSuccess(value)
    scene.display()
    return value
}

fun recipientChoiceGrid(recipients: List<RecipientDTO>): Widget {
    val lines =
        recipients.map { recipient ->
            Text("${th.primary(recipient.keybind.toString())}) ${recipient.companyName}")
        }
    return createGrid(*lines.chunked(2).toTypedArray(), sizes = listOf(30, 30))
}
