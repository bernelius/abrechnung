package com.bernelius.abrechnung.database

import com.bernelius.abrechnung.models.InvoiceItemDTO
import com.bernelius.abrechnung.models.RecipientDTO
import com.bernelius.abrechnung.models.UserConfigDTO
import org.jetbrains.exposed.v1.core.ResultRow

fun ResultRow.toRecipientDTO() =
    RecipientDTO(
        id = this[RecipientsTable.id],
        companyName = this[RecipientsTable.companyName],
        address = this[RecipientsTable.address],
        postal = this[RecipientsTable.postal],
        email = this[RecipientsTable.email],
        orgNumber = this[RecipientsTable.orgNumber],
    )

fun ResultRow.toInvoiceItemDTO() =
    InvoiceItemDTO(
        id = this[InvoiceItemsTable.id],
        description = this[InvoiceItemsTable.description],
        quantity = this[InvoiceItemsTable.quantity],
        unitPrice = this[InvoiceItemsTable.unitPrice],
        discount = this[InvoiceItemsTable.discount],
    )

fun ResultRow.toUserConfigDTO(): UserConfigDTO =
    UserConfigDTO(
        name = this[UserConfigTable.name],
        address = this[UserConfigTable.address],
        postal = this[UserConfigTable.postal],
        email = this[UserConfigTable.email],
        accountNumber = SecureVault.decrypt(this[UserConfigTable.encryptedAccountNumber]) ?: "",
        orgNumber = this[UserConfigTable.orgNumber],
        smtpHost = this[UserConfigTable.smtpHost],
        smtpPort = this[UserConfigTable.smtpPort],
        smtpUser = this[UserConfigTable.smtpUser],
        emailPassword = SecureVault.decrypt(this[UserConfigTable.encryptedEmailPassword]),
    )
