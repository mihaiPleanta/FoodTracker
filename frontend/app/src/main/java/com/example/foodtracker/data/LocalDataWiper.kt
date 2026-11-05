package com.example.foodtracker.data

import android.util.Log
import kotlinx.coroutines.CancellationException

/**
 * Runs every wipe step even when an earlier one throws (a failed journal clear must not leave
 * the previous account's food cache or profile snapshot behind). Failures are logged; cancellation propagates.
 */
class LocalDataWiper(private val steps: List<suspend () -> Unit>) {
    suspend fun wipe() {
        for (step in steps) {
            try {
                step()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                try { Log.w("LocalDataWiper", "wipe step failed", e) } catch (_: Throwable) { }
            }
        }
    }
}
