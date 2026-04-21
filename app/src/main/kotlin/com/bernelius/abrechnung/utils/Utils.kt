package com.bernelius.abrechnung.utils

import com.bernelius.abrechnung.cache.InvoiceCache
import com.bernelius.abrechnung.cache.RecipientCache
import com.bernelius.abrechnung.cache.UserConfigCache
import com.bernelius.abrechnung.models.Validator
import java.awt.Color
import java.nio.file.Path

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

/**
 * Returns the platform-specific directory for log files.
 *
 * Logs are written to:
 * - Windows: %LOCALAPPDATA%\Abrechnung\logs\
 * - Linux: ~/.local/share/abrechnung/logs/
 * - macOS: ~/Library/Logs/Abrechnung/
 *
 * Can be overridden with ABRECHNUNG_LOG_DIR environment variable.
 *
 * @throws IllegalStateException if the directory cannot be created
 */
fun getLogDir(): Path {
    // Check for environment variable override
    System.getenv("ABRECHNUNG_LOG_DIR")?.let {
        val path = Path.of(it).toAbsolutePath()
        path.toFile().mkdirs()
        return path
    }

    val osName = System.getProperty("os.name").lowercase()
    val logDir = when {
        osName.contains("win") -> {
            val localAppData = System.getenv("LOCALAPPDATA")
                ?: throw IllegalStateException("LOCALAPPDATA environment variable not set")
            Path.of(localAppData, "Abrechnung", "logs")
        }
        osName.contains("mac") -> {
            val home = System.getProperty("user.home")
            Path.of(home, "Library", "Logs", "Abrechnung")
        }
        else -> {
            // Linux and other Unix-like systems
            val xdgDataHome = System.getenv("XDG_DATA_HOME")
            if (xdgDataHome != null) {
                Path.of(xdgDataHome, "abrechnung", "logs")
            } else {
                val home = System.getProperty("user.home")
                Path.of(home, ".local", "share", "abrechnung", "logs")
            }
        }
    }

    val logDirFile = logDir.toFile()
    if (!logDirFile.exists() && !logDirFile.mkdirs()) {
        throw IllegalStateException(
            "Failed to create log directory: $logDir\n" +
                "Please ensure you have write permissions or set ABRECHNUNG_LOG_DIR environment variable."
        )
    }

    return logDir.toAbsolutePath()
}

/**
 * Returns the platform-specific directory for persistent data (database, etc.).
 *
 * Data is stored in:
 * - Windows: %APPDATA%\Abrechnung\data\
 * - Linux: ~/.local/share/abrechnung/
 * - macOS: ~/Library/Application Support/Abrechnung/
 *
 * Can be overridden with ABRECHNUNG_DATA_DIR environment variable.
 *
 * @throws IllegalStateException if the directory cannot be created
 */
fun getDataDir(): Path {
    // Check for environment variable override
    System.getenv("ABRECHNUNG_DATA_DIR")?.let {
        val path = Path.of(it).toAbsolutePath()
        path.toFile().mkdirs()
        return path
    }

    val osName = System.getProperty("os.name").lowercase()
    val dataDir = when {
        osName.contains("win") -> {
            val appData = System.getenv("APPDATA")
                ?: throw IllegalStateException("APPDATA environment variable not set")
            Path.of(appData, "Abrechnung", "data")
        }
        osName.contains("mac") -> {
            val home = System.getProperty("user.home")
            Path.of(home, "Library", "Application Support", "Abrechnung")
        }
        else -> {
            // Linux and other Unix-like systems
            val xdgDataHome = System.getenv("XDG_DATA_HOME")
            if (xdgDataHome != null) {
                Path.of(xdgDataHome, "abrechnung")
            } else {
                val home = System.getProperty("user.home")
                Path.of(home, ".local", "share", "abrechnung")
            }
        }
    }

    val dataDirFile = dataDir.toFile()
    if (!dataDirFile.exists() && !dataDirFile.mkdirs()) {
        throw IllegalStateException(
            "Failed to create data directory: $dataDir\n" +
                "Please ensure you have write permissions or set ABRECHNUNG_DATA_DIR environment variable."
        )
    }

    return dataDir.toAbsolutePath()
}
