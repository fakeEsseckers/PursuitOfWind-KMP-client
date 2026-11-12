package io.github.esseckers.pursuitofwind.core.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import io.github.esseckers.pursuitofwind.app.R
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.alert_channel
import org.jetbrains.compose.resources.getString

/**
 * Local notifications through the platform's [Notification.Builder]. The channel is created on every [show]: creating
 * an existing channel changes nothing but its name, which keeps the name in the app's current language. Tapping a
 * notification opens the app's launcher activity. The notification's id is its tag, so one about the same thing
 * replaces the earlier one.
 */
internal class AndroidLocalNotifier(
    private val context: Context,
) : LocalNotifier {
    override suspend fun show(notification: LocalNotification) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return

        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(Res.string.alert_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val builder =
            Notification
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_wind)
                .setContentTitle(notification.title)
                .setContentText(notification.text)
                .setStyle(Notification.BigTextStyle().bigText(notification.text))
                .setCategory(Notification.CATEGORY_RECOMMENDATION)
                .setAutoCancel(true)
        launchIntent()?.let { builder.setContentIntent(it) }
        manager.notify(notification.id, NOTIFICATION_ID, builder.build())
    }

    private fun launchIntent(): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        /** The one channel of the app; the permission check reads whether the rider turned it off. */
        const val CHANNEL_ID = "windows"

        // The tag tells notifications apart, so the numeric id stays the same.
        private const val NOTIFICATION_ID = 0
    }
}
