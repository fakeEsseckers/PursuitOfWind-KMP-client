package io.github.esseckers.pursuitofwind.core.platform

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * The app's visible activity. Visible means started, not only resumed: with the notification shade pulled down or a
 * dialog on top, the activity is paused yet still on screen, and the screens hand out their effects until they stop.
 * With none visible the app is in the background, where Android may block starting an activity without a word.
 * Activities are followed through lifecycle callbacks and held weakly, so none leaks. Create it once, with the graph,
 * so it sees the first activity start.
 */
internal class VisibleActivity(
    application: Application,
) {
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

    /** The visible activity, or null when the app is in the background. */
    fun get(): Activity? = visible.get()
}
