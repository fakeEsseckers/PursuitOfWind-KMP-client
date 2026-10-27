package io.github.esseckers.pursuitofwind.feature.today.presentation.mapper

import io.github.esseckers.pursuitofwind.core.refresh.RefreshNoticeMapper
import io.github.esseckers.pursuitofwind.core.refresh.RefreshStatus
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.error_no_forecast
import io.github.esseckers.pursuitofwind.core.ui.message
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.Profile
import io.github.esseckers.pursuitofwind.feature.today.domain.entity.SpotForecastEntity
import io.github.esseckers.pursuitofwind.feature.today.domain.entity.TodaySnapshotEntity
import io.github.esseckers.pursuitofwind.feature.today.domain.mapper.TodayEvaluationMapper
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayInputs
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayState
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayState.Content
import io.github.esseckers.pursuitofwind.feature.today.presentation.model.TodayState.Missing
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
        return if (snapshot == null || now == null) {
            TodayState(content = Content.Loading, isRefreshing = inputs.refresh.refreshing, inputs = inputs)
        } else {
            map(snapshot, now, riderZone, inputs).copy(inputs = inputs)
        }
    }

    private fun map(
        snapshot: TodaySnapshotEntity,
        now: Instant,
        riderZone: TimeZone,
        inputs: TodayInputs,
    ): TodayState {
        val refresh = inputs.refresh
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
                val evaluated = TodayEvaluationMapper.evaluate(content.profile, content.spots, now, riderZone)
                val days = DayCardsMapper(content.profile, content.spots, snapshot.crew, now, riderZone).map(evaluated)
                val age = content.updatedAt?.let { (now - it).coerceAtLeast(Duration.ZERO) }
                if (days.isEmpty()) {
                    TodayState(content = waiting, isRefreshing = refreshing)
                } else {
                    val notice = RefreshNoticeMapper.notice(refresh.error, age)
                    TodayState(
                        content = Content.Ready(days),
                        isRefreshing = refreshing,
                        updatedAgo = age,
                        notice = notice,
                        crewFailed = notice == null && inputs.crewError != null,
                    )
                }
            }
        }
    }

    /** What to show while there is nothing to decide on. */
    private fun waitingContent(
        snapshot: TodaySnapshotEntity,
        refresh: RefreshStatus,
    ): Content {
        val forecastError = refresh.error
        return when {
            refresh.refreshing || !refresh.finished -> Content.Loading

            forecastError != null -> Content.Error(forecastError.message())

            // The refresh worked, but the cache has not caught up with it yet.
            refresh.startedAt != null && !snapshot.reflects(refresh.startedAt) -> Content.Loading

            else -> Content.Error(Res.string.error_no_forecast)
        }
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
        val spots: List<SpotForecastEntity>,
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
                spots = withForecast.map { (spot, forecast) -> SpotForecastEntity(spot, forecast.value) },
                updatedAt = withForecast.mapNotNull { it.second.updatedAt }.minOrNull(),
            )
        }
    }
}
