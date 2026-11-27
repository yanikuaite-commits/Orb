package dev.orb.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class HistoryFile(val messages: List<HistoryMessage> = emptyList())

class HistoryStore(
    private val file: File,
    private val json: Json,
    private val limit: Int = 60,
) {
    private val lock = Any()

    fun messages(max: Int = limit): List<HistoryMessage> = synchronized(lock) {
        read().messages.takeLast(max)
    }

    fun append(role: String, text: String): HistoryMessage = synchronized(lock) {
        val messages = read().messages.toMutableList()
        val message = HistoryMessage(
            role = role,
            text = text,
            ts = System.currentTimeMillis() / 1000.0,
        )
        messages += message
        write(messages.takeLast(limit))
        message
    }

    fun clear() = synchronized(lock) { write(emptyList()) }

    private fun read(): HistoryFile = runCatching {
        if (file.exists()) json.decodeFromString<HistoryFile>(file.readText()) else HistoryFile()
    }.getOrElse { HistoryFile() }

    private fun write(messages: List<HistoryMessage>) {
        val parent = file.parentFile ?: return
        parent.mkdirs()
        val tmp = File(parent, "${file.name}.tmp")
        tmp.writeText(json.encodeToString(HistoryFile(messages)))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }
}
