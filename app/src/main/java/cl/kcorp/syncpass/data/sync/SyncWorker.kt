package cl.kcorp.syncpass.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cl.kcorp.syncpass.data.remote.SupabaseConfig
import cl.kcorp.syncpass.data.repository.SyncPassRepository

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        SyncPassRepository.init(applicationContext)
        return runCatching {
            SyncPassRepository.syncPending()
            SyncPassRepository.pendingCount()
        }.fold(
            onSuccess = { pending ->
                if (pending > 0 && SupabaseConfig.isConfigured && runAttemptCount < 5) {
                    Result.retry()
                } else {
                    Result.success()
                }
            },
            onFailure = {
                if (runAttemptCount < 5) Result.retry() else Result.failure()
            },
        )
    }
}
