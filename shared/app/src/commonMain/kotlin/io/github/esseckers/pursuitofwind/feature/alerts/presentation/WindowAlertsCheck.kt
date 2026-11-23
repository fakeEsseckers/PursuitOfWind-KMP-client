package io.github.esseckers.pursuitofwind.feature.alerts.presentation

import co.touchlab.kermit.Logger
import io.github.esseckers.pursuitofwind.core.domain.mapper.DayDecisionsMapper
import io.github.esseckers.pursuitofwind.core.platform.LocalNotifier
import io.github.esseckers.pursuitofwind.core.platform.NotificationPermission
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Outcome
import io.github.esseckers.pursuitofwind.data.model.valueOrNull
import io.github.esseckers.pursuitofwind.feature.alerts.domain.entity.AlertsCheckResultEntity
import io.github.esseckers.pursuitofwind.feature.alerts.domain.entity.AlertsSnapshotEntity
import io.github.esseckers.pursuitofwind.feature.alerts.domain.mapper.NewWindowsMapper
import io.github.esseckers.pursuitofwind.feature.alerts.domain.repository.AlertsRepository
import io.github.esseckers.pursuitofwind.feature.alerts.presentation.mapper.AlertTextMapper
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/**
 * One check for new session windows, run by the platform's scheduler while the app is closed (ADR 0011): fetch the
 * favorite spots' forecasts, decide the days as Today does, and tell the rider about each GO day that is news. It does
 * nothing unless the rider asked for it and the system lets notifications through. A favorite whose forecast cannot be
 * fetched is left out of this check; the others are decided on as usual.
 *
 * A window is remembered as announced before its notification shows. If the app is stopped in between, that one
 * notification is lost; the other order would show it again every hour while the device cannot keep what it showed.
 */
internal class WindowAlertsCheck(
    private val repository: AlertsRepository,
    private val permission: NotificationPermission,
    private val notifier: LocalNotifier,
    private val clock: Clock,
    private val riderZone: TimeZone,
) {
    private val log = Logger.withTag("Alerts")

    /**
     * Data failures come back as the result, never as an exception: [AlertsCheckResultEntity.RETRY] when a forecast was
     * not fetched for a reason that may pass.
     */
    suspend fun run(): AlertsCheckResultEntity {
        val wanted = repository.enabled() && permission.granted()
        val snapshot = if (wanted) repository.fetch().valueOrNull() else null
        return if (snapshot == null) AlertsCheckResultEntity.DONE else decide(snapshot)
    }

    private suspend fun decide(snapshot: AlertsSnapshotEntity): AlertsCheckResultEntity {
        if (snapshot.failures.isNotEmpty()) log.i { "Forecasts not fetched: ${snapshot.failures}" }
        if (snapshot.forecasts.isNotEmpty()) announce(snapshot)
        return if (snapshot.failures.any { it.mayPass() }) AlertsCheckResultEntity.RETRY else AlertsCheckResultEntity.DONE
    }

    private suspend fun announce(snapshot: AlertsSnapshotEntity) {
        val now = clock.now()
        val decisions = DayDecisionsMapper.decide(snapshot.forecasts, snapshot.profile.rider, now, riderZone)
        // Without knowing what was announced, or without keeping what is, the same window would show every hour.
        val notified = repository.notified().valueOrNull() ?: return
        val found = NewWindowsMapper.newWindows(decisions, notified, now)
        if (found.notified != notified && repository.saveNotified(found.notified) is Outcome.Failure) return
        val spots = snapshot.forecasts.associate { it.spot.id to it.spot }
        found.fresh.forEach { day -> notifier.show(AlertTextMapper.notification(day, spots.getValue(day.spotId), now, riderZone)) }
    }

    /** A lost connection or a server that is down may be over well before the next scheduled check. */
    private fun AppError.mayPass(): Boolean = this == AppError.Network || this is AppError.Server
}
