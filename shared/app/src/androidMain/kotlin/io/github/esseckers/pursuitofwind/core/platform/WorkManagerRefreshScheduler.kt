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
 * Runs [WindowAlertsWorker] hourly through WorkManager, only with a network. The work is enqueued again on every start
 * of the app's process, with UPDATE: a schedule that exists keeps its next run time, so the next check is not pushed
 * back, and it takes on the request as it is now. With KEEP, a later version's constraints, period or worker class
 * would never reach an installation that is already scheduled.
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
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(NAME)
    }

    private companion object {
        const val NAME = "window-alerts"
        const val BACKOFF_MINUTES = 10L
    }
}
