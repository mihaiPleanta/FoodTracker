package com.example.foodtracker.data

import kotlinx.coroutines.CancellationException

const val LOGOUT_SYNC_TIMEOUT_MS = 5000L

sealed interface LogoutCheck {
    data object Ready : LogoutCheck
    data class Unsynced(val count: Int) : LogoutCheck
}

/** Decides whether logging out would lose queued changes, and wipes the account's local data. */
class LogoutCoordinator(
    private val sync: suspend (Long) -> Boolean,
    private val pendingCount: suspend () -> Int,
    private val clearLocal: suspend () -> Unit,
) {
    /**
     * Tries to push the queue first; [LogoutCheck.Unsynced] when changes remain (offline, rejected...)
     * or when the check itself fails (never throws but cancellation: an unknown state must ask).
     */
    suspend fun tryLogout(): LogoutCheck {
        try {
            sync(LOGOUT_SYNC_TIMEOUT_MS)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        val pending = try {
            pendingCount()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return LogoutCheck.Unsynced(1)
        }
        return if (pending == 0) LogoutCheck.Ready else LogoutCheck.Unsynced(pending)
    }

    suspend fun clearLocal() = clearLocal.invoke()
}
