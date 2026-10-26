package com.bernelius.abrechnung.logging

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.core.BasicStatusManager
import ch.qos.logback.core.CoreConstants
import org.slf4j.LoggerFactory

/**
 * Runs at native-image build time only.
 *
 * Build-time auto-config records Status objects (whose `origin` references the
 * Joran XML parser machinery) in the LoggerContext's StatusManager and stores
 * the parsed XML model under SAFE_JORAN_CONFIGURATION. Neither can be baked
 * into the image heap. Since the runtime configuration is applied
 * programmatically in configureLogging(), both are simply discarded here,
 * leaving only the quiet placeholder config from logback.xml in the image.
 */
object BuildTimeLogbackCleanup {
    init {
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        (context.statusManager as BasicStatusManager).clear()
        context.removeObject(CoreConstants.SAFE_JORAN_CONFIGURATION)
    }
}
