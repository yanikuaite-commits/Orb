package dev.orb.app.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatDue(epochSeconds: Double): String {
    if (epochSeconds <= 0.0) return ""
    val instant = Instant.ofEpochSecond(epochSeconds.toLong())
    return DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR"))
        .withZone(ZoneId.systemDefault())
        .format(instant)
}

fun formatClock(epochSeconds: Double): String {
    if (epochSeconds <= 0.0) return ""
    return DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("pt-BR"))
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochSecond(epochSeconds.toLong()))
}
