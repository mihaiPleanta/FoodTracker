package com.example.foodtracker.sync

enum class SyncOutcome { SUCCESS, DROP_SILENT, DROP_NOTIFY, RETRY }

/** [httpCode] null = IOException / timeout. */
fun classify(httpCode: Int?): SyncOutcome = when {
    httpCode == null -> SyncOutcome.RETRY
    httpCode in 200..299 -> SyncOutcome.SUCCESS
    httpCode == 404 -> SyncOutcome.DROP_SILENT
    httpCode == 401 -> SyncOutcome.RETRY
    httpCode in 400..499 -> SyncOutcome.DROP_NOTIFY
    else -> SyncOutcome.RETRY
}
