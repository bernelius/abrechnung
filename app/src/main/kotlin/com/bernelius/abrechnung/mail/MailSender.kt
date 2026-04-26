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
import java.util.Properties


fun sendMail(
    from: EmailUserDTO,
    attachment: String,
    recipient: RecipientDTO,
    emailSubject: String,
    body: String,
): Outcome {
    try {
        val props =
            Properties().apply {
                put("mail.smtp.host", from.host)
                put("mail.smtp.port", from.port.toString())
                put("mail.smtp.auth", true.toString())
                put("mail.smtp.starttls.enable", true.toString())
            }

        val myEmail: String = from.email
        val myPassword: String = from.password
        val session =
            Session.getInstance(
                props,
                object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication =
                        PasswordAuthentication(myEmail, myPassword)
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
        return Outcome.Error(e.message ?: "Unknown error")
    }
}
