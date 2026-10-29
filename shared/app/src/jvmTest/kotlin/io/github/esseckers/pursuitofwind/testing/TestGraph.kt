package io.github.esseckers.pursuitofwind.testing

import io.github.esseckers.pursuitofwind.app.di.appModules
import io.github.esseckers.pursuitofwind.app.language.AppLocale
import io.github.esseckers.pursuitofwind.core.map.SpotMap
import io.github.esseckers.pursuitofwind.core.platform.ShareLauncher
import io.github.esseckers.pursuitofwind.core.platform.TextClipboard
import io.github.esseckers.pursuitofwind.data.Environment
import io.github.esseckers.pursuitofwind.data.fake.ControllableClock
import io.github.esseckers.pursuitofwind.data.fake.FakePursuitOfWindServer
import io.github.esseckers.pursuitofwind.data.fake.ForecastProfile
import io.github.esseckers.pursuitofwind.data.fake.Seed
import io.github.esseckers.pursuitofwind.data.model.Outcome
import kotlinx.datetime.TimeZone
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * The app's own Koin graph for [Environment.Fake] at [clock], with the fake backend seeded by [steadyBreeze]: the real
 * data layer (HTTP to the in-app server, in-memory Room, DataStore), so screen tests need no fake repositories. The
 * platform's share sheet is a [RecordingShareLauncher], its locale a [RecordingAppLocale], its map a [ScriptedSpotMap],
 * its clipboard a [RecordingTextClipboard], and the rider is in Berlin, like the fixtures, whatever the machine's zone.
 * [overrides] replace definitions of the graph, such as the HTTP engine with [HeldRequests.engine].
 */
internal fun testGraph(
    clock: ControllableClock,
    vararg overrides: Module,
): KoinApplication =
    koinApplication {
        val platform =
            module {
                single { FakePursuitOfWindServer(get(), steadyBreeze(clock)) }
                single<ShareLauncher> { RecordingShareLauncher() }
                single<AppLocale> { RecordingAppLocale() }
                single<SpotMap> { ScriptedSpotMap() }
                single<TextClipboard> { RecordingTextClipboard() }
            }
        modules(appModules(Environment.Fake, clock, riderZone = { TimeZone.of("Europe/Berlin") }) + platform + overrides)
    }

/** The default seed (spots, Weekend Crew, Andrei's Saturday ride) with 18 kn from 240° everywhere, so verdicts are known. */
internal fun steadyBreeze(clock: ControllableClock): Seed {
    val seed = Seed.default(clock.now())
    val steady =
        ForecastProfile(
            meanKn = 18.0,
            swingKn = 0.0,
            swingPeriodHours = 24,
            seaBreezeKn = 0.0,
            bearingDeg = 240.0,
            bearingSwingDeg = 0.0,
            gustFactor = 0.1,
            modelSpreadKn = 1.0,
            noiseKn = 0.0,
            rainyShare = 0.0,
            temperatureC = 18.0,
        )
    return seed.copy(spots = seed.spots.map { it.copy(forecast = steady) })
}

internal fun <T> Outcome<T>.value(): T = (this as? Outcome.Success)?.value ?: error("Expected a success, got $this")
