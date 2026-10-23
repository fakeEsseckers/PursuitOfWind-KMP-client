package io.github.esseckers.pursuitofwind.app.di

import io.github.esseckers.pursuitofwind.data.Environment
import io.github.esseckers.pursuitofwind.data.di.dataModule
import io.github.esseckers.pursuitofwind.feature.profile.di.profileModule
import io.github.esseckers.pursuitofwind.feature.spotdetail.di.spotDetailModule
import io.github.esseckers.pursuitofwind.feature.spotpicker.di.spotPickerModule
import io.github.esseckers.pursuitofwind.feature.today.di.todayModule
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.mp.KoinPlatform
import kotlin.time.Clock

/** The whole graph for [environment], apart from platform objects such as the Android `Context`. */
internal fun appModules(
    environment: Environment,
    clock: Clock = Clock.System,
): List<Module> = listOf(dataModule(environment, clock), todayModule, spotDetailModule, profileModule, spotPickerModule)

/**
 * Starts Koin once per process with [platform] bindings. The app talks to the in-app fake backend, which behaves like
 * a live server, until the real server exists.
 */
internal fun startKoinOnce(platform: Module) {
    if (KoinPlatform.getKoinOrNull() != null) return
    startKoin { modules(listOf(platform) + appModules(Environment.Fake)) }
}
