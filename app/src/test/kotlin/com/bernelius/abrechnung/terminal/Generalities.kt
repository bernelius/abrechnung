package com.bernelius.abrechnung.terminal

import com.bernelius.abrechnung.utils.ansiRegex
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.runBlocking

class MockReader(
    val inputs: List<String>,
    val rawChars: List<Char> = emptyList(),
    var i: Int = 0,
    var rawI: Int = 0,
) : InputReader {
    constructor(vararg inputs: String) : this(inputs.toList())

    override fun readAndCenter(
        prefill: String,
        pos: Int?,
        mask: Boolean,
    ): String = if (i < inputs.size) inputs[i++] else ""

    override fun getRawCharIn(vararg allowed: Char): Char =
        if (rawI < rawChars.size && rawChars[rawI] in allowed) rawChars[rawI++] else error("No valid input supplied")

    override fun waitForEnter() {
        return
    }
}

class MockWriter : Writer {
    override val t: Terminal = Terminal()

    override fun print(scene: Scene): Int {
        println(scene.rows.joinToString("\n") { it.harden(this) })
        return 0
    }

    override suspend fun <T> withLoading(
        block: suspend () -> T,
        message: String?,
        pos: Int?,
        scene: Scene?,
    ): T = block()
}

class MockSceneRow(
    override val contents: String,
) : SceneRow {
    override fun harden(writer: Writer): String = contents
}

/*
* This is a mock scene without the ability to remove rows.
* replaceRow(idx, Widget) will still replace a row, but removeLast() is a no-op.
*/
class MockScene : Scene {
    override val rows = mutableListOf<SceneRow>()

    override fun addRow(vararg widgets: Widget): Int {
        rows.add(MockSceneRow(widgets.joinToString("") { it.toString() }))
        // this will likely be wonky with multiple widgets. should not matter
        return rows.size - 1
    }

    override fun addRow(text: String): Int {
        rows.add(MockSceneRow(text.replace(ansiRegex, "")))
        return rows.size - 1
    }

    override fun display(): Int {
        // this value is used in the application to determine the position for the input cursor
        // not necessary for testing
        return 0
    }

    override fun removeLast() {
        return
    }

    override fun removeLast(n: Int) {
        return
    }

    override fun replaceRow(
        idx: Int,
        contents: Widget,
    ) {
        rows[idx] = MockSceneRow(contents.toString())
    }

    override fun clear() {
        rows.clear()
    }
}
