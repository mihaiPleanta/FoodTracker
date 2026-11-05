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

/** Cancels the queued/running sync chain (logout, account deletion, account switch). */
fun cancelJournalSync(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork(WorkManagerSyncScheduler.UNIQUE_NAME)
}

/** Production scheduler: one unique chain of [SyncWorker] runs, appended while one is already queued. */
class WorkManagerSyncScheduler(private val context: Context) : SyncScheduler {
    override fun requestSync() = requestSync(replace = false)

    /**
     * [replace] = true (app resume) restarts the chain now, dropping a run parked in backoff;
     * mutation-triggered syncs append so an in-progress drain is never cancelled.
     */
    fun requestSync(replace: Boolean) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                UNIQUE_NAME,
                if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
    }

    companion object {
        /** Unique work name of the sync chain (cancelled on logout). */
        const val UNIQUE_NAME = "journal-sync"
    }
}
