package com.bernelius.abrechnung.utils

import com.github.ajalt.mordant.rendering.TextStyle
import com.github.lalyos.jfiglet.FigletFont
import java.io.File
import com.bernelius.abrechnung.theme.Theme as th

private fun makeLogo(
    pageName: String,
    fontName: String,
): String {
    val resourcePath = "/flf/$fontName.flf"
    val stream = FigletFont::class.java.getResourceAsStream(resourcePath)
    require(stream != null) { "Font file not found in classpath" }
    val logo = FigletFont.convertOneLine(stream, pageName)
    return logo
}

fun sanitizeFileName(fileName: String): String =
    fileName
        .map { char ->
            when (char) {
                in listOf('\\', '/', ':', '*', '?', '"', '<', '>', '|') -> "_${char.code}_"
                else -> char
            }
        }.joinToString("")
        .trimEnd(' ', '.')

fun renderLogo(
    logoName: String,
    style: TextStyle = th.primary,
    fontName: String = th.secondaryFont,
): String {
    val file = File("logos", fontName).resolve("${sanitizeFileName(logoName)}.txt")
    file.parentFile?.mkdirs()
    val logo: String
    if (!file.exists()) {
        logo = makeLogo(logoName, fontName)
        file.writeText(logo)
    } else {
        val fileReader = file.bufferedReader()
        logo = fileReader.readText()
        fileReader.close()
    }
    return style(logo)
}
