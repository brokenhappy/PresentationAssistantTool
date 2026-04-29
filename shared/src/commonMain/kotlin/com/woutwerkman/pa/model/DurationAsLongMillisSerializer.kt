package com.woutwerkman.pa.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

object DurationAsLongMillisSerializer : KSerializer<Duration> {
    override val descriptor = PrimitiveSerialDescriptor("DurationMs", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Duration) = encoder.encodeLong(value.inWholeMilliseconds)
    override fun deserialize(decoder: Decoder): Duration = decoder.decodeLong().milliseconds
}

object InstantAsLongMillisSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("InstantMs", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeLong(value.toEpochMilliseconds())
    override fun deserialize(decoder: Decoder): Instant = Instant.fromEpochMilliseconds(decoder.decodeLong())
}
