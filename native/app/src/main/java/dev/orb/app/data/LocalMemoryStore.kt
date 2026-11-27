package dev.orb.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
private data class MemoryFile(val entries: List<MemoryEntry> = emptyList())

class LocalMemoryStore(
    private val file: File,
    private val json: Json,
) {
    private val lock = Any()
    private val categories = setOf("identity", "preferences", "projects", "notes", "facts")
    private val stop = setOf(
        "a", "o", "e", "de", "da", "do", "que", "the", "and", "of", "to", "is",
        "um", "uma", "meu", "minha", "my", "i", "you", "eu", "para", "por",
    )

    fun all(): List<MemoryEntry> = synchronized(lock) { read().entries }

    fun add(key: String, value: String, category: String = "notes"): MemoryEntry = synchronized(lock) {
        val cleanKey = key.trim().take(120)
        val cleanValue = value.trim().take(1200)
        val cleanCategory = if (category in categories) category else "notes"
        require(cleanKey.isNotBlank() && cleanValue.isNotBlank()) { "key and value are required" }
        val entries = read().entries.toMutableList()
        val now = System.currentTimeMillis() / 1000.0
        val existing = entries.indexOfFirst {
            it.category == cleanCategory && it.key.equals(cleanKey, ignoreCase = true)
        }
        if (existing >= 0) {
            val updated = entries[existing].copy(value = cleanValue, updatedAt = now)
            entries[existing] = updated
            write(entries)
            return updated
        }
        val entry = MemoryEntry(
            id = UUID.randomUUID().toString().replace("-", "").take(12),
            category = cleanCategory,
            key = cleanKey,
            value = cleanValue,
            createdAt = now,
            updatedAt = now,
        )
        entries += entry
        write(entries)
        entry
    }

    fun delete(id: String): Boolean = synchronized(lock) {
        val entries = read().entries
        val kept = entries.filterNot { it.id == id }
        if (kept.size == entries.size) {
            false
        } else {
            write(kept)
            true
        }
    }

    fun forget(key: String, category: String = ""): Int = synchronized(lock) {
        val entries = read().entries
        val target = key.trim().lowercase()
        val kept = entries.filterNot {
            it.key.lowercase() == target && (category.isBlank() || it.category == category)
        }
        val removed = entries.size - kept.size
        if (removed > 0) write(kept)
        removed
    }

    fun clear() = synchronized(lock) { write(emptyList()) }

    fun search(query: String, limit: Int = 8): List<MemoryEntry> = synchronized(lock) {
        val words = tokens(query)
        val entries = read().entries
        if (words.isEmpty()) return entries.take(limit)
        val scored = mutableListOf<Pair<Int, MemoryEntry>>()
        for (entry in entries) {
            val haystack = "${entry.category} ${entry.key} ${entry.value}"
            val hay = tokens(haystack).toSet()
            var score = 0
            for (word in words) {
                score += when {
                    word in hay -> 3
                    haystack.lowercase().contains(word) -> 1
                    else -> 0
                }
            }
            if (score > 0) scored += score to entry
        }
        scored.sortedWith(
            compareByDescending<Pair<Int, MemoryEntry>> { it.first }
                .thenByDescending { it.second.updatedAt },
        ).take(limit).map { it.second }
    }

    fun summary(limit: Int = 1400): String = synchronized(lock) {
        val entries = read().entries.sortedWith(
            compareBy<MemoryEntry> { it.category != "identity" }
                .thenByDescending { it.updatedAt },
        )
        val lines = mutableListOf<String>()
        var used = 0
        for (entry in entries) {
            val line = "- [${entry.category}] ${entry.key}: ${entry.value}"
            if (used + line.length > limit) break
            lines += line
            used += line.length + 1
        }
        lines.joinToString("\n")
    }

    private fun read(): MemoryFile = runCatching {
        if (file.exists()) json.decodeFromString<MemoryFile>(file.readText()) else MemoryFile()
    }.getOrElse { MemoryFile() }

    private fun write(entries: List<MemoryEntry>) {
        val parent = file.parentFile ?: return
        parent.mkdirs()
        val tmp = File(parent, "${file.name}.tmp")
        tmp.writeText(json.encodeToString(MemoryFile(entries)))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    private fun tokens(text: String): List<String> =
        Regex("[\\p{L}\\p{N}]+").findAll(text.lowercase())
            .map { it.value }
            .filter { it.length > 2 && it !in stop }
            .toList()
}
