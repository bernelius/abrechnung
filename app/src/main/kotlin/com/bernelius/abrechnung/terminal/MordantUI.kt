package com.bernelius.abrechnung.terminal

import kotlinx.coroutines.*
import com.bernelius.abrechnung.ExitSignal
import com.github.ajalt.mordant.animation.textAnimation
import com.bernelius.abrechnung.utils.ansiRegex
import com.github.ajalt.mordant.input.coroutines.receiveKeyEventsFlow
import com.github.ajalt.mordant.input.enterRawMode
import com.github.ajalt.mordant.input.isCtrlC
import com.github.ajalt.mordant.rendering.OverflowWrap
import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.table.ColumnWidth
import com.github.ajalt.mordant.table.grid
import com.github.ajalt.mordant.table.horizontalLayout
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Padding
import com.github.ajalt.mordant.widgets.Panel
import com.github.ajalt.mordant.widgets.Text
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.runBlocking
import java.lang.AutoCloseable
import kotlin.math.max
import kotlin.time.Duration
import com.bernelius.abrechnung.theme.Theme as th

fun String.visualLength(): Int {
    var length = this.length
    val matcher = ansiRegex.toPattern().matcher(this)
    while (matcher.find()) {
        length -= (matcher.end() - matcher.start())
    }
    return length
}

enum class TitleFont {
    PRIMARY,
    SECONDARY,
}

enum class Style {
    NORMAL,
    PRIMARY,
    SECONDARY,
    TERTIARY,
    SUCCESS,
    ERROR,
    WARNING,
    INFO,
}

interface Writer {
    val t: Terminal

    fun print(scene: Scene): Int

    suspend fun <T> withLoading(
        block: suspend () -> T,
        message: String? = "Loading",
        pos: Int? = null,
        scene: Scene? = null,
    ): T

    companion object {
        const val DEFAULT_LOADING_MESSAGE = "Fetching..."
    }
}

interface InputReader {
    fun readAndCenter(
        prefill: String = "",
        pos: Int?,
        mask: Boolean = false,
    ): String

    fun getRawCharIn(vararg allowed: Char): Char

    fun waitForEnter()
}

interface SceneRow {
    val contents: Any

    fun harden(writer: Writer): String
}

interface Scene {
    val rows: Collection<SceneRow>

    fun addRow(vararg widgets: Widget): Int

    fun addRow(text: String): Int

    fun replaceRow(
        idx: Int,
        contents: Widget,
    )

    fun clear(): Unit

    fun display(): Int

    fun removeLast()

    fun removeLast(n: Int)
}

sealed interface MordantSceneRow : SceneRow {
    class WidgetRow : MordantSceneRow {
        override val contents = mutableListOf<Widget>()

        fun add(widget: Widget) = contents.add(widget)

        fun remove(widget: Widget) {
            contents.remove(widget)
        }

        fun pop(): Widget = contents.removeLast()

        override fun harden(writer: Writer): String =
            writer.t.render(
                horizontalLayout {
                    align = TextAlign.CENTER
                    contents.forEach { cell(it) }
                },
            )
    }

    class TextRow(
        override val contents: String,
    ) : MordantSceneRow {
        override fun harden(writer: Writer): String = contents
    }
}

val pressEnterMainMenu = th.tertiary("...Press Enter to go back to main menu...")
const val PRESS_ENTER_CONTINUE = "...Press Enter to continue..."

