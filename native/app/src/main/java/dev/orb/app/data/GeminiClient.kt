package dev.orb.app.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

@Serializable
data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart> = emptyList(),
)

@Serializable
data class GeminiPart(
    val text: String? = null,
    val functionCall: GeminiFunctionCall? = null,
    val functionResponse: GeminiFunctionResponse? = null,
    val inlineData: GeminiInlineData? = null,
    val thought: Boolean? = null,
    val thoughtSignature: String? = null,
)

@Serializable
data class GeminiFunctionCall(
    val id: String? = null,
    val name: String = "",
    val args: JsonObject? = null,
)

@Serializable
data class GeminiFunctionResponse(
    val id: String? = null,
    val name: String = "",
    val response: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class GeminiInlineData(
    val mimeType: String? = null,
    val data: String? = null,
)

@Serializable
data class GeminiFunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: JsonObject? = null,
)

@Serializable
data class GeminiTool(
    val functionDeclarations: List<GeminiFunctionDeclaration>? = null,
    val googleSearch: JsonObject? = null,
)

@Serializable
data class GeminiGenerationConfig(
    val temperature: Double? = null,
    val responseModalities: List<String>? = null,
    val speechConfig: GeminiSpeechConfig? = null,
)

@Serializable
data class GeminiSpeechConfig(val voiceConfig: GeminiVoiceConfig)

@Serializable
data class GeminiVoiceConfig(val prebuiltVoiceConfig: GeminiPrebuiltVoiceConfig)

@Serializable
data class GeminiPrebuiltVoiceConfig(val voiceName: String)

@Serializable
data class GeminiToolConfig(val functionCallingConfig: GeminiFunctionCallingConfig)

@Serializable
data class GeminiFunctionCallingConfig(val mode: String = "AUTO")

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent> = emptyList(),
    val tools: List<GeminiTool>? = null,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null,
    val toolConfig: GeminiToolConfig? = null,
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
    val promptFeedback: GeminiPromptFeedback? = null,
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null,
    val groundingMetadata: GeminiGroundingMetadata? = null,
)

@Serializable
data class GeminiPromptFeedback(val blockReason: String? = null)

@Serializable
data class GeminiGroundingMetadata(
    val groundingChunks: List<GeminiGroundingChunk> = emptyList(),
)

@Serializable
data class GeminiGroundingChunk(val web: GeminiWeb? = null)

@Serializable
data class GeminiWeb(val uri: String? = null, val title: String? = null)

fun GeminiResponse.text(): String = candidates.firstOrNull()?.content?.parts
    ?.filter { it.thought != true }
    ?.mapNotNull { it.text }
    ?.joinToString("")
    ?.trim()
    .orEmpty()

fun GeminiResponse.functionCalls(): List<GeminiFunctionCall> =
    candidates.firstOrNull()?.content?.parts?.mapNotNull { it.functionCall }.orEmpty()

enum class Tier { Fast, Smart }

data class SearchResult(val text: String, val sources: List<String>)

class GeminiException(val code: Int, body: String) : Exception("Gemini HTTP $code: ${body.take(400)}") {
    val isQuota: Boolean = code == 429 ||
        body.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
        body.contains("quota", ignoreCase = true) ||
        body.contains("rate limit", ignoreCase = true)

    val isKey: Boolean = code == 401 || code == 403 ||
        body.contains("API key not valid", ignoreCase = true) ||
        body.contains("API_KEY_INVALID", ignoreCase = true) ||
        body.contains("unauthenticated", ignoreCase = true) ||
        body.contains("permission denied", ignoreCase = true)
}

