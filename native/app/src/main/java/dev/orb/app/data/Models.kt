package dev.orb.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Health(
    val status: String = "",
    val name: String = "Orb",
    val version: String = "",
    @SerialName("api_key_set") val apiKeySet: Boolean = false,
    @SerialName("auth_required") val authRequired: Boolean = false,
)

@Serializable
data class OrbConfig(
    @SerialName("assistant_name") val assistantName: String = "Orb",
    @SerialName("user_name") val userName: String = "",
    val language: String = "pt-BR",
    val voice: String = "Charon",
    @SerialName("tts_engine") val ttsEngine: String = "browser",
    val persona: String = "",
    @SerialName("api_key_set") val apiKeySet: Boolean = false,
    val voices: List<String> = emptyList(),
    @SerialName("model_ladder") val modelLadder: List<String> = emptyList(),
    @SerialName("smart_ladder") val smartLadder: List<String> = emptyList(),
    @SerialName("tts_model") val ttsModel: String = "",
)

@Serializable
data class ConfigUpdate(
    @SerialName("assistant_name") val assistantName: String? = null,
    @SerialName("user_name") val userName: String? = null,
    val language: String? = null,
    val voice: String? = null,
    @SerialName("tts_engine") val ttsEngine: String? = null,
    val persona: String? = null,
    @SerialName("api_key") val apiKey: String? = null,
)

@Serializable
data class ChatRequest(val message: String)

@Serializable
data class ToolTrace(
    val name: String = "",
    val arguments: JsonElement? = null,
    val result: JsonElement? = null,
)

@Serializable
data class ChatResponse(
    val reply: String = "",
    val tools: List<ToolTrace> = emptyList(),
    val ok: Boolean = true,
    val error: String? = null,
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
data class MemoryList(val entries: List<MemoryEntry> = emptyList())

@Serializable
data class MemoryRequest(val key: String, val value: String, val category: String = "notes")

@Serializable
data class MemoryCreated(val entry: MemoryEntry? = null)

@Serializable
data class HistoryMessage(
    val role: String = "",
    val text: String = "",
    val ts: Double = 0.0,
)

@Serializable
data class HistoryList(val messages: List<HistoryMessage> = emptyList())

@Serializable
data class Reminder(
    val id: String = "",
    val text: String = "",
    @SerialName("due_at") val dueAt: Double = 0.0,
    @SerialName("due_iso") val dueIso: String = "",
    @SerialName("created_at") val createdAt: Double = 0.0,
    val fired: Boolean = false,
)

@Serializable
data class ReminderList(val reminders: List<Reminder> = emptyList())

@Serializable
data class TtsRequest(val text: String, val voice: String = "")
