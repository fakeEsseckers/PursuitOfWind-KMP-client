package io.github.esseckers.pursuitofwind.core.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.esseckers.pursuitofwind.app.R
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.alert_channel
import org.jetbrains.compose.resources.getString

/**
 * Local notifications through the platform's [Notification.Builder]. The channel is created on every [show]: creating
 * an existing channel changes nothing but its name, which keeps the name in the app's current language. Tapping a
 * notification opens the app at the notification's link. The notification's id is its tag, so one about the same thing
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
        tapIntent(notification)?.let { builder.setContentIntent(it) }
        manager.notify(notification.id, NOTIFICATION_ID, builder.build())
    }

    /**
     * What a tap starts: the app with the notification's link, for the activity that takes the app's links, or the app
     * as the launcher starts it. The link is addressed to this app only, so no other app can answer it. Notifications
     * with different links already get different pending intents; the request code keeps them apart as well when
     * there is no link, where every one would otherwise share the launcher's.
     */
    private fun tapIntent(notification: LocalNotification): PendingIntent? {
        val link = notification.link?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)).setPackage(context.packageName) }
        val intent = link ?: context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getActivity(context, notification.id.hashCode(), intent, flags)
    }

    companion object {
        /** The one channel of the app; the permission check reads whether the rider turned it off. */
        const val CHANNEL_ID = "windows"

        // The tag tells notifications apart, so the numeric id stays the same.
        private const val NOTIFICATION_ID = 0
    }
}
