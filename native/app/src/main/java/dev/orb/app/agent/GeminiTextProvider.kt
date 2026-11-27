package dev.orb.app.agent

import dev.orb.app.data.DEFAULT_GEMINI_TEXT_MODEL
import dev.orb.app.data.GEMINI_TEXT_MODELS
import dev.orb.app.data.GeminiClient
import dev.orb.app.data.GeminiContent
import dev.orb.app.data.GeminiFunctionCallingConfig
import dev.orb.app.data.GeminiFunctionDeclaration
import dev.orb.app.data.GeminiFunctionResponse
import dev.orb.app.data.GeminiGenerationConfig
import dev.orb.app.data.GeminiPart
import dev.orb.app.data.GeminiRequest
import dev.orb.app.data.GeminiTool
import dev.orb.app.data.GeminiToolConfig
import dev.orb.app.data.functionCalls
import dev.orb.app.data.text
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class GeminiTextProvider(private val gemini: GeminiClient) : TextModelClient {
    override val id = "gemini"
    override val label = "Gemini"
    override val models = GEMINI_TEXT_MODELS
    override val defaultModel = DEFAULT_GEMINI_TEXT_MODEL

    override fun hasKey(): Boolean = gemini.hasKey()

    override suspend fun validateKey(key: String): Result<Unit> = gemini.validateKey(key)

    override fun conversation(
        system: String,
        history: List<Pair<String, String>>,
        tools: List<ToolSpec>,
        model: String,
    ): TextConversation = GeminiConversation(gemini, system, history, tools, model)
}

private class GeminiConversation(
    private val gemini: GeminiClient,
    private val system: String,
    history: List<Pair<String, String>>,
    tools: List<ToolSpec>,
    private val model: String,
) : TextConversation {
    private val contents = mutableListOf<GeminiContent>()
    private val declarations = tools.map {
        GeminiFunctionDeclaration(name = it.name, description = it.description, parameters = it.parameters)
    }

    init {
        history.forEach { (role, text) ->
            if (text.isNotBlank()) {
                contents += GeminiContent(
                    role = if (role == "assistant") "model" else "user",
                    parts = listOf(GeminiPart(text = text)),
                )
            }
        }
    }

    override suspend fun send(text: String): LlmTurn {
        contents += GeminiContent(role = "user", parts = listOf(GeminiPart(text = text)))
        return call()
    }

    override suspend fun sendToolResults(results: List<LlmToolResult>): LlmTurn {
        val parts = results.map { result ->
            GeminiPart(
                functionResponse = GeminiFunctionResponse(
                    id = result.id,
                    name = result.name,
                    response = buildJsonObject { put("result", result.result) },
                ),
            )
        }
        contents += GeminiContent(role = "user", parts = parts)
        return call()
    }

    private suspend fun call(): LlmTurn {
        val request = GeminiRequest(
            contents = contents.toList(),
            tools = declarations.takeIf { it.isNotEmpty() }?.let { listOf(GeminiTool(functionDeclarations = it)) },
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = system))),
            generationConfig = GeminiGenerationConfig(temperature = 0.7),
            toolConfig = GeminiToolConfig(GeminiFunctionCallingConfig("AUTO")),
        )
        val response = gemini.generate(model, request)
        val calls = response.functionCalls()
        if (calls.isNotEmpty()) {
            response.candidates.firstOrNull()?.content?.let { contents += it }
            return LlmTurn.Tools(
                calls.map {
                    LlmToolCall(
                        id = it.id,
                        name = it.name,
                        arguments = it.args ?: JsonObject(emptyMap()),
                    )
                },
            )
        }
        return LlmTurn.Reply(response.text().ifBlank { "Não consegui produzir uma resposta desta vez." })
    }
}
