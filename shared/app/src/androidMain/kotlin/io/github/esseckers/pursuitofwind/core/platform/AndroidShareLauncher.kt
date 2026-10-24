package io.github.esseckers.pursuitofwind.core.platform

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * The Android share sheet ([Intent.ACTION_SEND] in a chooser). It starts from the app's resumed activity, so the sheet
 * belongs to the app's task and window; only when none is resumed does it fall back to [application] with a task of
 * its own. Activities are followed through lifecycle callbacks and held weakly, so none leaks.
 */
internal class AndroidShareLauncher(
    private val application: Application,
) : ShareLauncher {
    private var resumed = WeakReference<Activity>(null)

    init {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    resumed = WeakReference(activity)
                }

                override fun onActivityPaused(activity: Activity) {
                    if (resumed.get() === activity) resumed.clear()
                }

                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) = Unit

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

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
        val chooser = Intent.createChooser(send, null)
        val activity = resumed.get()
        if (activity != null) {
            activity.startActivity(chooser)
        } else {
            application.startActivity(chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
