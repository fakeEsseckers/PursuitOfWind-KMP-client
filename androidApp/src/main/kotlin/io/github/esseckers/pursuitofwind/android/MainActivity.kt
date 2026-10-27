package io.github.esseckers.pursuitofwind.android

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.esseckers.pursuitofwind.app.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // A transparent navigation bar with no system scrim, also on Android 8 and 9: the tab bar and the spot picker's
        // Done bar reach the bottom edge in their own color, and screens that scroll behind three-button navigation
        // draw their own scrim (NavigationBarScrim).
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}
