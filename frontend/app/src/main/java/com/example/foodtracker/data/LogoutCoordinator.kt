package com.example.foodtracker.data

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
    /** Tries to push the queue first; [LogoutCheck.Unsynced] when changes remain (offline, rejected...). */
    suspend fun tryLogout(): LogoutCheck {
        sync(LOGOUT_SYNC_TIMEOUT_MS)
        val pending = pendingCount()
        return if (pending == 0) LogoutCheck.Ready else LogoutCheck.Unsynced(pending)
    }

    suspend fun clearLocal() = clearLocal.invoke()
}
