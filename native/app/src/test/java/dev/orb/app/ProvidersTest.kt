package dev.orb.app

import dev.orb.app.data.DEFAULT_GEMINI_TEXT_MODEL
import dev.orb.app.data.GEMINI_TEXT_MODELS
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvidersTest {
    @Test
    fun geminiDefaultIsListedAndTheRemovedModelIsGone() {
        assertTrue(DEFAULT_GEMINI_TEXT_MODEL in GEMINI_TEXT_MODELS)
        assertFalse("gemini-3.5-flash" in GEMINI_TEXT_MODELS)
    }
}
