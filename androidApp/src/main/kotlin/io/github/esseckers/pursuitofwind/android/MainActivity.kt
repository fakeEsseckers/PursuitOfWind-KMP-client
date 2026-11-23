package io.github.esseckers.pursuitofwind.android

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.esseckers.pursuitofwind.app.App
import io.github.esseckers.pursuitofwind.app.openLink

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // A transparent navigation bar with no system scrim, also on Android 8 and 9: the tab bar and the spot picker's
        // Done bar reach the bottom edge in their own color, and screens that scroll behind three-button navigation
        // draw their own scrim (NavigationBarScrim).
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        super.onCreate(savedInstanceState)
        // Only a fresh start: a restored activity gets the same intent again, and its link was opened before.
        if (savedInstanceState == null) openLinkOf(intent)
        setContent { App() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openLinkOf(intent)
    }

    /**
     * Hands a link the activity was opened with to the app, which checks it and ignores what it does not know. Nothing
     * else of the intent is read: it may come from any app. A task brought back from Recents comes with the intent
     * that first started it, so that one is skipped: its link was opened then.
     */
    private fun openLinkOf(intent: Intent?) {
        val fromRecents = intent != null && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (intent?.action == Intent.ACTION_VIEW && !fromRecents) intent.dataString?.let(::openLink)
    }
}
