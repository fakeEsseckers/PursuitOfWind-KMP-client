package io.github.esseckers.pursuitofwind.android

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.StrictMode
import io.github.esseckers.pursuitofwind.app.di.startPursuitOfWind

class PursuitOfWindApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) watchForMistakes()
        startPursuitOfWind(this)
    }

    /**
     * A debuggable build logs to Logcat (tag StrictMode) disk or network work on the main thread, resources never
     * closed, leaked activities and, from Android 12, intents from outside started unchecked. It only logs, so a
     * developer sees where the app does this without the app stopping.
     */
    private fun watchForMistakes() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy
                .Builder()
                .detectAll()
                .penaltyLog()
                .build(),
        )
        val vm =
            StrictMode.VmPolicy
                .Builder()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .penaltyLog()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) vm.detectUnsafeIntentLaunch()
        StrictMode.setVmPolicy(vm.build())
    }
}