class GeminiClient(
    private val http: HttpClient,
    private val keys: ApiKeyStore,
) {
    fun hasKey(): Boolean = keys.hasGemini()

    fun key(): String = keys.gemini()

    suspend fun validateKey(key: String): Result<Unit> {
        val trimmed = key.trim()
        if (trimmed.isBlank()) return Result.failure(IllegalArgumentException("empty key"))
        return try {
            val response = http.get("$BASE/models") {
                header("x-goog-api-key", trimmed)
            }
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(GeminiException(response.status.value, response.bodyAsText()))
            }
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun generate(tier: Tier, request: GeminiRequest): GeminiResponse = generate(ladder(tier), request)

    suspend fun generate(model: String, request: GeminiRequest): GeminiResponse {
        val requested = model.trim().removePrefix("models/")
        val preferred = if (requested.isBlank()) emptyList() else listOf(requested)
        return generate((preferred + ladder(Tier.Smart)).distinct(), request)
    }

    private suspend fun generate(models: List<String>, request: GeminiRequest): GeminiResponse {
        val apiKey = key()
        if (apiKey.isBlank()) throw GeminiException(401, "missing api key")
        var last: GeminiException? = null
        for (model in models) {
            if (isCooling(model)) continue
            try {
                return generate(model, apiKey, request)
            } catch (error: GeminiException) {
                last = error
                if (error.isQuota) {
                    cool(model)
                    continue
                }
                if (error.isKey) throw error
            }
        }
        throw last ?: GeminiException(503, "no model available")
    }

    private suspend fun generate(model: String, apiKey: String, request: GeminiRequest): GeminiResponse {
        val response = http.post("$BASE/${model.removePrefix("models/")}:generateContent") {
            header("x-goog-api-key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) {
            throw GeminiException(response.status.value, response.bodyAsText())
        }
        return response.body()
    }

    suspend fun speak(text: String, voice: String): ByteArray? {
        val clean = text.trim()
        if (clean.isBlank() || !hasKey()) return null
        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    role = "user",
                    parts = listOf(
                        GeminiPart(text = "Say this naturally, in the language it is written in:\n${clean.take(1500)}"),
                    ),
                ),
            ),
            generationConfig = GeminiGenerationConfig(
                responseModalities = listOf("AUDIO"),
                speechConfig = GeminiSpeechConfig(
                    voiceConfig = GeminiVoiceConfig(
                        prebuiltVoiceConfig = GeminiPrebuiltVoiceConfig(
                            voiceName = voice.ifBlank { "Charon" },
                        ),
                    ),
                ),
            ),
        )
        return try {
            var generated: GeminiResponse? = null
            for (model in TTS_MODELS) {
                generated = runCatching { generate(model, key(), request) }.getOrNull()
                if (generated != null) break
            }
            val response = generated ?: return null
            val inline = response.candidates.firstOrNull()?.content?.parts
                ?.firstOrNull { it.inlineData?.data != null }
                ?.inlineData
                ?: return null
            val data = inline.data ?: return null
            val pcm = Base64.getDecoder().decode(data)
            val rate = Regex("rate=(\\d+)").find(inline.mimeType.orEmpty())
                ?.groupValues?.get(1)?.toIntOrNull() ?: 24_000
            Pcm.toWav(pcm, rate)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun groundedSearch(query: String, deep: Boolean): SearchResult {
        val instruction = if (deep) {
            "Research this thoroughly and answer with concrete facts, dates and figures. "
        } else {
            "Answer this using current web results, briefly and concretely. "
        }
        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart(text = instruction + "Question: " + query)),
                ),
            ),
            tools = listOf(GeminiTool(googleSearch = JsonObject(emptyMap()))),
            generationConfig = GeminiGenerationConfig(temperature = 0.2),
        )
        val response = generate(if (deep) Tier.Smart else Tier.Fast, request)
        val sources = response.candidates.firstOrNull()?.groundingMetadata?.groundingChunks
            ?.mapNotNull { it.web?.uri }
            ?.distinct()
            ?.take(6)
            .orEmpty()
        return SearchResult(response.text(), sources)
    }

    private fun ladder(tier: Tier): List<String> = when (tier) {
        Tier.Fast -> FAST_LADDER
        Tier.Smart -> SMART_LADDER
    }

    private fun isCooling(model: String): Boolean = synchronized(cooldowns) {
        (cooldowns[model] ?: 0L) > System.currentTimeMillis()
    }

    private fun cool(model: String) = synchronized(cooldowns) {
        cooldowns[model] = System.currentTimeMillis() + COOLDOWN_MS
    }

    private companion object {
        const val BASE = "https://generativelanguage.googleapis.com/v1beta"
        val TTS_MODELS = listOf(
            "gemini-3.8-flash-lite-tts",
            "gemini-3.8-flash-tts",
            "gemini-2.5-flash-preview-tts",
        )
        const val COOLDOWN_MS = 300_000L
        val FAST_LADDER = listOf(
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite",
            "gemini-3.6-flash",
            "gemini-flash-latest",
        )
        val SMART_LADDER = listOf(
            "gemini-3.6-flash",
            "gemini-3.5-flash",
            "gemini-3.5-flash-lite",
            "gemini-3.8-flash",
            "gemini-3.1-flash-lite",
            "gemini-flash-latest",
        )
        val cooldowns = mutableMapOf<String, Long>()
    }
}

object Pcm {
    fun toWav(pcm: ByteArray, sampleRate: Int, channels: Int = 1, bitsPerSample: Int = 16): ByteArray {
        val dataSize = pcm.size
        val blockAlign = channels * bitsPerSample / 8
        val byteRate = sampleRate * blockAlign
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1.toShort())
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize)
        val out = ByteArray(44 + dataSize)
        System.arraycopy(header.array(), 0, out, 0, 44)
        System.arraycopy(pcm, 0, out, 44, dataSize)
        return out
    }
}
