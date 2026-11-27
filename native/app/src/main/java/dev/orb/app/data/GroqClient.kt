package dev.orb.app.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class GroqMessage(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<GroqToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
)

@Serializable
data class GroqToolCall(
    val id: String = "",
    val type: String = "function",
    val function: GroqFunctionCall,
)

@Serializable
data class GroqFunctionCall(
    val name: String = "",
    val arguments: String = "{}",
)

@Serializable
data class GroqTool(
    val type: String = "function",
    val function: GroqFunctionDef,
)

@Serializable
data class GroqFunctionDef(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

@Serializable
private data class GroqRequest(
    val model: String,
    val messages: List<GroqMessage>,
    val tools: List<GroqTool>? = null,
    @SerialName("tool_choice") val toolChoice: String? = null,
    val temperature: Double = 0.7,
)

@Serializable
data class GroqResponse(val choices: List<GroqChoice> = emptyList())

@Serializable
data class GroqChoice(
    val message: GroqMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

class GroqException(val code: Int, val body: String) : Exception("Groq HTTP $code: ${body.take(400)}") {
    val isQuota: Boolean = code == 429 ||
        body.contains("rate limit", ignoreCase = true) ||
        body.contains("quota", ignoreCase = true)

    val isKey: Boolean = code == 401 || code == 403 ||
        body.contains("invalid_api_key", ignoreCase = true) ||
        body.contains("unauthorized", ignoreCase = true) ||
        body.contains("permission", ignoreCase = true)
}

class GroqClient(
    private val http: HttpClient,
    private val keys: ApiKeyStore,
) {
    fun hasKey(): Boolean = keys.hasGroq()

    suspend fun validateKey(key: String): Result<Unit> {
        val trimmed = key.trim()
        if (trimmed.isBlank()) return Result.failure(IllegalArgumentException("empty key"))
        return try {
            val response = http.get("$BASE/models") {
                header(HttpHeaders.Authorization, "Bearer $trimmed")
            }
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(GroqException(response.status.value, response.bodyAsText()))
            }
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun chat(model: String, messages: List<GroqMessage>, tools: List<GroqTool>): GroqResponse {
        val apiKey = keys.groq()
        if (apiKey.isBlank()) throw GroqException(401, "missing groq api key")
        var last: GroqException? = null
        for (candidate in (listOf(model.trim()) + FALLBACKS).filter { it.isNotBlank() }.distinct()) {
            try {
                return post(candidate, apiKey, messages, tools)
            } catch (error: GroqException) {
                last = error
                if (error.isKey) throw error
            }
        }
        throw last ?: GroqException(503, "no groq model available")
    }

    private suspend fun post(
        model: String,
        apiKey: String,
        messages: List<GroqMessage>,
        tools: List<GroqTool>,
    ): GroqResponse {
        val response = http.post("$BASE/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(
                GroqRequest(
                    model = model,
                    messages = messages,
                    tools = tools.takeIf { it.isNotEmpty() },
                    toolChoice = if (tools.isEmpty()) null else "auto",
                ),
            )
        }
        if (!response.status.isSuccess()) {
            throw GroqException(response.status.value, response.bodyAsText())
        }
        return response.body()
    }

    private companion object {
        const val BASE = "https://api.groq.com/openai/v1"
        val FALLBACKS = listOf(
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "openai/gpt-oss-120b",
        )
    }
}
