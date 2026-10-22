package io.github.esseckers.pursuitofwind.android

import android.app.Application
import io.github.esseckers.pursuitofwind.app.di.startPursuitOfWind

class PursuitOfWindApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startPursuitOfWind(this)
    }
}
