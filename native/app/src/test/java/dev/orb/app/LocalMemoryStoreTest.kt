package dev.orb.app

import dev.orb.app.data.LocalMemoryStore
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalMemoryStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun store(): LocalMemoryStore {
        val file = temp.newFile("memory.json")
        file.delete()
        return LocalMemoryStore(file, Json { ignoreUnknownKeys = true })
    }

    @Test
    fun addsSearchesAndForgets() {
        val store = store()
        store.add("cidade", "São Paulo", "identity")
        store.add("projeto", "Orb Android", "projects")
        assertEquals(2, store.all().size)
        assertEquals("São Paulo", store.search("cidade").first().value)
        assertEquals(1, store.forget("cidade"))
        assertEquals(1, store.all().size)
    }

    @Test
    fun updatesExistingKeyInsteadOfDuplicating() {
        val store = store()
        store.add("cor", "azul", "preferences")
        store.add("cor", "vermelho", "preferences")
        assertEquals(1, store.all().size)
        assertEquals("vermelho", store.all().first().value)
    }

    @Test
    fun summaryIncludesIdentityFirst() {
        val store = store()
        store.add("nome", "Yan", "identity")
        store.add("nota", "teste", "notes")
        val summary = store.summary()
        assertTrue(summary.indexOf("identity") < summary.indexOf("notes"))
    }
}
