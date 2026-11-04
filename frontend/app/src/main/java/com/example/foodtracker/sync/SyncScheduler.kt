package com.example.foodtracker.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Asks for the op queue to be pushed to the server (WorkManager in production). */
interface SyncScheduler {
    fun requestSync()
}

/** Production scheduler: one unique chain of [SyncWorker] runs, appended while one is already queued. */
class WorkManagerSyncScheduler(private val context: Context) : SyncScheduler {
    override fun requestSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    companion object {
        /** Unique work name of the sync chain (cancelled on logout). */
        const val UNIQUE_NAME = "journal-sync"
    }
}
