package io.github.esseckers.pursuitofwind.feature.alerts.di

import io.github.esseckers.pursuitofwind.feature.alerts.data.repository.AlertsRepositoryImpl
import io.github.esseckers.pursuitofwind.feature.alerts.domain.repository.AlertsRepository
import io.github.esseckers.pursuitofwind.feature.alerts.presentation.AlertsSchedule
import io.github.esseckers.pursuitofwind.feature.alerts.presentation.WindowAlertsCheck
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Telling the rider about new session windows while the app is closed: its repository over the shared data layer, one
 * check per run (so each reads where the rider is), and what keeps the platform's schedule in step with the rider's
 * choice.
 */
internal val alertsModule: Module =
    module {
        factory<AlertsRepository> { AlertsRepositoryImpl(get(), get(), get(), get()) }
        factory { WindowAlertsCheck(repository = get(), permission = get(), notifier = get(), clock = get(), riderZone = get()) }
        factory { AlertsSchedule(repository = get(), scheduler = get()) }
    }
