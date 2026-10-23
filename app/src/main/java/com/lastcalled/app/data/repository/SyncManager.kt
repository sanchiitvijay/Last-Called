package com.lastcalled.app.data.repository

import com.lastcalled.domain.sync.SyncCallHistoryUseCase
import com.lastcalled.domain.sync.SyncResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Running : SyncStatus
    data class Finished(val result: SyncResult, val atMillis: Long) : SyncStatus
}

/**
 * Runs imports one at a time, off the main thread, in the application scope (so leaving a screen
 * does not cancel a half-finished import). A manual request made while an import is running (for
 * example right after contacts access is granted) schedules one more run, so it is never lost.
 */
class SyncManager(
    private val sync: SyncCallHistoryUseCase,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
) {
    private val mutex = Mutex()
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    @Volatile private var lastFinishedAt = 0L
    @Volatile private var rerunRequested = false

    /** Manual refresh. */
    fun refresh() = launchSync(minIntervalMillis = 0)

    /** Automatic refresh (app start / foreground); skipped if a sync finished very recently. */
    fun refreshIfStale() = launchSync(minIntervalMillis = AUTO_REFRESH_INTERVAL_MS)

    private fun launchSync(minIntervalMillis: Long) {
        if (clock() - lastFinishedAt < minIntervalMillis) return
        if (!mutex.tryLock()) {
            if (minIntervalMillis == 0L) rerunRequested = true
            return
        }
        scope.launch {
            try {
                rerunRequested = false
                _status.value = SyncStatus.Running
                val result = try {
                    sync()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Never surface exception messages: they could contain provider data.
                    SyncResult(false, false, 0, 0, 0, 0, 0, 0, false, failure = e.javaClass.simpleName)
                }
                lastFinishedAt = clock()
                _status.value = SyncStatus.Finished(result, lastFinishedAt)
            } finally {
                if (_status.value == SyncStatus.Running) _status.value = SyncStatus.Idle
                mutex.unlock()
            }
            if (rerunRequested) launchSync(minIntervalMillis = 0)
        }
    }

    /** Called after data is cleared so the next foreground refresh is not throttled. */
    fun forgetLastSync() {
        lastFinishedAt = 0
        _status.value = SyncStatus.Idle
    }

    private companion object {
        const val AUTO_REFRESH_INTERVAL_MS = 30_000L
    }
}