class MordantScene(
    private val ui: Writer,
) : Scene {
    override val rows = mutableListOf<MordantSceneRow>()

    override fun addRow(vararg widgets: Widget): Int {
        val row = MordantSceneRow.WidgetRow()
        widgets.forEach { row.add(it) }
        return addInternalRow(row)
    }

    override fun clear() {
        rows.clear()
    }

    fun replaceRow(
        idx: Int,
        contents: String,
    ) {
        val new = MordantSceneRow.TextRow(contents)
        rows[idx] = new
    }

    override fun replaceRow(
        idx: Int,
        contents: Widget,
    ) {
        val new = MordantSceneRow.WidgetRow()
        new.add(contents)
        rows[idx] = new
    }

    private fun addInternalRow(row: MordantSceneRow): Int {
        rows.add(row)
        return rows.lastIndex
    }

    override fun addRow(text: String): Int {
        val row = MordantSceneRow.TextRow(text)
        return addInternalRow(row)
    }

    fun removeRow(rowIndex: Int) {
        rows.removeAt(rowIndex)
    }

    override fun removeLast() {
        rows.removeLast()
    }

    override fun removeLast(n: Int) {
        for (i in 0 until n) {
            removeLast()
        }
    }

    fun removeRow(text: String) {
        for (row in rows) {
            if (row is MordantSceneRow.TextRow && row.contents == text) {
                rows.remove(row)
                return
            }
        }
        error("Row with text $text not found.")
    }

    fun removeRow(row: MordantSceneRow.WidgetRow) {
        rows.remove(row)
    }

    override fun display(): Int = ui.print(this)
}

class GenericTableController(
    private val headers: List<String>,
    initialRows: List<List<Any>>, // Can be String, Text, or Widget
) {
    private val rows = initialRows.map { it.toMutableList() }.toMutableList()

    fun updateCell(
        rowIndex: Int,
        colIndex: Int,
        newValue: Any,
    ) {
        if (rowIndex in rows.indices && colIndex in rows[rowIndex].indices) {
            rows[rowIndex][colIndex] = newValue
        }
    }

    fun removeRow(rowIndex: Int) {
        if (rowIndex in rows.indices) rows.removeAt(rowIndex)
    }

    fun render() =
        table {
            header { row(*headers.toTypedArray()) }
            body {
                rows.forEach { rowData ->
                    row(*rowData.toTypedArray())
                }
            }
        }
}

val isCorrectYesNo = Text("Is this correct? ${th.success("y")}${th.norm("/")}${th.error("n")}")

