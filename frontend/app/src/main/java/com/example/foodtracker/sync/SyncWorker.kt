package com.example.foodtracker.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.foodtracker.data.journal.JournalProvider

class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        // An op left "in flight" by a crash or a killed process is sent again.
        JournalProvider.repository(applicationContext).releaseInFlight()
        return when (JournalProvider.processor(applicationContext).drain()) {
            DrainResult.DONE -> Result.success()
            DrainResult.RETRY -> Result.retry()
        }
    }
}
