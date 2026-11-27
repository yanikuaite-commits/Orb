package dev.orb.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.orb.app.AppContainer
import dev.orb.app.agent.LlmTurn
import dev.orb.app.data.DEFAULT_GEMINI_TEXT_MODEL
import dev.orb.app.data.DEFAULT_GROQ_TEXT_MODEL
import dev.orb.app.data.GeminiException
import dev.orb.app.data.GroqException
import dev.orb.app.data.MemoryEntry
import dev.orb.app.data.ORB_VOICES
import dev.orb.app.data.Reminder
import dev.orb.app.data.ToolTrace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class OrbStatus { Ready, MissingKey, MissingGroqKey }

data class SettingsDraft(
    val assistantName: String,
    val userName: String,
    val language: String,
    val persona: String,
    val ttsEngine: String,
    val voice: String,
    val textProvider: String,
    val textModel: String,
    val geminiKey: String,
    val groqKey: String,
)

data class UiMessage(
    val id: String = UUID.randomUUID().toString(),
    val fromUser: Boolean,
    val text: String,
    val tools: List<ToolTrace> = emptyList(),
    val ts: Long = System.currentTimeMillis(),
)

data class OrbState(
    val ready: Boolean = false,
    val setupRequired: Boolean = false,
    val apiKeySet: Boolean = false,
    val groqKeySet: Boolean = false,
    val textProvider: String = "gemini",
    val textModel: String = DEFAULT_GEMINI_TEXT_MODEL,
    val assistantName: String = "Orb",
    val userName: String = "",
    val language: String = "pt-BR",
    val persona: String = "",
    val ttsEngine: String = "device",
    val voice: String = "Charon",
    val voices: List<String> = ORB_VOICES,
    val messages: List<UiMessage> = emptyList(),
    val input: String = "",
    val busy: Boolean = false,
    val ttsEnabled: Boolean = true,
    val memory: List<MemoryEntry> = emptyList(),
    val memoryLoading: Boolean = false,
    val reminders: List<Reminder> = emptyList(),
    val remindersLoading: Boolean = false,
    val exactAlarms: Boolean = true,
    val snackbar: String? = null,
) {
    val status: OrbStatus get() = when {
        !apiKeySet -> OrbStatus.MissingKey
        textProvider == "groq" && !groqKeySet -> OrbStatus.MissingGroqKey
        else -> OrbStatus.Ready
    }
}

class OrbViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(OrbState())
    val state: StateFlow<OrbState> = _state.asStateFlow()

    private val settingsStore = container.settings
    private val apiKeys = container.apiKeyStore
    private val memory = container.memory
    private val history = container.history
    private val reminderStore = container.reminderStore
    private val scheduler = container.scheduler
    private val agent = container.agent

    init {
        viewModelScope.launch {
            val saved = settingsStore.flow.first()
            settingsStore.current = saved
            val hasGemini = apiKeys.hasGemini()
            val hasGroq = apiKeys.hasGroq()
            val provider = if (saved.textProvider == "groq") "groq" else "gemini"
            val model = when {
                saved.textModel.isBlank() ->
                    if (provider == "groq") DEFAULT_GROQ_TEXT_MODEL else DEFAULT_GEMINI_TEXT_MODEL
                provider == "groq" && saved.textModel.startsWith("gemini") -> DEFAULT_GROQ_TEXT_MODEL
                else -> saved.textModel.trim()
            }
            val messages = withContext(Dispatchers.IO) {
                history.messages(40).map { message ->
                    UiMessage(
                        fromUser = message.role == "user",
                        text = message.text,
                        ts = (message.ts * 1000).toLong(),
                    )
                }
            }
            _state.update {
                it.copy(
                    ready = true,
                    setupRequired = !hasGemini,
                    apiKeySet = hasGemini,
                    groqKeySet = hasGroq,
                    textProvider = provider,
                    textModel = model,
                    assistantName = saved.assistantName,
                    userName = saved.userName,
                    language = saved.language,
                    persona = saved.persona,
                    ttsEngine = saved.ttsEngine,
                    voice = saved.voice,
                    ttsEnabled = saved.ttsEnabled,
                    messages = messages,
                    exactAlarms = scheduler.canScheduleExact(),
                )
            }
            loadRemindersInternal()
        }
    }

    fun activate(geminiKey: String, groqKey: String, userName: String) {
        val geminiTrimmed = geminiKey.trim()
        val groqTrimmed = groqKey.trim()
        if (geminiTrimmed.isBlank()) {
            notify("A chave Gemini é obrigatória para voz e live.")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val geminiResult = withContext(Dispatchers.IO) { container.gemini.validateKey(geminiTrimmed) }
            if (geminiResult.isFailure) {
                val error = geminiResult.exceptionOrNull()
                val message = if (error is GeminiException && error.isKey) {
                    "Chave Gemini recusada. Confira no Google AI Studio."
                } else {
                    "Não consegui validar a chave Gemini agora. Verifique a internet."
                }
                _state.update { it.copy(busy = false, snackbar = message) }
                return@launch
            }
            if (groqTrimmed.isNotBlank()) {
                val groqResult = withContext(Dispatchers.IO) { container.groq.validateKey(groqTrimmed) }
                if (groqResult.isFailure) {
                    val error = groqResult.exceptionOrNull()
                    val message = if (error is GroqException && error.isKey) {
                        "Chave Groq recusada. Confira em console.groq.com/keys."
                    } else {
                        "Não consegui validar a chave Groq agora. Verifique a internet."
                    }
                    _state.update { it.copy(busy = false, snackbar = message) }
                    return@launch
                }
                apiKeys.setGroq(groqTrimmed)
            }
            apiKeys.setGemini(geminiTrimmed)
            val saved = settingsStore.current.copy(
                userName = userName.trim().ifBlank { settingsStore.current.userName },
            )
            settingsStore.save(saved)
            _state.update {
                it.copy(
                    busy = false,
                    apiKeySet = true,
                    groqKeySet = apiKeys.hasGroq(),
                    setupRequired = false,
                    userName = saved.userName,
                    snackbar = null,
                )
            }
            loadRemindersInternal()
        }
    }

    fun send(text: String = _state.value.input) {
        val message = text.trim()
        if (message.isBlank() || _state.value.busy) return
        if (!_state.value.apiKeySet) {
            notify("Ative o Orb com a chave Gemini em Ajustes.")
            return
        }
        val provider = _state.value.textProvider
        if (provider == "groq" && !_state.value.groqKeySet) {
            notify("Adicione a chave Groq em Ajustes para usar modelos Groq.")
            return
        }
        _state.update {
            it.copy(
                input = "",
                busy = true,
                messages = it.messages + UiMessage(fromUser = true, text = message),
            )
        }
        viewModelScope.launch {
            try {
                val client = if (provider == "groq") container.groqText else container.geminiText
                val result = withContext(Dispatchers.IO) {
                    agent.chat(message, client, _state.value.textModel)
                }
                _state.update {
                    it.copy(
                        messages = it.messages + UiMessage(
                            fromUser = false,
                            text = result.reply,
                            tools = result.tools,
                        ),
                        busy = false,
                    )
                }
                if (result.ok) speak(result.reply) else notify(result.reply)
                loadRemindersInternal()
                loadMemoryInternal()
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        busy = false,
                        messages = it.messages + UiMessage(
                            fromUser = false,
                            text = "Não consegui falar com o modelo agora. Verifique a internet e tente de novo.",
                        ),
                        snackbar = error.message ?: "Falha de conexão.",
                    )
                }
            }
        }
    }

    fun setInput(value: String) {
        _state.update { it.copy(input = value) }
    }

    fun toggleTts() {
        val enabled = !_state.value.ttsEnabled
        _state.update { it.copy(ttsEnabled = enabled) }
        if (!enabled) {
            container.tts.stop()
            container.wavPlayer.stop()
        }
        viewModelScope.launch {
            settingsStore.save(settingsStore.current.copy(ttsEnabled = enabled))
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { history.clear() }
            _state.update { it.copy(messages = emptyList(), snackbar = "Conversa apagada.") }
        }
    }

    fun loadMemory() {
        viewModelScope.launch {
            _state.update { it.copy(memoryLoading = true) }
            val entries = withContext(Dispatchers.IO) {
                memory.all().sortedByDescending { it.updatedAt }
            }
            _state.update { it.copy(memory = entries, memoryLoading = false) }
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { memory.delete(id) }
            _state.update { it.copy(memory = it.memory.filterNot { entry -> entry.id == id }) }
        }
    }

    fun clearMemory() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { memory.clear() }
            _state.update { it.copy(memory = emptyList(), snackbar = "Memória apagada.") }
        }
    }

    fun loadReminders() {
        viewModelScope.launch {
            _state.update { it.copy(remindersLoading = true) }
            loadRemindersInternal()
            _state.update { it.copy(remindersLoading = false) }
        }
    }

    private suspend fun loadRemindersInternal() {
        val items = withContext(Dispatchers.IO) {
            reminderStore.all().filter { !it.fired }.sortedBy { it.dueAt }
        }
        scheduler.cancelAll(items)
        scheduler.scheduleAll(items)
        _state.update { it.copy(reminders = items, exactAlarms = scheduler.canScheduleExact()) }
    }

    private suspend fun loadMemoryInternal() {
        val entries = withContext(Dispatchers.IO) {
            memory.all().sortedByDescending { it.updatedAt }
        }
        _state.update { it.copy(memory = entries) }
    }

    fun saveSettings(draft: SettingsDraft) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val provider = if (draft.textProvider == "groq") "groq" else "gemini"
            val geminiKey = draft.geminiKey.trim()
            val groqKey = draft.groqKey.trim()

            if (geminiKey.isNotBlank()) {
                val result = withContext(Dispatchers.IO) { container.gemini.validateKey(geminiKey) }
                if (result.isFailure) {
                    val error = result.exceptionOrNull()
                    val message = if (error is GeminiException && error.isKey) {
                        "Chave Gemini recusada. Confira no Google AI Studio."
                    } else {
                        "Não consegui validar a chave Gemini agora. Verifique a internet."
                    }
                    _state.update { it.copy(busy = false, snackbar = message) }
                    return@launch
                }
                apiKeys.setGemini(geminiKey)
            } else if (!apiKeys.hasGemini()) {
                _state.update {
                    it.copy(busy = false, snackbar = "A chave Gemini é obrigatória para voz e live.")
                }
                return@launch
            }

            if (groqKey.isNotBlank()) {
                val result = withContext(Dispatchers.IO) { container.groq.validateKey(groqKey) }
                if (result.isFailure) {
                    val error = result.exceptionOrNull()
                    val message = if (error is GroqException && error.isKey) {
                        "Chave Groq recusada. Confira em console.groq.com/keys."
                    } else {
                        "Não consegui validar a chave Groq agora. Verifique a internet."
                    }
                    _state.update { it.copy(busy = false, snackbar = message) }
                    return@launch
                }
                apiKeys.setGroq(groqKey)
            }
            if (provider == "groq" && !apiKeys.hasGroq()) {
                _state.update {
                    it.copy(busy = false, snackbar = "Adicione a chave Groq para usar modelos Groq.")
                }
                return@launch
            }

            val defaultModel = if (provider == "groq") DEFAULT_GROQ_TEXT_MODEL else DEFAULT_GEMINI_TEXT_MODEL
            val saved = settingsStore.current.copy(
                assistantName = draft.assistantName.trim().ifBlank { "Orb" },
                userName = draft.userName.trim(),
                language = draft.language.trim().ifBlank { "pt-BR" },
                persona = draft.persona.trim(),
                ttsEngine = if (draft.ttsEngine == "gemini") "gemini" else "device",
                voice = draft.voice,
                textProvider = provider,
                textModel = draft.textModel.trim().ifBlank { defaultModel },
            )
            settingsStore.save(saved)
            _state.update {
                it.copy(
                    assistantName = saved.assistantName,
                    userName = saved.userName,
                    language = saved.language,
                    persona = saved.persona,
                    ttsEngine = saved.ttsEngine,
                    voice = saved.voice,
                    textProvider = saved.textProvider,
                    textModel = saved.textModel,
                    apiKeySet = apiKeys.hasGemini(),
                    groqKeySet = apiKeys.hasGroq(),
                    setupRequired = !apiKeys.hasGemini(),
                    busy = false,
                    snackbar = "Ajustes salvos.",
                )
            }
        }
    }

    fun testModel() {
        val state = _state.value
        if (!state.apiKeySet) {
            notify("Salve a chave Gemini primeiro.")
            return
        }
        if (state.textProvider == "groq" && !state.groqKeySet) {
            notify("Salve a chave Groq primeiro.")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val client = if (state.textProvider == "groq") container.groqText else container.geminiText
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    client.conversation(
                        system = "Responda apenas OK.",
                        history = emptyList(),
                        tools = emptyList(),
                        model = state.textModel,
                    ).send("Teste de conexão. Responda apenas OK.")
                }
            }
            val message = result.fold(
                onSuccess = { turn ->
                    when (turn) {
                        is LlmTurn.Reply -> "Modelo OK: ${turn.text.take(80)}"
                        is LlmTurn.Tools -> "Modelo OK (chamou ferramenta)."
                    }
                },
                onFailure = { error -> "Falha no modelo: ${failureDetail(error)}" },
            )
            _state.update { it.copy(busy = false, snackbar = message) }
        }
    }

    private fun failureDetail(error: Throwable): String = when (error) {
        is GeminiException -> when (error.code) {
            404 -> "Gemini HTTP 404, modelo não encontrado"
            429 -> "Gemini HTTP 429, cota"
            else -> "Gemini HTTP ${error.code}"
        }
        is GroqException -> when (error.code) {
            404 -> "Groq HTTP 404, modelo não encontrado"
            429 -> "Groq HTTP 429, cota"
            else -> "Groq HTTP ${error.code}"
        }
        else -> error.message?.take(140) ?: error.javaClass.simpleName
    }

    fun removeGeminiKey() {
        apiKeys.clearGemini()
        _state.update { it.copy(apiKeySet = false, setupRequired = true, snackbar = null) }
    }

    fun removeGroqKey() {
        apiKeys.clearGroq()
        _state.update { it.copy(groqKeySet = false, snackbar = "Chave Groq removida.") }
    }

    private fun speak(text: String) {
        if (!_state.value.ttsEnabled || text.isBlank()) return
        if (_state.value.ttsEngine == "gemini") {
            viewModelScope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    runCatching { container.gemini.speak(text, _state.value.voice) }.getOrNull()
                }
                if (bytes != null) {
                    withContext(Dispatchers.Main) { container.wavPlayer.play(bytes) }
                } else {
                    container.tts.speak(text, _state.value.language)
                }
            }
        } else {
            container.tts.speak(text, _state.value.language)
        }
    }

    fun notify(message: String) {
        _state.update { it.copy(snackbar = message) }
    }

    fun dismissSnackbar() {
        _state.update { it.copy(snackbar = null) }
    }
}

class OrbViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = OrbViewModel(container) as T
}
