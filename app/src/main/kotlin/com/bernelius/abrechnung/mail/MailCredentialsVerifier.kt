package com.bernelius.abrechnung.mail

import com.bernelius.abrechnung.models.EmailUserDTO
import com.bernelius.abrechnung.utils.Outcome
import jakarta.mail.Session
import java.util.Properties

fun verifyEmailConfig(
    userConfig: EmailUserDTO,
    timeout: Int,
): Outcome {
    val timeoutMillis = timeout * 1000
    val props =
        Properties().apply {
            put("mail.smtp.port", userConfig.port.toString())
            put("mail.smtp.auth", true.toString())
            put("mail.smtp.starttls.enable", true.toString())

            put("mail.smtp.connectiontimeout", timeoutMillis.toString())
            put("mail.smtp.timeout", timeoutMillis.toString())
            put("mail.smtp.writetimeout", timeoutMillis.toString())
        }
    return try {
        val session = Session.getInstance(props)
        val transport = session.getTransport("smtp")
        transport.connect(userConfig.host, userConfig.port, userConfig.email, userConfig.password)
        transport.close()
        Outcome.Success("Email credentials valid")
    } catch (e: Exception) {
        if (e.message!!.contains("timeout")) {
            Outcome.Error(
                "Timeout when validating email credentials.\nThis could mean that your email provider is not reachable.\nIt could also mean that you do not have an internet connection.",
            )
        } else {
            return Outcome.Error(e.message ?: "Unknown error")
        }
    }
}
