package dev.orb.app

import dev.orb.app.data.normalizeServerUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class UrlToolsTest {
    @Test
    fun addsHttpsForPublicHost() {
        assertEquals("https://orb.example.com", normalizeServerUrl("orb.example.com/"))
    }

    @Test
    fun keepsHttpForLocalNetwork() {
        assertEquals("http://192.168.0.10:8000", normalizeServerUrl("192.168.0.10:8000"))
    }

    @Test
    fun keepsExplicitScheme() {
        assertEquals("https://orb.example.com", normalizeServerUrl("https://orb.example.com/"))
    }

    @Test
    fun blankStaysBlank() {
        assertEquals("", normalizeServerUrl("   "))
    }
}
