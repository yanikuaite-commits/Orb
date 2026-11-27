package dev.orb.app

import dev.orb.app.agent.ApprovalCenter
import dev.orb.app.agent.FileApproval
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovalCenterTest {
    private fun approval() = FileApproval(
        id = "test",
        title = "Apagar",
        detail = "Apagar /sdcard/teste?",
        risk = "ALTO",
        paths = listOf("/sdcard/teste"),
    )

    @Test
    fun resolvesApprovedRequest() = runBlocking {
        val center = ApprovalCenter()
        val result = async { center.request(approval()) }
        while (center.pending.value == null) yield()
        center.resolve(true)
        assertTrue(result.await())
    }

    @Test
    fun resolvesDeniedRequest() = runBlocking {
        val center = ApprovalCenter()
        val result = async { center.request(approval()) }
        while (center.pending.value == null) yield()
        center.resolve(false)
        assertFalse(result.await())
    }
}
