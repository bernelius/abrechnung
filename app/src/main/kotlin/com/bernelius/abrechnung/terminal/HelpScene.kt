package com.bernelius.abrechnung.terminal

import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.rendering.Whitespace
import com.github.ajalt.mordant.widgets.Padding
import com.github.ajalt.mordant.widgets.Panel
import com.github.ajalt.mordant.widgets.Text
import com.bernelius.abrechnung.theme.Theme as th

class HelpScene(
    private val writer: Writer,
    private val reader: InputReader,
) {
    fun helpDisplay(intro: Boolean = false) {
        val scene = MordantScene(writer)

        val helpBuilder = StringBuilder()
        helpBuilder.appendLine(
            "${th.tertiary("Abrechnung")} (german: Settlement, Reckoning) is a simple terminal based invoice management tool.",
        )
        helpBuilder.appendLine(
            "It is designed to get out of your way, and to get you to a finished (and sent) invoice as quickly as possible.",
        )
        helpBuilder.appendLine("It also includes the necessary music to keep you motivated while working on your abrechnungs.")
        helpBuilder.appendLine()
        helpBuilder.appendLine("Whenever you see something that looks like this: ${th.primary("o)")}, you have spotted a keybinding.")
        helpBuilder.appendLine("Press this button on your keyboard to trigger the associated behavior.")
        helpBuilder.appendLine(
            "Whenever possible, these keybinds are logical shortcuts. ${th.primary("q)")} for quit, ${
                th.primary(
                    "h)",
                )
            } for help, etc.",
        )
        helpBuilder.appendLine(" ${th.underline("There is no mouse support")}, so you will have to get comfortable with the keyboard.")
        helpBuilder.appendLine()
        helpBuilder.appendLine("The following keybind is globally available:")
        helpBuilder.appendLine("${th.primary("Ctrl-c")}: Quit to outer menu, or if already in the outermost menu, exit the program.")
        helpBuilder.appendLine()
        if (intro) {
            helpBuilder.appendLine("To send your first invoice, you need to register a recipient from the main menu.")
            helpBuilder.appendLine("After this is done, you will be able to create invoices under the generate invoice menu option.")
            helpBuilder.appendLine()
            helpBuilder.appendLine()
            helpBuilder.appendLine()
            helpBuilder.appendLine("${th.primary("f) ")}First, let's define the information needed to create an invoice.")
        } else {
            helpBuilder.appendLine(
                "There is extensive theming support, so you can customize the colors of the terminal app to your liking.",
            )
            helpBuilder.appendLine("You can also redefine the language of the invoice outputs. The default language is English.")
            helpBuilder.appendLine("See the readme at [https://github.com/bernelius/abrechnung] for details.")
        }

        val help =
            Text(
                helpBuilder.toString(),
                align = TextAlign.CENTER,
                whitespace = Whitespace.PRE_WRAP,
            )

        val info =
            Panel(
                help,
                borderStyle = th.secondary,
                title =
                    if (intro) {
                        null
                    } else {
                        Text(th.secondary("Hilfe"))
                    },
                bottomTitle =
                    if (intro) {
                        null
                    } else {
                        Text(th.secondary(PRESS_ENTER_CONTINUE))
                    },
                bottomTitleAlign = TextAlign.RIGHT,
                padding = Padding(1, 3, 1, 3),
            )

        scene.addRow(info)
        scene.display()
        if (intro) {
            reader.getRawCharIn('f')
        } else {
            reader.waitForEnter()
        }
    }
}
