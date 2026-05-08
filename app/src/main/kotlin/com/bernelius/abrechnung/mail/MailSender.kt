package com.bernelius.abrechnung.mail

import com.bernelius.abrechnung.models.EmailUserDTO
import com.bernelius.abrechnung.models.RecipientDTO
import com.bernelius.abrechnung.utils.Outcome
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import java.io.File
import java.net.SocketTimeoutException
import java.util.Properties
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun sendMail(
    from: EmailUserDTO,
    attachment: String,
    recipient: RecipientDTO,
    emailSubject: String,
    body: String,
    timeout: Duration = 20.seconds,
): Outcome {
    try {
        val timeoutMillis = timeout.inWholeMilliseconds
        val props =
            Properties().apply {
                put("mail.smtp.host", from.host)
                put("mail.smtp.port", from.port.toString())
                put("mail.smtp.auth", true.toString())
                put("mail.smtp.starttls.enable", true.toString())
                put("mail.smtp.connectiontimeout", timeoutMillis.toString())
                put("mail.smtp.timeout", timeoutMillis.toString())
                put("mail.smtp.writetimeout", timeoutMillis.toString())
            }

        val myEmail: String = from.email
        val myPassword: String = from.password
        val session =
            Session.getInstance(
                props,
                object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication = PasswordAuthentication(myEmail, myPassword)
                },
            )

        val message =
            MimeMessage(session).apply {
                setFrom(InternetAddress(from.email))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient.email))
                setRecipients(Message.RecipientType.CC, InternetAddress.parse(from.email))
                subject = emailSubject
            }
        val multipart = MimeMultipart()

        val textPart =
            MimeBodyPart().apply {
                setText(body, "utf-8")
            }
        multipart.addBodyPart(textPart)

        val attachmentPart =
            MimeBodyPart().apply {
                attachFile(File(attachment))
            }
        multipart.addBodyPart(attachmentPart)

        message.setContent(multipart)
        Transport.send(message)
        return Outcome.Success("Mail sent to ${recipient.email}.")
    } catch (e: Exception) {
    val isTimeout = generateSequence(e as Throwable?) { it.cause }
        .any { it is SocketTimeoutException }

    return Outcome.Error(
        when {
            isTimeout -> "Connection timeout"
            else -> e.message ?: "Unknown error"
        }
    )
}
}
