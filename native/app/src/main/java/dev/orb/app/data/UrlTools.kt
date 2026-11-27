package dev.orb.app.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PRIVATE_HOST = Regex("^(localhost|127\\.|10\\.|192\\.168\\.|172\\.(1[6-9]|2\\d|3[01])\\.)")

fun normalizeServerUrl(raw: String): String {
    val value = raw.trim()
    if (value.isEmpty()) return ""
    if (value.startsWith("http://") || value.startsWith("https://")) {
        return value.trimEnd('/')
    }
    val host = value.substringBefore('/').substringBefore(':')
    val scheme = if (PRIVATE_HOST.containsMatchIn(host)) "http://" else "https://"
    return (scheme + value).trimEnd('/')
}

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
