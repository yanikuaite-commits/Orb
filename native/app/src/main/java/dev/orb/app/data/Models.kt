package dev.orb.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ToolTrace(
    val name: String = "",
    val arguments: JsonElement? = null,
    val result: JsonElement? = null,
)

@Serializable
data class MemoryEntry(
    val id: String = "",
    val category: String = "notes",
    val key: String = "",
    val value: String = "",
    @SerialName("created_at") val createdAt: Double = 0.0,
    @SerialName("updated_at") val updatedAt: Double = 0.0,
)

@Serializable
data class HistoryMessage(
    val role: String = "",
    val text: String = "",
    val ts: Double = 0.0,
)

@Serializable
data class Reminder(
    val id: String = "",
    val text: String = "",
    @SerialName("due_at") val dueAt: Double = 0.0,
    @SerialName("due_iso") val dueIso: String = "",
    @SerialName("created_at") val createdAt: Double = 0.0,
    val fired: Boolean = false,
)

val ORB_VOICES = listOf("Charon", "Kore", "Puck", "Fenrir", "Aoede", "Orus")
