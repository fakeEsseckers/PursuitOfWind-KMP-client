package io.github.esseckers.pursuitofwind.core.platform

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Runs [WindowAlertsWorker] hourly through WorkManager, only with a network. KEEP leaves a schedule that exists alone,
 * so turning the switch on again does not push the next run back.
 */
internal class WorkManagerRefreshScheduler(
    private val context: Context,
) : BackgroundRefreshScheduler {
    override val available: Boolean = true

    override fun schedule() {
        val request =
            PeriodicWorkRequestBuilder<WindowAlertsWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
                .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(NAME)
    }

    private companion object {
        const val NAME = "window-alerts"
        const val BACKOFF_MINUTES = 10L
    }
}
