package com.example.foodtracker.data.journal

import android.content.Context
import com.example.foodtracker.api.DaysApi
import com.example.foodtracker.api.RetrofitInstance
import com.example.foodtracker.sync.SyncProcessor
import com.example.foodtracker.sync.WorkManagerSyncScheduler
import com.google.firebase.auth.FirebaseAuth
import java.time.LocalDate

/** Process-wide [JournalRepository] and [SyncProcessor] (the repository's refresh epoch is per instance). */
object JournalProvider {
    @Volatile private var repo: JournalRepository? = null
    @Volatile private var proc: SyncProcessor? = null

    fun repository(context: Context): JournalRepository = repo ?: synchronized(this) {
        repo ?: build(context.applicationContext).also { repo = it }
    }

    fun processor(context: Context): SyncProcessor = proc ?: synchronized(this) {
        proc ?: SyncProcessor(
            repository(context),
            RetrofitInstance.logsApi,
            RetrofitInstance.retrofit.create(DaysApi::class.java),
        ).also { proc = it }
    }

    private fun build(app: Context): JournalRepository {
        val db = UserDataDb.get(app)
        return JournalRepository(
            dao = db.userDataDao(),
            tx = db.txRunner,
            logsApi = RetrofitInstance.logsApi,
            daysApi = RetrofitInstance.retrofit.create(DaysApi::class.java),
            scheduler = WorkManagerSyncScheduler(app),
            now = System::currentTimeMillis,
            today = LocalDate::now,
            meta = DataStoreJournalMeta(app.journalMetaDataStore),
            currentUid = { runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull() },
        )
    }
}
