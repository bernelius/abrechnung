package com.bernelius.abrechnung.mail

import com.bernelius.abrechnung.models.EmailUserDTO
import com.bernelius.abrechnung.models.RecipientDTO
import com.bernelius.abrechnung.utils.Outcome
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MailSenderTest {
    private val validFrom =
        EmailUserDTO(
            email = "sender@test.com",
            password = "password",
            host = "smtp.test.com",
            port = 587,
        )

    private val validRecipient =
        RecipientDTO(
            id = 1,
            companyName = "Test Corp",
            address = "123 Test St",
            postal = "12345",
            email = "recipient@test.com",
            orgNumber = "SE123456789",
        )

    @Test
    fun `sendMail returns error when attachment file does not exist`() {
        val result =
            sendMail(
                from = validFrom,
                attachment = "/nonexistent/path/invoice.pdf",
                recipient = validRecipient,
                emailSubject = "Test Invoice",
                body = "Please find attached your invoice.",
            )

        assertTrue(result is Outcome.Error)
    }

    @Test
    fun `sendMail returns error when attachment path is a directory`() {
        val result =
            sendMail(
                from = validFrom,
                attachment = ".",
                recipient = validRecipient,
                emailSubject = "Test Invoice",
                body = "Please find attached your invoice.",
            )

        assertTrue(result is Outcome.Error)
    }

    @Test
    fun `sendMail returns error when recipient email is invalid`() {
        val invalidRecipient = validRecipient.copy(email = "not-an-email")

        val result =
            sendMail(
                from = validFrom,
                attachment = ".",
                recipient = invalidRecipient,
                emailSubject = "Test Invoice",
                body = "Please find attached your invoice.",
            )

        assertTrue(result is Outcome.Error)
    }
}
