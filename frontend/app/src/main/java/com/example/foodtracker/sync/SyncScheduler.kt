package com.example.foodtracker.sync

/** Asks for the op queue to be pushed to the server (WorkManager in production). */
interface SyncScheduler {
    fun requestSync()
}
