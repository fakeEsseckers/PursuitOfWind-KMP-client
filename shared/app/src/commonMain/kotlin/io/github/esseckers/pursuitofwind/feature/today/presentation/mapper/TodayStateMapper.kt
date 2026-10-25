package io.github.esseckers.pursuitofwind.feature.today.presentation.mapper

import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.error_no_forecast
import io.github.esseckers.pursuitofwind.core.ui.Freshness
import io.github.esseckers.pursuitofwind.core.ui.message
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.Profile
import io.github.esseckers.pursuitofwind.feature.today.domain.entity.RefreshResultEntity
import io.github.esseckers.pursuitofwind.feature.today.domain.entity.TodaySnapshotEntity
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.RefreshStatus
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayInputs
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayState
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayState.Content
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayState.Missing
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayState.Notice
import kotlinx.datetime.TimeZone
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Computes what Today shows from its [TodayInputs]; day labels and departures use the rider's zone. Cached data always
 * wins over an error: the error only replaces the content when there is nothing to show, otherwise it becomes a notice
 * above the cards.
 */
internal object TodayStateMapper {
    fun map(
        inputs: TodayInputs,
        riderZone: TimeZone,
    ): TodayState {
        val snapshot = inputs.snapshot
        val now = inputs.now
        val refresh = inputs.refresh
        return if (snapshot == null || now == null) {
            TodayState(content = Content.Loading, isRefreshing = refresh.refreshing, inputs = inputs)
        } else {
            map(snapshot, now, riderZone, refresh).copy(inputs = inputs)
        }
    }

    private fun map(
        snapshot: TodaySnapshotEntity,
        now: Instant,
        riderZone: TimeZone,
        refresh: RefreshStatus,
    ): TodayState {
        val refreshing = refresh.refreshing
        val waiting = waitingContent(snapshot, refresh)
        return when (val content = snapshot.content()) {
            SnapshotContent.NotFetched -> {
                TodayState(content = waiting, isRefreshing = refreshing)
            }

            is SnapshotContent.Missing -> {
                TodayState(content = Content.Empty(content.what), isRefreshing = refreshing)
            }

            is SnapshotContent.Complete -> {
                val days = DayCardsMapper(content.profile, content.spots, snapshot.crew, now, riderZone).map()
                val age = content.updatedAt?.let { (now - it).coerceAtLeast(Duration.ZERO) }
                if (days.isEmpty()) {
                    TodayState(content = waiting, isRefreshing = refreshing)
                } else {
                    TodayState(Content.Ready(days), refreshing, updatedAgo = age, notice = notice(refresh.last, age))
                }
            }
        }
    }

    /** What to show while there is nothing to decide on. */
    private fun waitingContent(
        snapshot: TodaySnapshotEntity,
        refresh: RefreshStatus,
    ): Content {
        val forecastError = refresh.last?.forecastError
        return when {
            refresh.refreshing || refresh.last == null -> Content.Loading

            forecastError != null -> Content.Error(forecastError.message())

            // The refresh worked, but the cache has not caught up with it yet.
            refresh.startedAt != null && !snapshot.reflects(refresh.startedAt) -> Content.Loading

            else -> Content.Error(Res.string.error_no_forecast)
        }
    }

    private fun notice(
        last: RefreshResultEntity?,
        age: Duration?,
    ): Notice? =
        when {
            last?.forecastError == AppError.Network -> Notice.OFFLINE
            last?.forecastError != null -> Notice.REFRESH_FAILED
            age != null && age > Freshness.STALE_AFTER -> Notice.STALE
            last?.crewError != null -> Notice.CREW_FAILED
            else -> null
        }
}

/**
 * True when the cache shows everything a refresh started at [startedAt] fetched. A write lands in Room and DataStore
 * before their flows emit it, so for a moment after a refresh ends the snapshot can still be the old one.
 */
private fun TodaySnapshotEntity.reflects(startedAt: Instant): Boolean {
    fun Cached<*>?.fresh() = this?.updatedAt?.let { it >= startedAt } == true
    return profile.fresh() && spots.fresh() && spots.value.filter { it.favorite }.all { forecasts[it.spot.id].fresh() }
}

/** What a snapshot holds, before any decision is made. */
private sealed interface SnapshotContent {
    /** The profile or the spots were never fetched, or no favorite has a forecast yet. */
    data object NotFetched : SnapshotContent

    data class Missing(
        val what: TodayState.Missing,
    ) : SnapshotContent

    /** [updatedAt] is the fetch time of the oldest forecast among [spots]. */
    data class Complete(
        val profile: Profile,
        val spots: List<SpotWithForecast>,
        val updatedAt: Instant?,
    ) : SnapshotContent
}

private fun TodaySnapshotEntity.content(): SnapshotContent {
    val favorites = spots.value.filter { it.favorite }
    val withForecast =
        favorites.mapNotNull { spot ->
            forecasts[spot.spot.id]?.takeIf { it.value.isNotEmpty() }?.let { spot to it }
        }
    val rider = profile.value
    return when {
        profile.updatedAt == null || spots.updatedAt == null -> {
            SnapshotContent.NotFetched
        }

        rider == null -> {
            SnapshotContent.Missing(Missing.PROFILE)
        }

        favorites.isEmpty() -> {
            SnapshotContent.Missing(Missing.FAVORITE_SPOTS)
        }

        withForecast.isEmpty() -> {
            SnapshotContent.NotFetched
        }

        else -> {
            SnapshotContent.Complete(
                profile = rider,
                spots = withForecast.map { (spot, forecast) -> SpotWithForecast(spot, forecast.value) },
                updatedAt = withForecast.mapNotNull { it.second.updatedAt }.minOrNull(),
            )
        }
    }
}
