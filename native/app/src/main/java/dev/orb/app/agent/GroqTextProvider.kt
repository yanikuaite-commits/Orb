package dev.orb.app.agent

import dev.orb.app.data.DEFAULT_GROQ_TEXT_MODEL
import dev.orb.app.data.GROQ_TEXT_MODELS
import dev.orb.app.data.GroqClient
import dev.orb.app.data.GroqFunctionDef
import dev.orb.app.data.GroqMessage
import dev.orb.app.data.GroqTool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

class GroqTextProvider(
    private val groq: GroqClient,
    private val json: Json,
) : TextModelClient {
    override val id = "groq"
    override val label = "Groq"
    override val models = GROQ_TEXT_MODELS
    override val defaultModel = DEFAULT_GROQ_TEXT_MODEL

    override fun hasKey(): Boolean = groq.hasKey()

    override suspend fun validateKey(key: String): Result<Unit> = groq.validateKey(key)

    override fun conversation(
        system: String,
        history: List<Pair<String, String>>,
        tools: List<ToolSpec>,
        model: String,
    ): TextConversation = GroqConversation(groq, json, system, history, tools, model)
}

private class GroqConversation(
    private val groq: GroqClient,
    private val json: Json,
    system: String,
    history: List<Pair<String, String>>,
    tools: List<ToolSpec>,
    private val model: String,
) : TextConversation {
    private val messages = mutableListOf<GroqMessage>()
    private val toolSpecs = tools.map {
        GroqTool(
            function = GroqFunctionDef(
                name = it.name,
                description = it.description,
                parameters = normalizeSchema(it.parameters).jsonObject,
            ),
        )
    }

    init {
        messages += GroqMessage(role = "system", content = system)
        history.forEach { (role, text) ->
            if (text.isNotBlank()) {
                messages += GroqMessage(
                    role = if (role == "assistant") "assistant" else "user",
                    content = text,
                )
            }
        }
    }

    override suspend fun send(text: String): LlmTurn {
        messages += GroqMessage(role = "user", content = text)
        return call()
    }

    override suspend fun sendToolResults(results: List<LlmToolResult>): LlmTurn {
        results.forEach { result ->
            messages += GroqMessage(role = "tool", toolCallId = result.id ?: "", content = result.result)
        }
        return call()
    }

    private suspend fun call(): LlmTurn {
        val response = groq.chat(model, messages.toList(), toolSpecs)
        val message = response.choices.firstOrNull()?.message
            ?: return LlmTurn.Reply("Não consegui produzir uma resposta desta vez.")
        messages += message
        val calls = message.toolCalls.orEmpty()
        if (calls.isEmpty()) {
            return LlmTurn.Reply(message.content.orEmpty().ifBlank { "Não consegui produzir uma resposta desta vez." })
        }
        return LlmTurn.Tools(
            calls.mapIndexed { index, call ->
                val arguments = runCatching {
                    json.parseToJsonElement(call.function.arguments.ifBlank { "{}" }).jsonObject
                }.getOrElse { JsonObject(emptyMap()) }
                LlmToolCall(
                    id = call.id.ifBlank { "call_${System.currentTimeMillis()}_$index" },
                    name = call.function.name,
                    arguments = arguments,
                )
            },
        )
    }
}

private fun normalizeSchema(element: JsonElement): JsonElement = when (element) {
    is JsonObject -> JsonObject(
        element.mapValues { (key, value) ->
            if (key == "type" && value is JsonPrimitive && value.isString) {
                JsonPrimitive(value.content.lowercase())
            } else {
                normalizeSchema(value)
            }
        },
    )
    is JsonArray -> JsonArray(element.map { normalizeSchema(it) })
    else -> element
}
