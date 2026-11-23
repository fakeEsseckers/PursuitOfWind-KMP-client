package io.github.esseckers.pursuitofwind.core.platform

import android.content.Intent
import co.touchlab.kermit.Logger

/**
 * The Android share sheet ([Intent.ACTION_SEND] in a chooser). It starts from the app's [VisibleActivity], so the sheet
 * belongs to the app's task and window. With none visible the app is in the background, where Android may block
 * starting an activity without a word, so the share is dropped and logged instead.
 */
internal class AndroidShareLauncher(
    private val visibleActivity: VisibleActivity,
) : ShareLauncher {
    override fun share(text: String) {
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
        val activity = visibleActivity.get()
        if (activity != null) {
            activity.startActivity(Intent.createChooser(send, null))
        } else {
            Logger.withTag("ShareLauncher").w { "No activity is visible, so the share sheet was not opened" }
        }
    }
}
