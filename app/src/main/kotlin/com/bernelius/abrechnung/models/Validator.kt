package com.bernelius.abrechnung.models

import java.time.LocalDate

data class Validator(
    val rule: (String) -> Boolean,
    val failedMessage: (String) -> String,
) {
    // Secondary constructor to allow passing a simple String instead of a lambda
    constructor(rule: (String) -> Boolean, failedMessage: String) :
        this(rule, { failedMessage })
}

val isParseableDate =
    Validator(
        { LocalDate.parse(it) != null },
        { "Failed to parse date: $it. Expected format: YYYY-MM-DD" },
    )

val isNotBlank =
    Validator(
        { it.isNotBlank() },
        "Field cannot be left blank.",
    )

val isPositive =
    Validator(
        {
            it.toDouble() >= 0.0
        },
        "Number must not be negative.",
    )

val isGrEqOne =
    Validator(
        {
            it.toDouble() >= 1.0
        },
        "Number must be greater than or equal to 1.",
    )

val isInteger =
    Validator(
        { it.toIntOrNull() != null },
        "Must be a valid integer.",
    )

val isBlankOrInteger =
    Validator(
        { it.isBlank() || it.toIntOrNull() != null },
        "Must be integer or blank",
    )

val isBlankOrPositiveInteger =
    Validator(
        { it.isBlank() || (it.toIntOrNull() != null && it.toInt() > 0) },
        "Must be a valid positive integer or left blank.",
    )

val isBlankOrIntegerBetweenZeroAndHundred =
    Validator(
        { it.isBlank() || (it.toIntOrNull() != null && it.toInt() in 0..100) },
        "Must be a valid integer between 0 and 100 or left blank.",
    )

val isDouble =
    Validator(
        { it.toDoubleOrNull() != null },
        "Must be a valid number.",
    )

val isBetweenOneAndHundred =
    Validator(
        {
            it.toDouble() in 1.0..100.0
        },
        "Must be a number between 1 and 100.",
    )

val isEmail =
    Validator(
        {
            it.contains("@")
        },
        "Must be a valid email address.",
    )

val isBetweenZeroAndHundred =
    Validator(
        {
            it.toDouble() in 0.0..100.0
        },
        "Must be a number between 0 and 100.",
    )
