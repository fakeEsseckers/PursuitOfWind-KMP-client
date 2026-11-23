package io.github.esseckers.pursuitofwind.feature.alerts.domain.mapper

import io.github.esseckers.pursuitofwind.data.model.NotifiedWindow
import io.github.esseckers.pursuitofwind.engine.model.DayDecision
import io.github.esseckers.pursuitofwind.engine.model.SessionWindow
import io.github.esseckers.pursuitofwind.feature.alerts.domain.entity.NewWindowsEntity
import kotlin.time.Instant

/**
 * Which GO days are news to the rider (ADR 0011). Every forecast is a new model run: it can move a window by an hour
 * and change which of two close spots ranks first. So a window is the one already announced when it is at the same
 * spot, on the same day and shares time with it, and a day is news only when none of its windows, neither the best one
 * nor an alternative, was announced. An exact start, or the best spot alone, would announce the same day again and
 * again.
 */
internal object NewWindowsMapper {
    /**
     * The GO days of [decisions] with no window that was [notified] before, and what to remember from now on: the
     * announced windows that are not over at [now], each as the forecast has it now, plus the best window of every new
     * day. A window the forecast dropped stays remembered until it would have ended, so it is not announced again if
     * it comes back.
     */
    fun newWindows(
        decisions: List<DayDecision>,
        notified: List<NotifiedWindow>,
        now: Instant,
    ): NewWindowsEntity {
        val goDays = decisions.filterIsInstance<DayDecision.Go>()
        val current = notified.filter { it.end > now }
        val (known, fresh) = goDays.partition { day -> current.any { it.windowOn(day) != null } }
        val moved = current.map { entry -> known.firstNotNullOfOrNull { entry.windowOn(it) }?.let { entry.movedTo(it) } ?: entry }
        val announced = fresh.map { NotifiedWindow(it.spotId, it.date, it.window.start, it.window.end) }
        return NewWindowsEntity(fresh = fresh, notified = (moved + announced).distinct())
    }

    /** The window of [day] that is this one, among its best window and the alternatives; null when none is. */
    private fun NotifiedWindow.windowOn(day: DayDecision.Go): SessionWindow? =
        if (date != day.date) null else (listOf(day.window) + day.alternatives).firstOrNull { it.spotId == spotId && overlaps(it) }

    private fun NotifiedWindow.overlaps(window: SessionWindow): Boolean = start < window.end && window.start < end

    private fun NotifiedWindow.movedTo(window: SessionWindow): NotifiedWindow = copy(start = window.start, end = window.end)
}
