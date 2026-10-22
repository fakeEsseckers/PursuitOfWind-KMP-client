package io.github.esseckers.pursuitofwind.ui.today

import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.data.model.Profile
import io.github.esseckers.pursuitofwind.engine.model.ForecastHour
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.ui.common.message
import io.github.esseckers.pursuitofwind.ui.generated.resources.Res
import io.github.esseckers.pursuitofwind.ui.generated.resources.error_no_forecast
import io.github.esseckers.pursuitofwind.ui.today.TodayUiState.Content
import io.github.esseckers.pursuitofwind.ui.today.TodayUiState.Missing
import io.github.esseckers.pursuitofwind.ui.today.TodayUiState.Notice
import kotlinx.datetime.TimeZone
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** A forecast older than this gets the "getting old" notice; the server updates forecasts every hour. */
internal val STALE_AFTER: Duration = 3.hours

/** Coming back to the screen refreshes it when the last refresh is older than this. */
internal val REFRESH_AFTER: Duration = 30.minutes

/** Everything Today is computed from, as cached right now. */
internal data class TodaySnapshot(
    val profile: Cached<Profile?>,
    val spots: Cached<List<MySpot>>,
    val forecasts: Map<SpotId, Cached<List<ForecastHour>>>,
    val crew: CrewRides,
)

/**
 * How the last refresh went. Forecast, spot and profile problems affect the answers; crew problems only the rides, so
 * they are kept apart.
 */
internal data class RefreshResult(
    val forecastError: AppError? = null,
    val crewError: AppError? = null,
)

/**
 * Where the refresh is: [refreshing] now, and how the last one ended ([last] is null before the first one ends).
 * [startedAt] is when the latest refresh started, so the state can tell whether the cache already shows it.
 */
internal data class RefreshStatus(
    val refreshing: Boolean = false,
    val last: RefreshResult? = null,
    val startedAt: Instant? = null,
)

/** The error to show for [errors]: a connection problem first, since it explains the others. */
internal fun mainError(errors: List<AppError>): AppError? = errors.firstOrNull { it == AppError.Network } ?: errors.firstOrNull()

/**
 * True when the screen should refresh as it (re)appears: never refreshed, the last refresh failed, is older than
 * [REFRESH_AFTER] or later than [now], or a favorite spot has no cached forecast yet.
 */
internal fun shouldRefresh(
    snapshot: TodaySnapshot,
    refresh: RefreshStatus,
    now: Instant,
): Boolean {
    val startedAt = refresh.startedAt
    return startedAt == null ||
        refresh.last?.forecastError != null ||
        // A clock set back (demo time travel) makes the last refresh look like it is from the future.
        now < startedAt ||
        now - startedAt >= REFRESH_AFTER ||
        snapshot.spots.value.any { it.favorite && snapshot.forecasts[it.spot.id]?.value.isNullOrEmpty() }
}

/**
 * True when the cache shows everything a refresh started at [startedAt] fetched. A write lands in Room and DataStore
 * before their flows emit it, so for a moment after a refresh ends the snapshot can still be the old one.
 */
private fun TodaySnapshot.reflects(startedAt: Instant): Boolean {
    fun Cached<*>?.fresh() = this?.updatedAt?.let { it >= startedAt } == true
    return profile.fresh() && spots.fresh() && spots.value.filter { it.favorite }.all { forecasts[it.spot.id].fresh() }
}

/**
 * The screen state for [snapshot] at [now]; day labels and departures use [riderZone]. Cached data always wins over an
 * error: the error only replaces the content when there is nothing to show, otherwise it becomes a notice above the
 * cards.
 */
internal fun todayState(
    snapshot: TodaySnapshot,
    now: Instant,
    riderZone: TimeZone,
    refresh: RefreshStatus,
): TodayUiState {
    val refreshing = refresh.refreshing
    val forecastError = refresh.last?.forecastError
    val waiting =
        when {
            refreshing || refresh.last == null -> Content.Loading

            forecastError != null -> Content.Error(forecastError.message())

            // The refresh worked, but the cache has not caught up with it yet.
            refresh.startedAt != null && !snapshot.reflects(refresh.startedAt) -> Content.Loading

            else -> Content.Error(Res.string.error_no_forecast)
        }
    return when (val inputs = snapshot.inputs()) {
        Inputs.NotFetched -> {
            TodayUiState(content = waiting, isRefreshing = refreshing)
        }

        is Inputs.Missing -> {
            TodayUiState(content = Content.Empty(inputs.what), isRefreshing = refreshing)
        }

        is Inputs.Complete -> {
            val days = DayCards(inputs.profile, inputs.spots, snapshot.crew, now, riderZone).build()
            val age = inputs.updatedAt?.let { (now - it).coerceAtLeast(Duration.ZERO) }
            if (days.isEmpty()) {
                TodayUiState(content = waiting, isRefreshing = refreshing)
            } else {
                TodayUiState(Content.Ready(days), refreshing, updatedAgo = age, notice = notice(refresh.last, age))
            }
        }
    }
}

private fun notice(
    last: RefreshResult?,
    age: Duration?,
): Notice? =
    when {
        last?.forecastError == AppError.Network -> Notice.OFFLINE
        last?.forecastError != null -> Notice.REFRESH_FAILED
        age != null && age > STALE_AFTER -> Notice.STALE
        last?.crewError != null -> Notice.CREW_FAILED
        else -> null
    }

private sealed interface Inputs {
    /** The profile or the spots were never fetched: wait for the first refresh. */
    data object NotFetched : Inputs

    data class Missing(
        val what: TodayUiState.Missing,
    ) : Inputs

    /** [updatedAt] is the fetch time of the oldest forecast among [spots]. */
    data class Complete(
        val profile: Profile,
        val spots: List<SpotWithForecast>,
        val updatedAt: Instant?,
    ) : Inputs
}

private fun TodaySnapshot.inputs(): Inputs {
    val favorites = spots.value.filter { it.favorite }
    val withForecast =
        favorites.mapNotNull { spot ->
            forecasts[spot.spot.id]?.takeIf { it.value.isNotEmpty() }?.let { spot to it }
        }
    val rider = profile.value
    return when {
        profile.updatedAt == null || spots.updatedAt == null -> {
            Inputs.NotFetched
        }

        rider == null -> {
            Inputs.Missing(Missing.PROFILE)
        }

        favorites.isEmpty() -> {
            Inputs.Missing(Missing.FAVORITE_SPOTS)
        }

        withForecast.isEmpty() -> {
            Inputs.NotFetched
        }

        else -> {
            Inputs.Complete(
                profile = rider,
                spots = withForecast.map { (spot, forecast) -> SpotWithForecast(spot, forecast.value) },
                updatedAt = withForecast.mapNotNull { it.second.updatedAt }.minOrNull(),
            )
        }
    }
}
