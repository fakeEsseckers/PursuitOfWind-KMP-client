package io.github.esseckers.pursuitofwind.testing

import io.github.esseckers.pursuitofwind.core.platform.BackgroundRefreshScheduler
import io.github.esseckers.pursuitofwind.core.platform.LocalNotification
import io.github.esseckers.pursuitofwind.core.platform.LocalNotifier
import io.github.esseckers.pursuitofwind.core.platform.NotificationPermission

/** The notification area in tests: keeps what would have been shown. */
internal class RecordingLocalNotifier : LocalNotifier {
    val shown = mutableListOf<LocalNotification>()

    override suspend fun show(notification: LocalNotification) {
        shown += notification
    }
}

/**
 * The system's say on notifications in tests. [allowed] is what it says now; a request is answered with [answer], which
 * then stands, as the system's dialog does. [requests] and [settingsOpened] count what the app asked for.
 */
internal class RecordingNotificationPermission : NotificationPermission {
    var allowed = false
    var answer = true
    var requests = 0
    var settingsOpened = 0

    override suspend fun granted(): Boolean = allowed

    override suspend fun request(): Boolean {
        requests++
        allowed = answer
        return allowed
    }

    override fun openSettings() {
        settingsOpened++
    }
}

/** The platform's periodic check in tests: keeps whether it is scheduled, and every call in order. */
internal class RecordingBackgroundRefreshScheduler : BackgroundRefreshScheduler {
    override var available = true
    val calls = mutableListOf<String>()
    val scheduled: Boolean get() = calls.lastOrNull() == SCHEDULE

    override fun schedule() {
        calls += SCHEDULE
    }

    override fun cancel() {
        calls += CANCEL
    }

    companion object {
        const val SCHEDULE = "schedule"
        const val CANCEL = "cancel"
    }
}
