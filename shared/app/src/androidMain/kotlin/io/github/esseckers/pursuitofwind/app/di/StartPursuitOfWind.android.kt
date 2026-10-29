package io.github.esseckers.pursuitofwind.app.di

import android.app.Application
import android.content.Context
import io.github.esseckers.pursuitofwind.app.language.AndroidAppLocale
import io.github.esseckers.pursuitofwind.app.language.AppLocale
import io.github.esseckers.pursuitofwind.core.map.MapLibreSpotMap
import io.github.esseckers.pursuitofwind.core.map.SpotMap
import io.github.esseckers.pursuitofwind.core.platform.AndroidShareLauncher
import io.github.esseckers.pursuitofwind.core.platform.AndroidTextClipboard
import io.github.esseckers.pursuitofwind.core.platform.ShareLauncher
import io.github.esseckers.pursuitofwind.core.platform.TextClipboard
import org.koin.dsl.module

/** Starts the app's dependency graph. Call from `Application.onCreate`, before any screen is shown. */
fun startPursuitOfWind(application: Application) {
    startKoinOnce(
        platform =
            module {
                single<Context> { application }
                // Created with the graph, before any activity, so it sees the first activity resume.
                single<ShareLauncher>(createdAtStart = true) { AndroidShareLauncher(application) }
                single<AppLocale> { AndroidAppLocale() }
                single<SpotMap> { MapLibreSpotMap() }
                single<TextClipboard> { AndroidTextClipboard(application) }
            },
    )
}
