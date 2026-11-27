package dev.orb.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.orb.app.AppContainer
import dev.orb.app.data.GeminiException
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

enum class OrbStatus { Ready, MissingKey }

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
    val status: OrbStatus get() = if (apiKeySet) OrbStatus.Ready else OrbStatus.MissingKey
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
            val hasKey = apiKeys.hasKey()
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
                    setupRequired = !hasKey,
                    apiKeySet = hasKey,
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

    fun activate(key: String, userName: String) {
        val trimmed = key.trim()
        if (trimmed.isBlank()) {
            notify("Cole sua chave Gemini para ativar o Orb.")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val result = withContext(Dispatchers.IO) { container.gemini.validateKey(trimmed) }
            if (result.isSuccess) {
                apiKeys.set(trimmed)
                val saved = settingsStore.current.copy(
                    userName = userName.trim().ifBlank { settingsStore.current.userName },
                )
                settingsStore.save(saved)
                _state.update {
                    it.copy(
                        busy = false,
                        apiKeySet = true,
                        setupRequired = false,
                        userName = saved.userName,
                        snackbar = null,
                    )
                }
                loadRemindersInternal()
            } else {
                val error = result.exceptionOrNull()
                val message = if (error is GeminiException && error.isKey) {
                    "Chave recusada. Confira se copiou a chave do Google AI Studio."
                } else {
                    "Não consegui validar a chave agora. Verifique a internet e tente de novo."
                }
                _state.update { it.copy(busy = false, snackbar = message) }
            }
        }
    }

    fun send(text: String = _state.value.input) {
        val message = text.trim()
        if (message.isBlank() || _state.value.busy) return
        if (!_state.value.apiKeySet) {
            notify("Ative o Orb com sua chave Gemini em Ajustes.")
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
                val result = withContext(Dispatchers.IO) { agent.chat(message) }
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
        val items = withContext(Dispatchers.IO) { reminderStore.all().sortedBy { it.dueAt } }
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

    fun saveSettings(
        assistantName: String,
        userName: String,
        language: String,
        persona: String,
        ttsEngine: String,
        voice: String,
        apiKey: String,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val trimmedKey = apiKey.trim()
            if (trimmedKey.isNotBlank()) {
                val result = withContext(Dispatchers.IO) { container.gemini.validateKey(trimmedKey) }
                if (result.isFailure) {
                    val error = result.exceptionOrNull()
                    val message = if (error is GeminiException && error.isKey) {
                        "Chave recusada. Confira a chave do Google AI Studio."
                    } else {
                        "Não consegui validar a chave agora. Verifique a internet."
                    }
                    _state.update { it.copy(busy = false, snackbar = message) }
                    return@launch
                }
                apiKeys.set(trimmedKey)
            }
            val saved = settingsStore.current.copy(
                assistantName = assistantName.trim().ifBlank { "Orb" },
                userName = userName.trim(),
                language = language.trim().ifBlank { "pt-BR" },
                persona = persona.trim(),
                ttsEngine = if (ttsEngine == "gemini") "gemini" else "device",
                voice = voice,
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
                    apiKeySet = apiKeys.hasKey(),
                    setupRequired = !apiKeys.hasKey(),
                    busy = false,
                    snackbar = "Ajustes salvos.",
                )
            }
        }
    }

    fun removeApiKey() {
        apiKeys.clear()
        _state.update { it.copy(apiKeySet = false, setupRequired = true, snackbar = null) }
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
