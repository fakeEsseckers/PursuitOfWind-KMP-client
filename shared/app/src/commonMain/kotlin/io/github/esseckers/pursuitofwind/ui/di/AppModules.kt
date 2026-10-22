package io.github.esseckers.pursuitofwind.ui.di

import io.github.esseckers.pursuitofwind.data.Environment
import io.github.esseckers.pursuitofwind.data.di.dataModule
import io.github.esseckers.pursuitofwind.ui.today.TodaySources
import io.github.esseckers.pursuitofwind.ui.today.TodayViewModel
import kotlinx.datetime.TimeZone
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import kotlin.time.Clock

/** Presentation: one ViewModel per screen and what it needs beyond the data layer. */
internal val presentationModule: Module =
    module {
        factoryOf(::TodaySources)
        viewModel { TodayViewModel(sources = get(), clock = get(), riderZone = TimeZone.currentSystemDefault()) }
    }

/** The whole graph for [environment], apart from platform objects such as the Android `Context`. */
internal fun appModules(
    environment: Environment,
    clock: Clock = Clock.System,
): List<Module> = listOf(dataModule(environment, clock), presentationModule)

/**
 * Starts Koin once per process with [platform] bindings. The app talks to the in-app fake backend until a server
 * exists; demo mode (Issue #10) brings its own environment.
 */
internal fun startKoinOnce(platform: Module) {
    if (KoinPlatform.getKoinOrNull() != null) return
    startKoin { modules(listOf(platform) + appModules(Environment.Fake)) }
}
