package io.github.esseckers.pursuitofwind.feature.alerts.domain.mapper

import io.github.esseckers.pursuitofwind.data.model.NotifiedWindow
import io.github.esseckers.pursuitofwind.engine.model.DayDecision
import io.github.esseckers.pursuitofwind.engine.model.SessionWindow
import io.github.esseckers.pursuitofwind.feature.alerts.domain.entity.NewWindowsEntity
import kotlin.time.Instant

/**
 * Which session windows are news to the rider (ADR 0011). A day's window is announced once. Every forecast is a new
 * model run and can move a window by an hour, so a window is the one already announced when it is at the same spot, on
 * the same day and shares time with it; an exact start would announce the same session again and again.
 */
internal object NewWindowsMapper {
    /**
     * The GO days of [decisions] whose window was not [notified] before, and what to remember from now on: the
     * announced windows that are not over at [now], each as the forecast has it now, plus the new ones. A window the
     * forecast dropped stays remembered until it would have ended, so it is not announced again if it comes back.
     */
    fun newWindows(
        decisions: List<DayDecision>,
        notified: List<NotifiedWindow>,
        now: Instant,
    ): NewWindowsEntity {
        val goDays = decisions.filterIsInstance<DayDecision.Go>()
        val current = notified.filter { it.end > now }
        val (known, fresh) = goDays.partition { day -> current.any { it.isSameAs(day) } }
        val moved = current.map { entry -> known.firstOrNull { entry.isSameAs(it) }?.toNotified() ?: entry }
        return NewWindowsEntity(fresh = fresh, notified = (moved + fresh.map { it.toNotified() }).distinct())
    }

    private fun NotifiedWindow.isSameAs(day: DayDecision.Go): Boolean = spotId == day.spotId && date == day.date && overlaps(day.window)

    private fun NotifiedWindow.overlaps(window: SessionWindow): Boolean = start < window.end && window.start < end

    private fun DayDecision.Go.toNotified(): NotifiedWindow = NotifiedWindow(spotId, date, window.start, window.end)
}
