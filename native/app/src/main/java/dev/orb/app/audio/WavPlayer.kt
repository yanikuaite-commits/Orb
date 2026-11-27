package dev.orb.app.audio

import android.content.Context
import android.media.MediaPlayer
import java.io.File

class WavPlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private var current: File? = null

    fun play(bytes: ByteArray) {
        stop()
        val file = File.createTempFile("orb-tts-", ".wav", context.cacheDir)
        file.writeBytes(bytes)
        current = file
        player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener { stop() }
            prepare()
            start()
        }
    }

    fun stop() {
        runCatching { player?.release() }
        player = null
        current?.delete()
        current = null
    }
}
