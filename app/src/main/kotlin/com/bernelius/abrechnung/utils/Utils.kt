package com.bernelius.abrechnung.utils

import com.bernelius.abrechnung.ProgramExitSignal
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
    throw ProgramExitSignal()
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

/**
 * Returns the platform-specific directory for invoice output (PDF files).
 *
 * Invoices are saved to:
 * - Windows: <localized Documents folder>\Abrechnung\
 * - Linux: <xdg-user-dir DOCUMENTS>/Abrechnung/
 * - macOS: ~/Documents/Abrechnung/
 *
 * Can be overridden with ABRECHNUNG_OUTPUT_DIR environment variable.
 *
 * @throws IllegalStateException if the directory cannot be created
 */
fun getOutputDir(): Path {
    // Check for environment variable override
    System.getenv("ABRECHNUNG_OUTPUT_DIR")?.let {
        val path = Path.of(it).toAbsolutePath()
        path.toFile().mkdirs()
        return path
    }

    val osName = System.getProperty("os.name").lowercase()
    val outputDir = when {
        osName.contains("win") -> {
            // Query Windows registry for localized Documents folder path
            val path = getWindowsDocumentsPath()
            path.resolve("Abrechnung")
        }

        osName.contains("mac") -> {
            // macOS - use standard Documents folder name
            val home = System.getProperty("user.home")
            Path.of(home, "Documents", "Abrechnung")
        }

        else -> {
            // Linux - use xdg-user-dir DOCUMENTS or fall back to Documents
            val home = System.getProperty("user.home")
            val path = getLinuxDocumentsPath(home)
            path.resolve("Abrechnung")
        }
    }

    val outputDirFile = outputDir.toFile()
    if (!outputDirFile.exists() && !outputDirFile.mkdirs()) {
        throw IllegalStateException(
            "Failed to create output directory: $outputDir\n" +
                    "Please ensure you have write permissions or set ABRECHNUNG_OUTPUT_DIR environment variable."
        )
    }

    return outputDir.toAbsolutePath()
}


fun getLinuxDocumentsPath(userHome: String): Path {
    val process = ProcessBuilder(
        "xdg-user-dir", "DOCUMENTS"
    ).start()

    process.waitFor()
    val output = process.inputStream.bufferedReader().readText().trim()
    if (output.isBlank() || output == userHome || output.contains("not found", ignoreCase = true))
        return Path.of(userHome, "Documents")
    return Path.of(output)
}

/**
 * Queries the Windows registry to get the localized Documents folder path.
 * Falls back to %USERPROFILE%\Documents if the registry query fails.
 *
 * @return The path to the user's Documents folder
 */
private fun getWindowsDocumentsPath(): Path {
    return try {
        val process = ProcessBuilder(
            "reg", "query",
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Shell Folders",
            "/v", "personal"
        ).start()

        process.waitFor()
        val output = process.inputStream.bufferedReader().readText()

        // Parse the registry output to extract the path
        // Format: HKEY_CURRENT_USER\...
        //     personal    REG_SZ    C:\Users\username\Documents
        val lines = output.lines()
        val pathLine = lines.find { it.trim().startsWith("personal") }

        // Split by whitespace and get the 4th element (the path)
        // The line format is: personal    REG_SZ    <path>
        val path = pathLine?.trim()?.split("\\s+".toRegex())?.drop(2)?.joinToString(" ")?.trim()

        if (path.isNullOrBlank()) {
            throw IllegalStateException("Could not parse Documents path from registry output")
        }
        Path.of(path)
    } catch (e: Exception) {
        // Fallback to user.home\Documents if registry query fails
        Path.of(System.getProperty("user.home"), "Documents")
    }
}
