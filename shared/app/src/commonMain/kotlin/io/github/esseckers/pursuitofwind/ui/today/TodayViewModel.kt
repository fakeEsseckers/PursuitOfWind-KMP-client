package io.github.esseckers.pursuitofwind.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Today's decisions: the cached data run through the decision engine. The screen refreshes when it appears, again when
 * it comes back after a failed refresh, after [REFRESH_AFTER] or with a favorite spot that has no forecast yet, and on
 * request. Decisions are recomputed every [tick] of [clock], so "leave by" and "updated 5 min ago" stay true while the
 * screen is open; demo mode passes a controllable clock. [riderZone] is where the rider is: it decides what "today" is.
 */
internal class TodayViewModel(
    private val sources: TodaySources,
    private val clock: Clock,
    private val riderZone: TimeZone,
    private val tick: Duration = 1.minutes,
) : ViewModel() {
    private val refresh = MutableStateFlow(RefreshStatus())

    val state: StateFlow<TodayUiState> =
        combine(sources.snapshot, now(), refresh) { snapshot, now, refresh ->
            todayState(snapshot, now, riderZone, refresh)
        }.onStart { refreshIfDue() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_SHARING_AFTER), TodayUiState())

    fun onIntent(intent: TodayIntent) {
        when (intent) {
            TodayIntent.Refresh -> refresh()
        }
    }

    /** Runs when the state is collected again, for example after the app was in the background. */
    private suspend fun refreshIfDue() {
        if (shouldRefresh(sources.snapshot.first(), refresh.value, clock.now())) refresh()
    }

    /** One refresh at a time; a pull while one runs joins it. */
    private fun refresh() {
        if (refresh.value.refreshing) return
        // Cache stamps are stored in milliseconds; a finer start would never count as reflected in them.
        val startedAt = Instant.fromEpochMilliseconds(clock.now().toEpochMilliseconds())
        refresh.update { it.copy(refreshing = true, startedAt = startedAt) }
        viewModelScope.launch {
            val result = sources.refresh()
            refresh.update { it.copy(refreshing = false, last = result) }
        }
    }

    /** The clock's time now and after every [tick]. Repeated readings are dropped, so a stopped clock costs nothing. */
    private fun now(): Flow<Instant> =
        flow {
            while (true) {
                emit(clock.now())
                delay(tick)
            }
        }.distinctUntilChanged()

    private companion object {
        /** Keeps the state alive across configuration changes without holding it in the background. */
        val STOP_SHARING_AFTER = 5.seconds.inWholeMilliseconds
    }
}
