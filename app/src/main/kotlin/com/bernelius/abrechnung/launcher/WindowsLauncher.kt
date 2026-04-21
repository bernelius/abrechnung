package com.bernelius.abrechnung.launcher

import java.io.File

/**
 * Windows launcher for abrechnung.
 * Launches the main abrechnung.exe with UTF-8 codepage support via Windows Terminal.
 */
fun main(args: Array<String>) {
    // Get the directory where this launcher executable is located
    val exePath = ProcessHandle.current()
        .info()
        .command()
        .orElseThrow { IllegalStateException("Cannot determine executable path") }
    val exeDir = File(exePath).parentFile
    val exeName = "abrechnung.exe"

    val argString = if (args.isNotEmpty()) args.joinToString(" ") else ""

    val command = listOf(
        "cmd",
        "/c",
        """start /max wt.exe -p "Command Prompt" cmd /c "cd /d \"${exeDir.absolutePath}\" && chcp 65001 >nul && $exeName" $argString"""
    )

    ProcessBuilder(command)
        .directory(exeDir)
        .inheritIO()
        .start()
}
