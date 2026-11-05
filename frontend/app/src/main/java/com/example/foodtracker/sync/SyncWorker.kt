package com.example.foodtracker.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.foodtracker.data.journal.JournalProvider
import kotlinx.coroutines.CancellationException

class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result =
        when (JournalProvider.processor(applicationContext).drain()) {
            DrainResult.DONE -> {
                // Queue empty: converge the mirror with the server (best-effort, refused for another account).
                try {
                    JournalProvider.repository(applicationContext).refreshWindow()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
                Result.success()
            }
            DrainResult.RETRY -> Result.retry()
        }
}
