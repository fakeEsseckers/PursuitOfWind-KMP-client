package io.github.esseckers.pursuitofwind.core.platform

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import co.touchlab.kermit.Logger
import java.lang.ref.WeakReference

/**
 * The Android share sheet ([Intent.ACTION_SEND] in a chooser). It starts from the app's visible activity, so the sheet
 * belongs to the app's task and window. Visible means started, not only resumed: with the notification shade pulled
 * down or a dialog on top, the activity is paused yet still on screen, and the screens hand out their effects until
 * they stop. With none visible the app is in the background, where Android may block starting an activity without a
 * word, so the share is dropped and logged instead. Activities are followed through lifecycle callbacks and held
 * weakly, so none leaks.
 */
internal class AndroidShareLauncher(
    application: Application,
) : ShareLauncher {
    private var visible = WeakReference<Activity>(null)

    init {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    visible = WeakReference(activity)
                }

                override fun onActivityStopped(activity: Activity) {
                    if (visible.get() === activity) visible.clear()
                }

                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) = Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    outState: Bundle,
                ) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }

    override fun share(text: String) {
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
        val activity = visible.get()
        if (activity != null) {
            activity.startActivity(Intent.createChooser(send, null))
        } else {
            Logger.withTag("ShareLauncher").w { "No activity is visible, so the share sheet was not opened" }
        }
    }
}
