package com.bernelius.abrechnung.utils

import com.github.ajalt.mordant.rendering.TextStyle
import com.github.lalyos.jfiglet.FigletFont
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

fun renderLogo(
    logoName: String,
    style: TextStyle = th.primary,
    fontName: String = th.secondaryFont,
): String {
    return style(makeLogo(logoName, fontName))
}
