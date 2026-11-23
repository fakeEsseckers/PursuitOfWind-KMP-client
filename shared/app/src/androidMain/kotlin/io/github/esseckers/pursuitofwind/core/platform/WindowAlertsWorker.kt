package io.github.esseckers.pursuitofwind.core.platform

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.esseckers.pursuitofwind.app.checkForNewWindows
import org.koin.mp.KoinPlatform

/**
 * One run of the hourly check for new session windows. The application starts Koin in `onCreate`, before WorkManager
 * runs anything, so a missing graph is only a process that is still starting: try again later.
 */
internal class WindowAlertsWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val koin = KoinPlatform.getKoinOrNull() ?: return Result.retry()
        return if (checkForNewWindows(koin)) Result.success() else Result.retry()
    }
}
