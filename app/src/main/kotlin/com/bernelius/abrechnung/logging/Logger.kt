package com.bernelius.abrechnung.logging

import com.bernelius.abrechnung.utils.getLogDir
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.core.FileAppender
import org.slf4j.LoggerFactory
import java.io.FileOutputStream
import java.io.PrintStream

fun configureLogging() {
    val logDir = getLogDir()
    System.setErr(PrintStream(FileOutputStream("$logDir/abrechnung-stderr.log", false), true, "UTF-8"))
    val context = LoggerFactory.getILoggerFactory() as LoggerContext
    context.reset()

    val encoder = PatternLayoutEncoder().apply {
        this.context = context
        pattern = "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger - %msg%n"
        start()
    }

    val fileAppender = FileAppender<ch.qos.logback.classic.spi.ILoggingEvent>().apply {
        this.context = context
        file = "$logDir/abrechnung.log"
        isAppend = false
        this.encoder = encoder
        start()
    }

    val rootLogger = context.getLogger("ROOT")
    rootLogger.level = Level.WARN
    rootLogger.addAppender(fileAppender)
}
