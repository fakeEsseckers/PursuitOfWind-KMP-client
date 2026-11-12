package io.github.esseckers.pursuitofwind.feature.alerts.presentation

import io.github.esseckers.pursuitofwind.core.platform.BackgroundRefreshScheduler
import io.github.esseckers.pursuitofwind.feature.alerts.domain.repository.AlertsRepository

/**
 * Keeps the platform's periodic check in step with the rider's choice: scheduled while the rider wants to hear about
 * new windows, cancelled otherwise. The choice is the only thing screens change; this follows it for as long as the
 * app's process lives, starting with the choice as stored, so every start of the process puts the schedule right,
 * whatever happened to it meanwhile.
 */
internal class AlertsSchedule(
    private val repository: AlertsRepository,
    private val scheduler: BackgroundRefreshScheduler,
) {
    /** Runs until cancelled; returns at once where the device runs no checks. */
    suspend fun follow() {
        if (!scheduler.available) return
        repository.choice.collect { wanted -> if (wanted) scheduler.schedule() else scheduler.cancel() }
    }
}
