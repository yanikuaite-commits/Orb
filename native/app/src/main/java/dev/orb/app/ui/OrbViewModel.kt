package dev.orb.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.orb.app.AppContainer
import dev.orb.app.data.ConfigUpdate
import dev.orb.app.data.HistoryMessage
import dev.orb.app.data.MemoryEntry
import dev.orb.app.data.OrbConfig
import dev.orb.app.data.Reminder
import dev.orb.app.data.ToolTrace
import dev.orb.app.data.normalizeServerUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class OrbStatus { Connecting, Online, Offline, MissingKey }

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
    val serverUrl: String = "",
    val token: String = "",
    val status: OrbStatus = OrbStatus.Connecting,
    val assistantName: String = "Orb",
    val userName: String = "",
    val language: String = "pt-BR",
    val persona: String = "",
    val ttsEngine: String = "device",
    val voice: String = "Charon",
    val voices: List<String> = emptyList(),
    val apiKeySet: Boolean = false,
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
)

class OrbViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(OrbState())
    val state: StateFlow<OrbState> = _state.asStateFlow()

    private val settingsStore = container.settings
    private val api = container.api
    private val reminderStore = container.reminderStore
    private val scheduler = container.scheduler

    init {
        viewModelScope.launch {
            val saved = settingsStore.flow.first()
            settingsStore.current = saved
            _state.update {
                it.copy(
                    serverUrl = saved.serverUrl,
                    token = saved.token,
                    ttsEnabled = saved.ttsEnabled,
                    ttsEngine = saved.ttsEngine,
                    voice = saved.voice,
                    language = saved.language,
                    assistantName = saved.assistantName,
                    setupRequired = saved.serverUrl.isBlank(),
                    exactAlarms = scheduler.canScheduleExact(),
                    ready = true,
                )
            }
            if (saved.serverUrl.isNotBlank()) refreshAll(initial = true)
        }
        viewModelScope.launch {
            settingsStore.flow.collect { saved ->
                _state.update { current ->
                    if (current.serverUrl != saved.serverUrl ||
                        current.token != saved.token ||
                        current.ttsEnabled != saved.ttsEnabled
                    ) {
                        current.copy(
                            serverUrl = saved.serverUrl,
                            token = saved.token,
                            ttsEnabled = saved.ttsEnabled,
                            setupRequired = saved.serverUrl.isBlank(),
                        )
                    } else {
                        current
                    }
                }
            }
        }
    }

    fun connect(rawUrl: String, token: String) {
        val url = normalizeServerUrl(rawUrl)
        if (url.isBlank()) {
            notify("Informe o endereço do servidor.")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, status = OrbStatus.Connecting) }
            try {
                val health = api.health(url, token)
                settingsStore.save(
                    settingsStore.current.copy(
                        serverUrl = url,
                        token = token,
                        assistantName = health.name.ifBlank { "Orb" },
                    ),
                )
                _state.update {
                    it.copy(
                        serverUrl = url,
                        token = token,
                        setupRequired = false,
                        assistantName = health.name.ifBlank { "Orb" },
                        apiKeySet = health.apiKeySet,
                        status = if (health.apiKeySet) OrbStatus.Online else OrbStatus.MissingKey,
                        busy = false,
                        snackbar = null,
                    )
                }
                refreshAll(initial = true)
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        busy = false,
                        status = OrbStatus.Offline,
                        snackbar = error.message ?: "Não consegui conectar ao servidor.",
                    )
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { refreshAll(initial = false) }
    }

    private suspend fun refreshAll(initial: Boolean) {
        if (settingsStore.current.serverUrl.isBlank()) return
        _state.update { it.copy(status = OrbStatus.Connecting) }
        try {
            val health = api.health(settingsStore.current.serverUrl, settingsStore.current.token)
            _state.update {
                it.copy(
                    status = if (health.apiKeySet) OrbStatus.Online else OrbStatus.MissingKey,
                    assistantName = health.name.ifBlank { it.assistantName },
                    apiKeySet = health.apiKeySet,
                )
            }
            val config: OrbConfig = api.config()
            val engine = if (config.ttsEngine == "gemini") "gemini" else "device"
            settingsStore.save(
                settingsStore.current.copy(
                    assistantName = config.assistantName,
                    language = config.language,
                    voice = config.voice,
                    ttsEngine = engine,
                ),
            )
            _state.update {
                it.copy(
                    assistantName = config.assistantName,
                    userName = config.userName,
                    language = config.language,
                    persona = config.persona,
                    voice = config.voice,
                    ttsEngine = engine,
                    voices = config.voices,
                    apiKeySet = config.apiKeySet,
                    status = if (config.apiKeySet) OrbStatus.Online else OrbStatus.MissingKey,
                    snackbar = null,
                )
            }
            if (initial) {
                val history = api.history(40)
                _state.update {
                    it.copy(messages = history.messages.map { message: HistoryMessage ->
                        UiMessage(
                            fromUser = message.role == "user",
                            text = message.text,
                            ts = (message.ts * 1000).toLong(),
                        )
                    })
                }
            }
            refreshRemindersInternal()
        } catch (error: Exception) {
            _state.update {
                it.copy(
                    status = OrbStatus.Offline,
                    snackbar = error.message ?: "Sem conexão com o servidor.",
                )
            }
        }
    }

    fun send(text: String = _state.value.input) {
        val message = text.trim()
        if (message.isBlank() || _state.value.busy) return
        if (!_state.value.apiKeySet) {
            notify("Configure a chave Gemini em Ajustes.")
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
                val result = api.chat(message)
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
                runCatching { refreshRemindersInternal() }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        busy = false,
                        messages = it.messages + UiMessage(
                            fromUser = false,
                            text = "Não consegui alcançar o servidor do Orb. Verifique o endereço em Ajustes.",
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
            try {
                api.clearHistory()
                _state.update { it.copy(messages = emptyList(), snackbar = "Conversa apagada.") }
            } catch (error: Exception) {
                _state.update { it.copy(snackbar = error.message ?: "Não consegui apagar a conversa.") }
            }
        }
    }

    fun loadMemory() {
        if (_state.value.memoryLoading) return
        viewModelScope.launch {
            _state.update { it.copy(memoryLoading = true) }
            try {
                val entries = api.memory().entries.sortedByDescending { it.updatedAt }
                _state.update { it.copy(memory = entries, memoryLoading = false) }
            } catch (error: Exception) {
                _state.update {
                    it.copy(memoryLoading = false, snackbar = error.message ?: "Não consegui carregar a memória.")
                }
            }
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch {
            try {
                api.deleteMemory(id)
                _state.update { it.copy(memory = it.memory.filterNot { entry -> entry.id == id }) }
            } catch (error: Exception) {
                _state.update { it.copy(snackbar = error.message ?: "Não consegui apagar.") }
            }
        }
    }

    fun clearMemory() {
        viewModelScope.launch {
            try {
                api.clearMemory()
                _state.update { it.copy(memory = emptyList(), snackbar = "Memória apagada.") }
            } catch (error: Exception) {
                _state.update { it.copy(snackbar = error.message ?: "Não consegui apagar a memória.") }
            }
        }
    }

    fun loadReminders() {
        viewModelScope.launch {
            _state.update { it.copy(remindersLoading = true) }
            try {
                refreshRemindersInternal()
                _state.update { it.copy(remindersLoading = false) }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        remindersLoading = false,
                        snackbar = error.message ?: "Não consegui carregar os lembretes.",
                    )
                }
            }
        }
    }

    private suspend fun refreshRemindersInternal() {
        if (settingsStore.current.serverUrl.isBlank()) return
        val items = api.reminders().reminders.sortedBy { it.dueAt }
        val previous = reminderStore.all()
        scheduler.cancelAll(previous)
        reminderStore.save(items)
        scheduler.scheduleAll(items)
        _state.update { it.copy(reminders = items, exactAlarms = scheduler.canScheduleExact()) }
    }

    fun saveSettings(
        serverUrl: String,
        token: String,
        assistantName: String,
        userName: String,
        language: String,
        persona: String,
        ttsEngine: String,
        voice: String,
        apiKey: String,
    ) {
        val normalized = normalizeServerUrl(serverUrl)
        if (normalized.isBlank()) {
            notify("Informe o endereço do servidor.")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                settingsStore.save(settingsStore.current.copy(serverUrl = normalized, token = token))
                val update = ConfigUpdate(
                    assistantName = assistantName.trim().ifBlank { "Orb" },
                    userName = userName.trim(),
                    language = language.trim().ifBlank { "pt-BR" },
                    voice = voice,
                    ttsEngine = if (ttsEngine == "gemini") "gemini" else "browser",
                    persona = persona.trim(),
                    apiKey = apiKey.trim().ifBlank { null },
                )
                val config = api.saveConfig(update)
                val engine = if (config.ttsEngine == "gemini") "gemini" else "device"
                settingsStore.save(
                    settingsStore.current.copy(
                        assistantName = config.assistantName,
                        language = config.language,
                        voice = config.voice,
                        ttsEngine = engine,
                    ),
                )
                _state.update {
                    it.copy(
                        serverUrl = normalized,
                        token = token,
                        assistantName = config.assistantName,
                        userName = config.userName,
                        language = config.language,
                        persona = config.persona,
                        voice = config.voice,
                        ttsEngine = engine,
                        voices = config.voices,
                        apiKeySet = config.apiKeySet,
                        busy = false,
                        status = if (config.apiKeySet) OrbStatus.Online else OrbStatus.MissingKey,
                        snackbar = "Ajustes salvos.",
                    )
                }
                runCatching { refreshRemindersInternal() }
            } catch (error: Exception) {
                _state.update {
                    it.copy(busy = false, snackbar = error.message ?: "Não consegui salvar os ajustes.")
                }
            }
        }
    }

    private fun speak(text: String) {
        if (!_state.value.ttsEnabled || text.isBlank()) return
        if (_state.value.ttsEngine == "gemini") {
            viewModelScope.launch {
                try {
                    val bytes = api.tts(text, _state.value.voice)
                    withContext(Dispatchers.Main) { container.wavPlayer.play(bytes) }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        container.tts.speak(text, _state.value.language)
                    }
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
