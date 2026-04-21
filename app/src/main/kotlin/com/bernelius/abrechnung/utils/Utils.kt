package com.bernelius.abrechnung.utils

import com.bernelius.abrechnung.cache.InvoiceCache
import com.bernelius.abrechnung.cache.RecipientCache
import com.bernelius.abrechnung.cache.UserConfigCache
import com.bernelius.abrechnung.models.Validator
import java.io.File
import java.awt.Color
import java.nio.file.Path
import java.nio.file.Paths

fun hexColor(hex: String): Color {
    val cleanHex = hex.removePrefix("#")
    val colorInt = cleanHex.toInt(16)
    return Color(colorInt)
}

fun getEnv(
    key: String,
    require: Boolean = false,
): String? {
    val envVar = System.getenv(key)
    if (envVar == null && require) {
        throw IllegalArgumentException("Environment variable $key not set")
    }
    return envVar
}

val ansiRegex = Regex("\u001B\\[[0-9;]*[a-zA-Z]")

fun validateInput(
    input: String,
    vararg validators: Validator,
): Outcome {
    val failed = validators.firstOrNull { !it.rule(input) }

    return if (failed == null) {
        Outcome.Success(input)
    } else {
        Outcome.Error(failed.failedMessage(input))
    }
}

sealed class Outcome {
    data class Success(
        val value: String,
    ) : Outcome()

    data class Error(
        val message: String,
    ) : Outcome()
}

fun exitProgram() {
    RecipientCache.invalidateAll()
    InvoiceCache.invalidateAll()
    UserConfigCache.invalidateAll()
    kotlin.system.exitProcess(0)
}

fun getProjectDir(): Path {
    System.getenv("ABRECHNUNG_PROJECT_DIR")?.let {
        return Path.of(it)
    }

    val startDir = Paths.get(
        System.getProperty("java.class.path").split(File.pathSeparator)
            .firstOrNull { it.endsWith(".jar") }
            ?.let { File(it).parentFile?.absolutePath }
            ?: System.getProperty("user.dir")
    ).toAbsolutePath()

    var current = startDir.normalize()

    while (current != current.parent) {
        if (File(current.toFile(), ".abrechnung").exists()) {
            return current
        }
        val parent = current.parent ?: break
        current = parent
    }

    System.err.println("WARNING: Could not find .abrechnung marker. Using working directory.")

    return Paths.get("").toAbsolutePath()
}
