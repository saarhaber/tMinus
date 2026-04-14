package com.saarlabs.tminus.util

import kotlin.time.Duration
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.minus
import kotlinx.datetime.offsetAt
import kotlinx.datetime.offsetIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = EasternTimeInstant.Serializer::class)
public class EasternTimeInstant
private constructor(internal val instant: Instant, public val local: LocalDateTime) :
    Comparable<EasternTimeInstant> {
    public constructor(instant: Instant) : this(instant, instant.toLocalDateTime(timeZone))

    public constructor(local: LocalDateTime) : this(local.toInstant(timeZone), local)

    public constructor(date: LocalDate, time: LocalTime) : this(LocalDateTime(date, time))

    public constructor(
        year: Int,
        month: Month,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int = 0,
    ) : this(LocalDateTime(year, month, day, hour, minute, second))

    public val serviceDate: LocalDate
        get() = if (local.hour >= 3) local.date else local.date.minus(DatePeriod(days = 1))

    public fun toEpochMilliseconds(): Long = instant.toEpochMilliseconds()

    public operator fun plus(duration: Duration): EasternTimeInstant =
        EasternTimeInstant(instant + duration)

    public operator fun minus(duration: Duration): EasternTimeInstant =
        EasternTimeInstant(instant - duration)

    public operator fun minus(other: EasternTimeInstant): Duration = instant - other.instant

    override fun compareTo(other: EasternTimeInstant): Int = instant.compareTo(other.instant)

    override fun equals(other: Any?): Boolean {
        return other is EasternTimeInstant && instant == other.instant
    }

    override fun hashCode(): Int = instant.hashCode()

    override fun toString(): String = local.toString() + timeZone.offsetAt(instant).toString()

    internal object Serializer : KSerializer<EasternTimeInstant> {
        override val descriptor =
            PrimitiveSerialDescriptor(
                "com.saarlabs.tminus.util.EasternTimeInstant",
                PrimitiveKind.STRING,
            )

        override fun serialize(encoder: Encoder, value: EasternTimeInstant) {
            val data =
                value.instant.format(
                    DateTimeComponents.Formats.ISO_DATE_TIME_OFFSET,
                    value.instant.offsetIn(timeZone),
                )
            encoder.encodeString(data)
        }

        override fun deserialize(decoder: Decoder): EasternTimeInstant {
            val data = decoder.decodeString()
            return EasternTimeInstant(Instant.parse(data))
        }
    }

    public companion object {
        internal val timeZone: TimeZone by lazy { TimeZone.of("America/New_York") }

        public fun now(clock: Clock = Clock.System): EasternTimeInstant =
            EasternTimeInstant(clock.now())
    }
}
