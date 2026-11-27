package dev.orb.app.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TtsPlayer(context: Context) {
    private var ready = false
    private val engine = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }

    fun speak(text: String, languageTag: String) {
        if (!ready || text.isBlank()) return
        engine.language = Locale.forLanguageTag(languageTag.ifBlank { "pt-BR" })
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "orb")
    }

    fun stop() {
        if (ready) engine.stop()
    }

    fun shutdown() {
        engine.stop()
        engine.shutdown()
    }
}
