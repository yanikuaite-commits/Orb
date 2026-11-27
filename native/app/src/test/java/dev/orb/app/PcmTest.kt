package dev.orb.app

import dev.orb.app.data.Pcm
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmTest {
    @Test
    fun wrapsPcmInWavHeader() {
        val pcm = ByteArray(8) { it.toByte() }
        val wav = Pcm.toWav(pcm, 24_000)
        assertEquals(52, wav.size)
        assertEquals("RIFF", String(wav.copyOfRange(0, 4), Charsets.US_ASCII))
        assertEquals("WAVE", String(wav.copyOfRange(8, 12), Charsets.US_ASCII))
        assertEquals("data", String(wav.copyOfRange(36, 40), Charsets.US_ASCII))
        assertEquals(8, ByteBuffer.wrap(wav, 40, 4).order(ByteOrder.LITTLE_ENDIAN).int)
        assertEquals(24_000, ByteBuffer.wrap(wav, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int)
    }
}
