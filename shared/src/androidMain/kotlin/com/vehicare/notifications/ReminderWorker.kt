package com.vehicare.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * The WorkManager [CoroutineWorker] that evaluates and delivers on Android.
 *
 * The worker holds no logic — it builds the same orchestrator the iOS sweep builds; it must never
 * call `DatabaseProvider.initialize` and never construct a driver, because `VehiCareApp.onCreate`
 * already did and a second `VehiCareDatabase` would break the UI's `Flow`s. Retry is bounded so a
 * permanently broken database cannot spin forever, and this is where D-52's "no exact alarms, no
 * boot receiver" pays off: WorkManager re-arms itself after reboot.
 */
class ReminderWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result =
        runCatching {
            ReminderDeliveryFactory.create(createNotificationScheduler(applicationContext)).evaluateAndDeliver()
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.failure() },
        )

    private companion object {
        private const val MAX_RETRY_ATTEMPTS = 3
    }
}
