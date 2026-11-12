package io.github.esseckers.pursuitofwind.testing

import io.github.esseckers.pursuitofwind.core.platform.BackgroundRefreshScheduler
import io.github.esseckers.pursuitofwind.core.platform.LocalNotification
import io.github.esseckers.pursuitofwind.core.platform.LocalNotifier
import io.github.esseckers.pursuitofwind.core.platform.NotificationPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

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

/**
 * The platform's periodic check in tests: keeps every call in order. The calls come from whatever thread stores the
 * rider's choice, so a test waits for them with [awaitCalls] instead of looking at once.
 */
internal class RecordingBackgroundRefreshScheduler : BackgroundRefreshScheduler {
    override var available = true
    private val recorded = MutableStateFlow(emptyList<String>())
    val calls: List<String> get() = recorded.value

    override fun schedule() {
        recorded.update { it + SCHEDULE }
    }

    override fun cancel() {
        recorded.update { it + CANCEL }
    }

    /** Suspends until at least [count] calls were made, and returns them. */
    suspend fun awaitCalls(count: Int): List<String> = recorded.first { it.size >= count }

    companion object {
        const val SCHEDULE = "schedule"
        const val CANCEL = "cancel"
    }
}
