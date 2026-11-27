package dev.orb.app

import dev.orb.app.data.DEFAULT_GEMINI_TEXT_MODEL
import dev.orb.app.data.GEMINI_TEXT_MODELS
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvidersTest {
    @Test
    fun geminiDefaultAndFallbackAreListed() {
        assertTrue(DEFAULT_GEMINI_TEXT_MODEL in GEMINI_TEXT_MODELS)
        assertTrue("gemini-3.5-flash" in GEMINI_TEXT_MODELS)
    }
}
