package dev.orb.app.agent

import kotlinx.serialization.json.JsonObject

data class ToolSpec(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

data class LlmToolCall(
    val id: String?,
    val name: String,
    val arguments: JsonObject,
)

data class LlmToolResult(
    val id: String?,
    val name: String,
    val result: String,
)

sealed interface LlmTurn {
    data class Reply(val text: String) : LlmTurn
    data class Tools(val calls: List<LlmToolCall>) : LlmTurn
}

interface TextConversation {
    suspend fun send(text: String): LlmTurn
    suspend fun sendToolResults(results: List<LlmToolResult>): LlmTurn
}

interface TextModelClient {
    val id: String
    val label: String
    val models: List<String>
    val defaultModel: String

    fun hasKey(): Boolean

    suspend fun validateKey(key: String): Result<Unit>

    fun conversation(
        system: String,
        history: List<Pair<String, String>>,
        tools: List<ToolSpec>,
        model: String,
    ): TextConversation
}
