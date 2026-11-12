package io.github.esseckers.pursuitofwind.core.platform

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Notification permission on Android. [granted] asks the notification manager, which covers the runtime permission
 * from Android 13 and the app's notification switch on every version, and looks at the channel of the window alerts:
 * a rider can turn off that one category, and Android then drops its notifications without a word. Below Android 13
 * there is no dialog. The dialog needs a visible [ComponentActivity] to host it; with none, the request is refused
 * rather than queued.
 */
internal class AndroidNotificationPermission(
    private val context: Context,
    private val visibleActivity: VisibleActivity,
) : NotificationPermission {
    /** One question at a time: the system cancels a second request made while its dialog is up. */
    private val asking = Mutex()

    override suspend fun granted(): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        // No channel yet means no notification was shown yet; nothing is turned off then.
        val channelOff = manager.getNotificationChannel(AndroidLocalNotifier.CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE
        return manager.areNotificationsEnabled() && !channelOff
    }

    override suspend fun request(): Boolean =
        asking.withLock {
            // Below Android 13 there is no dialog, so only the rider's own switch in settings can change the answer.
            val activity = visibleActivity.get() as? ComponentActivity
            if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !granted()) {
                // The activity result registry must be used on the main thread.
                withContext(Dispatchers.Main.immediate) { ask(activity) }
            }
            granted()
        }

    override fun openSettings() {
        val intent =
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        val activity = visibleActivity.get()
        if (activity != null) {
            activity.startActivity(intent)
        } else {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /**
     * Shows the system's dialog and waits for it to close. The launcher is registered for this one question, under a
     * key of its own, and taken out again whatever happens: the registry never keeps a callback of a question that is
     * over, and an answer that arrives after the activity was recreated (a rotation while the dialog is up) is never
     * handed to the next question. That answer is not lost: the profile reads the permission again when it comes back.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun ask(activity: ComponentActivity) {
        var launcher: ActivityResultLauncher<String>? = null
        try {
            suspendCancellableCoroutine { continuation ->
                // The answer is read back from the notification manager, not from this callback.
                val contract = ActivityResultContracts.RequestPermission()
                val key = "notification-permission-${UUID.randomUUID()}"
                val question = activity.activityResultRegistry.register(key, contract) { continuation.resume(Unit) }
                launcher = question
                question.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } finally {
            launcher?.unregister()
        }
    }
}
