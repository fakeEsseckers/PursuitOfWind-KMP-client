package io.github.esseckers.pursuitofwind.app.di

import io.github.esseckers.pursuitofwind.data.Environment
import io.github.esseckers.pursuitofwind.data.di.dataModule
import io.github.esseckers.pursuitofwind.feature.alerts.di.alertsModule
import io.github.esseckers.pursuitofwind.feature.alerts.presentation.AlertsSchedule
import io.github.esseckers.pursuitofwind.feature.crew.di.crewModule
import io.github.esseckers.pursuitofwind.feature.profile.di.profileModule
import io.github.esseckers.pursuitofwind.feature.rideoffer.di.rideOfferModule
import io.github.esseckers.pursuitofwind.feature.spotdetail.di.spotDetailModule
import io.github.esseckers.pursuitofwind.feature.spotmap.di.spotMapModule
import io.github.esseckers.pursuitofwind.feature.spotpicker.di.spotPickerModule
import io.github.esseckers.pursuitofwind.feature.spots.di.spotsModule
import io.github.esseckers.pursuitofwind.feature.today.di.todayModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import kotlin.time.Clock

/**
 * The whole graph for [environment], apart from platform objects such as the Android `Context`. [riderZone] is where
 * the rider is, read each time a screen is created; tests pin it, so they do not depend on the machine's zone.
 */
internal fun appModules(
    environment: Environment,
    clock: Clock = Clock.System,
    riderZone: () -> TimeZone = TimeZone::currentSystemDefault,
): List<Module> =
    listOf(
        module { factory<TimeZone> { riderZone() } },
        dataModule(environment, clock),
        todayModule,
        spotDetailModule,
        profileModule,
        spotPickerModule,
        spotMapModule,
        spotsModule,
        crewModule,
        rideOfferModule,
        alertsModule,
    )

/**
 * Starts Koin once per process with [platform] bindings. The app talks to the in-app fake backend, which behaves like
 * a live server, until the real server exists. From here on the platform's background check follows the rider's choice
 * ([AlertsSchedule]), also when the system started the process without a screen.
 */
internal fun startKoinOnce(platform: Module) {
    if (KoinPlatform.getKoinOrNull() != null) return
    val koin = startKoin { modules(listOf(platform) + appModules(Environment.Fake)) }.koin
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { koin.get<AlertsSchedule>().follow() }
}
