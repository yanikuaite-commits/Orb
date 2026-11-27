package dev.orb.app.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class OrbApiException(val status: Int, message: String) : Exception(message)

class OrbApi(private val settings: SettingsStore) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    private val client = HttpClient(OkHttp) {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 120_000
            socketTimeoutMillis = 120_000
        }
    }

    private fun base(): String = settings.current.serverUrl.trimEnd('/')

    private fun HttpRequestBuilder.auth() {
        settings.current.token.takeIf { it.isNotBlank() }?.let {
            header(HttpHeaders.Authorization, "Bearer $it")
        }
    }

    private suspend inline fun <reified T> HttpResponse.decode(): T {
        if (status.isSuccess()) return body()
        throw OrbApiException(status.value, errorMessage())
    }

    private suspend fun HttpResponse.errorMessage(): String {
        val text = runCatching { bodyAsText() }.getOrDefault("")
        val detail = runCatching {
            json.parseToJsonElement(text).jsonObject["detail"]?.jsonPrimitive?.content
        }.getOrNull()
        return when (status.value) {
            401 -> "Token inválido. Confira em Ajustes."
            else -> detail ?: "Erro ${status.value}."
        }
    }

    suspend fun health(baseUrl: String, token: String = ""): Health {
        val response = client.get("${baseUrl.trimEnd('/')}/api/health") {
            if (token.isNotBlank()) header(HttpHeaders.Authorization, "Bearer $token")
        }
        return response.decode()
    }

    suspend fun config(): OrbConfig =
        client.get("${base()}/api/config") { auth() }.decode()

    suspend fun saveConfig(update: ConfigUpdate): OrbConfig =
        client.put("${base()}/api/config") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(update)
        }.decode()

    suspend fun chat(message: String): ChatResponse =
        client.post("${base()}/api/chat") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(ChatRequest(message))
        }.decode()

    suspend fun history(limit: Int = 40): HistoryList =
        client.get("${base()}/api/history") {
            auth()
            parameter("limit", limit)
        }.decode()

    suspend fun clearHistory() {
        client.delete("${base()}/api/history") { auth() }.decode<Map<String, Boolean>>()
    }

    suspend fun memory(): MemoryList =
        client.get("${base()}/api/memory") { auth() }.decode()

    suspend fun addMemory(key: String, value: String, category: String = "notes"): MemoryEntry? =
        client.post("${base()}/api/memory") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(MemoryRequest(key, value, category))
        }.decode<MemoryCreated>().entry

    suspend fun deleteMemory(id: String) {
        client.delete("${base()}/api/memory/$id") { auth() }.decode<Map<String, String>>()
    }

    suspend fun clearMemory() {
        client.delete("${base()}/api/memory") { auth() }.decode<Map<String, Boolean>>()
    }

    suspend fun reminders(): ReminderList =
        client.get("${base()}/api/reminders") { auth() }.decode()

    suspend fun tts(text: String, voice: String): ByteArray {
        val response = client.post("${base()}/api/tts") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(TtsRequest(text, voice))
        }
        if (!response.status.isSuccess()) {
            throw OrbApiException(response.status.value, response.errorMessage())
        }
        return response.body()
    }
}
