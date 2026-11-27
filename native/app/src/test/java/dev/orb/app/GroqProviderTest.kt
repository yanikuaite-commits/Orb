package dev.orb.app

import dev.orb.app.agent.normalizeSchema
import dev.orb.app.data.GroqException
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroqProviderTest {
    @Test
    fun normalizesGeminiSchemaTypesForGroq() {
        val input = buildJsonObject {
            put("type", "OBJECT")
            putJsonObject("properties") {
                putJsonObject("query") { put("type", "STRING") }
                putJsonObject("days") { put("type", "INTEGER") }
                putJsonObject("tags") {
                    put("type", "ARRAY")
                    putJsonObject("items") { put("type", "STRING") }
                }
            }
            putJsonArray("required") { add("query") }
        }

        val output = normalizeSchema(input).jsonObject
        assertEquals("object", output["type"]?.jsonPrimitive?.content)

        val properties = output["properties"]!!.jsonObject
        assertEquals("string", properties["query"]!!.jsonObject["type"]?.jsonPrimitive?.content)
        assertEquals("integer", properties["days"]!!.jsonObject["type"]?.jsonPrimitive?.content)
        assertEquals("array", properties["tags"]!!.jsonObject["type"]?.jsonPrimitive?.content)
        assertEquals(
            "string",
            properties["tags"]!!.jsonObject["items"]!!.jsonObject["type"]?.jsonPrimitive?.content,
        )
    }

    @Test
    fun detectsGroqKeyErrors() {
        assertTrue(GroqException(401, "unauthorized").isKey)
        assertTrue(GroqException(403, "permission denied").isKey)
        assertFalse(GroqException(429, "rate limit").isKey)
        assertTrue(GroqException(429, "rate limit").isQuota)
    }
}
