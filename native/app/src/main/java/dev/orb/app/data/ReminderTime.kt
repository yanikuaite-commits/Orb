package dev.orb.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

object ReminderTime {
    fun parse(value: String): ZonedDateTime? {
        val clean = value.trim()
        if (clean.isBlank()) return null
        return runCatching { OffsetDateTime.parse(clean).toZonedDateTime() }.getOrNull()
            ?: runCatching { ZonedDateTime.parse(clean) }.getOrNull()
            ?: runCatching { Instant.parse(clean).atZone(ZoneId.systemDefault()) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(clean).atZone(ZoneId.systemDefault()) }.getOrNull()
            ?: runCatching { LocalDate.parse(clean).atStartOfDay(ZoneId.systemDefault()) }.getOrNull()
    }
}
