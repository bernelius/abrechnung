package com.bernelius.abrechnung.models

import java.math.RoundingMode
import java.time.LocalDate

data class VatDateResolvedDTO(
    val vatRate: Int,
    val invoiceDate: LocalDate,
    val dueDate: LocalDate,
)

data class VatDateConfigDTO(
    var vatRate: Int,
    var invoiceDateOffset: Long = 0,
    var dueDateOffset: Long,
) {
    fun resolve(today: LocalDate): VatDateResolvedDTO {
        return VatDateResolvedDTO(
            vatRate = vatRate,
            invoiceDate = today.plusDays(invoiceDateOffset),
            dueDate = today.plusDays(invoiceDateOffset).plusDays(dueDateOffset),
        )
    }

    companion object {
        val vatRateValidators = arrayOf(isBlankOrIntegerBetweenZeroAndHundred)
        val invoiceDateOffsetValidators = arrayOf(isBlankOrPositiveInteger)
        val dueDateOffsetValidators = arrayOf(isBlankOrPositiveInteger)
    }

}

data class EmailUserDTO(
    val email: String,
    val password: String,
    val host: String,
    val port: Int,
)

data class UserConfigDTO(
    var name: String = "",
    var address: String = "",
    var postal: String = "",
    var email: String = "",
    var accountNumber: String = "",
    var orgNumber: String? = null,
    var smtpHost: String? = null,
    var smtpPort: String? = null,
    var smtpUser: String? = null,
    var emailPassword: String? = null,
) {
    companion object {
        val nameValidators = arrayOf(isNotBlank)
        val addressValidators = arrayOf(isNotBlank)
        val postalValidators = arrayOf(isNotBlank)
        val emailValidators = arrayOf(isEmail)
        val accountNumberValidators = arrayOf(isNotBlank)
        val orgNumberValidators = emptyArray<Validator>()
        val smtpHostValidators = emptyArray<Validator>()
        val smtpPortValidators = emptyArray<Validator>()
        val smtpUserValidators = emailValidators
        val emailPasswordValidators = emptyArray<Validator>()
    }

    fun isValid(): Boolean {
        return name.isNotBlank() &&
                address.isNotBlank() &&
                postal.isNotBlank() &&
                email.contains("@") &&
                accountNumber.isNotBlank()
    }

    fun emailSemanticallyValid(): Boolean {
        return (!smtpHost.isNullOrBlank() && !smtpPort.isNullOrBlank() && !smtpUser.isNullOrBlank() && emailPassword != null)
    }

    fun toEmailUserDTO(): EmailUserDTO {
        if (!emailSemanticallyValid()) throw IllegalStateException("Email credentials are not valid")
        return EmailUserDTO(
            email = smtpUser ?: email,
            password = emailPassword!!,
            host = smtpHost!!,
            port = smtpPort!!.toInt(),
        )
    }
}

data class RecipientDTO(
    var id: Int = 0,
    var companyName: String = "",
    var address: String = "",
    var postal: String = "",
    var email: String = "",
    var orgNumber: String? = null,
    var keybind: Char? = null,
) {
    companion object {
        val companyNameValidators = arrayOf(isNotBlank)
        val addressValidators = arrayOf(isNotBlank)
        val postalValidators = arrayOf(isNotBlank)
        val emailValidators = arrayOf(isNotBlank, isEmail)
        val orgNumberValidators = emptyArray<Validator>()
        val keybindValidators = emptyArray<Validator>()
    }
}

data class InvoiceInputDTO(
    var invoiceDate: LocalDate,
    var dueDate: LocalDate,
    var vatRate: Int,
    val currency: String,
    var invoiceItems: List<InvoiceItemDTO>,
    var recipient: RecipientDTO?,
) {
    companion object {
        val invoiceDateValidators =
            arrayOf(
                isParseableDate,
            )

        val dueDateValidators =
            arrayOf(
                isParseableDate,
            )

        val vatRateValidators =
            arrayOf(
                isInteger,
                isBetweenOneAndHundred,
            )
    }
}

data class InvoiceDTO(
    var id: Int,
    var invoiceDate: LocalDate,
    var dueDate: LocalDate,
    var vatRate: Int,
    var invoiceItems: List<InvoiceItemDTO>,
    val currency: String,
    var status: String,
    var recipient: RecipientDTO,
) {
    val subtotal: Double
        get() = invoiceItems.sumOf { it.subtotal }

    val vatAmount: Double
        get() = (subtotal * (vatRate.toDouble() / 100)).toBigDecimal().setScale(2, RoundingMode.HALF_UP).toDouble()

    val total: Double
        get() = (subtotal + vatAmount).toBigDecimal().setScale(2, RoundingMode.HALF_UP).toDouble()
}

data class InvoiceItemDTO(
    val id: Int = 0,
    var description: String = "",
    var quantity: Int = 0,
    var unitPrice: Double = 0.0,
    var discount: Int = 0,
) {
    val subtotal: Double
        get() =
            quantity *
                    (unitPrice - unitPrice * (discount.toDouble() / 100))
                        .toBigDecimal()
                        .setScale(2, RoundingMode.HALF_UP)
                        .toDouble()

    companion object {
        val descriptionValidators =
            arrayOf(
                isNotBlank,
            )

        val quantityValidators =
            arrayOf(
                isInteger,
                isGrEqOne,
            )

        val unitPriceValidators =
            arrayOf(
                isDouble,
                isPositive,
            )

        val discountValidators =
            arrayOf(
                isDouble,
                isBetweenZeroAndHundred,
            )
    }
}
