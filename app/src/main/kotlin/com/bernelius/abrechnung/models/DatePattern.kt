package com.bernelius.abrechnung.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.format.DateTimeFormatter
import kotlin.jvm.JvmInline

@Serializable(with = DatePatternSerializer::class)
@JvmInline
value class DatePattern(
    val value: String,
) {
    init {
        require("y" in value && "M" in value && "d" in value) { "DatePattern must contain year, month and day. Example: yyyy-MM-dd" }
        DateTimeFormatter.ofPattern(value)
    }
}

// ktoml did not like the DatePattern value class, so we serialize ourselves
object DatePatternSerializer : KSerializer<DatePattern> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("DatePattern", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: DatePattern,
    ) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): DatePattern = DatePattern(decoder.decodeString())
}
