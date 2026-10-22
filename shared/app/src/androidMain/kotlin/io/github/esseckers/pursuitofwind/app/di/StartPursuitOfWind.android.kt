package io.github.esseckers.pursuitofwind.app.di

import android.app.Application
import android.content.Context
import org.koin.dsl.module

/** Starts the app's dependency graph. Call from `Application.onCreate`, before any screen is shown. */
fun startPursuitOfWind(application: Application) {
    startKoinOnce(platform = module { single<Context> { application } })
}
