package dev.orb.app.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class SpeechRecognizerManager(
    context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
) : RecognitionListener {
    private val appContext = context.applicationContext
    private val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
        setRecognitionListener(this@SpeechRecognizerManager)
    }
    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(appContext)

    fun start(languageTag: String) {
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag.ifBlank { "pt-BR" })
        recognizer.startListening(intent)
    }

    fun stop() = recognizer.stopListening()

    fun destroy() = recognizer.destroy()

    override fun onResults(results: Bundle?) {
        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()
        if (text.isBlank()) onError("Não entendi. Tente de novo.") else onResult(text)
    }

    override fun onError(error: Int) {
        val message = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Não entendi. Tente de novo."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permita o microfone para o Orb."
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Sem conexão para reconhecer a voz."
            else -> "Não consegui ouvir. Tente de novo."
        }
        onError(message)
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}
