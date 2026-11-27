package dev.orb.app.agent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

data class FileApproval(
    val id: String,
    val title: String,
    val detail: String,
    val risk: String,
    val paths: List<String>,
)

class ApprovalCenter {
    private val _pending = MutableStateFlow<FileApproval?>(null)
    val pending: StateFlow<FileApproval?> = _pending.asStateFlow()

    private val mutex = Mutex()
    @Volatile
    private var waiter: CompletableDeferred<Boolean>? = null

    suspend fun request(approval: FileApproval): Boolean = mutex.withLock {
        val deferred = CompletableDeferred<Boolean>()
        waiter = deferred
        _pending.value = approval
        try {
            withTimeoutOrNull(TIMEOUT_MS) { deferred.await() } ?: false
        } finally {
            _pending.value = null
            waiter = null
        }
    }

    fun resolve(approved: Boolean) {
        waiter?.complete(approved)
    }

    private companion object {
        const val TIMEOUT_MS = 180_000L
    }
}