class MordantUI(
    override val t: Terminal = Terminal(),
    hideCursor: Boolean = true,
) : AutoCloseable,
    InputReader,
    Writer {
    init {
        if (hideCursor) {
            t.cursor.hide()
        }
    }

    val rawTerminal = t

    val size: Size get() = t.size

    override fun print(scene: Scene): Int {
        val lines = scene.rows.joinToString("\n") { it.harden(this) }.lines()
        val adjustment = (t.size.height - lines.size) / 2
        var cursorRow = 0

        // Build the entire frame as a single string and print atomically
        val frame = buildString {
            // Move to top-left
            append(t.cursor.getMoves { setPosition(0, 0) })

            // Top margin
            if (adjustment > 0) {
                repeat(adjustment) {
                    append(t.cursor.getMoves { clearLine() })
                    append("\n")
                }
                cursorRow = adjustment
            }

            // Content lines
            for (line in lines) {
                val padding = max(0, (t.size.width - line.visualLength()) / 2)
                append(" ".repeat(padding))
                append(line)
                append(t.cursor.getMoves { clearLineAfterCursor() })
                append("\n")
                cursorRow++
            }

            // Bottom margin
            while (cursorRow < t.size.height - 1) {
                append(t.cursor.getMoves { clearLine() })
                append("\n")
                cursorRow++
            }
            if (cursorRow < t.size.height) {
                append(t.cursor.getMoves { clearLine() })
            }
        }

        t.rawPrint(frame)
        return adjustment + lines.size - 1
    }

    override fun waitForEnter() {
        val key =
            runBlocking {
                t
                    .receiveKeyEventsFlow()
                    .first()
                    { event ->
                        if (event.isCtrlC) {
                            throw ExitSignal()
                        }
                        event.key == "Enter"
                    }
            }
    }

    override fun getRawCharIn(vararg allowed: Char): Char = getRawCharIn(allowed.toSet())

    fun getRawCharIn(allowed: Set<Char>): Char {
        val key =

            runBlocking {
                t
                    .receiveKeyEventsFlow()
                    .mapNotNull { event ->
                        if (event.isCtrlC) {
                            throw ExitSignal()
                        }
                        if (event.key.length == 1) {
                            val c = event.key.first()
                            if (c in allowed) c else null
                        } else {
                            null
                        }
                    }.first()
            }
        return key
    }

    override fun readAndCenter(
        prefill: String,
        pos: Int?,
        mask: Boolean,
    ): String {
        val buffer = StringBuilder(prefill)

        val rawMode = rawTerminal.enterRawMode()

        try {
            if (pos != null) {
                val height = t.size.height
                t.cursor.move {
                    setPosition(0, pos + 1)
                }
            }

            do {
                val width = t.size.width
                val line =
                    if (mask) {
                        "•".repeat(buffer.length)
                    } else {
                        buffer.toString()
                    }
                val padding = (width - line.length) / 2
                t.print("\r" + " ".repeat(width))
                t.print("\r" + " ".repeat(padding.coerceAtLeast(0)) + line)
                t.cursor.show()
                val event = runBlocking {
                    t.receiveKeyEventsFlow().first()
                }
                if (event.isCtrlC) {
                    throw ExitSignal()
                }
                when (val key = event.key) {
                    "Enter" -> {
                        break
                    }

                    // backspace
                    "Backspace" -> {
                        if (buffer.isNotEmpty()) {
                            buffer.deleteCharAt(buffer.lastIndex)
                        }
                    }

                    else -> {
                        if (key.length == 1) {
                            // if (!Character.isISOControl(key[0])) {
                            // I think this is not needed now?
                            buffer.append(key)
                            // }
                        }
                    }
                }
            } while (true)
        } finally {
            t.cursor.hide()
            t.cursor.move {
                startOfLine()
            }
            rawMode.close()
        }
        return buffer.toString()
    }

    override fun close() {
        t.cursor.show()
    }

    private fun printOverlay(widget: Panel): Int {
        val lines = t.render(widget).split("\n")
        val width = lines[0].visualLength()
        var pos = (t.size.height - lines.size) / 2
        for (line in lines) {
            t.cursor.move {
                setPosition((t.size.width - width) / 2, pos++)
            }
            t.print(line)
        }
        //offset so the spinner location is inside the panel
        return pos - 2
    }

    private val spinnerFrames = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")

    override suspend fun <T> withLoading(
        block: suspend () -> T,
        message: String?,
        pos: Int?,
        scene: Scene?,
    ): T = coroutineScope {
        val terminal = this@MordantUI.t
        val centerX = terminal.size.width / 2
        var spinnerRow = pos ?: (terminal.size.height / 2)

        val deferredBlock = async { block() }
        val start = System.currentTimeMillis()
        val minSpinnerDelay = 50L
        val animationDelay = 60L
        //we wait timeToWait ms no matter what. The spinner will appear only after minSpinnerDelay.
        var timeToWait = 10L

        var frameIndex = 0
        // TODO: finish the nullable message functionality
        var needToPrintPanel = if (message != null) true else false
        while (!deferredBlock.isCompleted) {
            if (System.currentTimeMillis() - start >= minSpinnerDelay) {
                timeToWait = animationDelay
                if (needToPrintPanel) {
                    spinnerRow = printOverlay(
                        Panel(Text(message!!), padding = Padding(1, 1, 2, 1), borderStyle = th.secondary)
                    )
                    needToPrintPanel = false
                }
                terminal.cursor.move {
                    savePosition()
                    setPosition(centerX, spinnerRow)
                }
                terminal.print(spinnerFrames[frameIndex])
                terminal.cursor.move {
                    restorePosition()
                }
                frameIndex = (frameIndex + 1) % spinnerFrames.size
            }
            delay(timeMillis = timeToWait)
        }

        scene?.display()
        deferredBlock.await()
    }
}

fun createGrid(
    vararg rows: List<Any>,
    alignments: List<TextAlign> = listOf(TextAlign.LEFT),
    sizes: List<Int>? = null,
    ellipsisOverflow: Boolean = true,
): Widget =
    grid {
        if (sizes != null) {
            for (i in sizes.indices) {
                column(i) {
                    width = ColumnWidth.Fixed(sizes[i])
                }
            }
        }
        if (ellipsisOverflow) {
            overflowWrap = OverflowWrap.ELLIPSES
        }
        rows.forEach { rowData ->
            row {
                rowData.forEachIndexed { index, cellData ->
                    val cellAlignment = alignments.getOrElse(index) { TextAlign.LEFT }

                    cell(cellData) { align = cellAlignment }
                }
            }
        }
    }
