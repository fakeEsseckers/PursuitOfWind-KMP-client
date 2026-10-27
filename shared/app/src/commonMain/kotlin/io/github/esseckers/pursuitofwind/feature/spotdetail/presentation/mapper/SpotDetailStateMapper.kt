package io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.mapper

import io.github.esseckers.pursuitofwind.core.refresh.RefreshNoticeMapper
import io.github.esseckers.pursuitofwind.core.refresh.RefreshStatus
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.spot_no_forecast
import io.github.esseckers.pursuitofwind.core.ui.message
import io.github.esseckers.pursuitofwind.feature.spotdetail.domain.entity.SpotDetailSnapshotEntity
import io.github.esseckers.pursuitofwind.feature.spotdetail.domain.entity.SpotEvaluationEntity
import io.github.esseckers.pursuitofwind.feature.spotdetail.domain.mapper.SpotEvaluationMapper
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.Selection
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.SpotDetailInputs
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.SpotDetailState
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.SpotDetailState.Content
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.SpotDetailState.Missing
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.SpotHeaderUi
import kotlinx.datetime.TimeZone
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Computes what the spot detail screen shows from its [SpotDetailInputs]. As on Today, cached data always wins over an
 * error: the error replaces the chart only when there is nothing to show, otherwise it becomes a notice above it.
 */
internal object SpotDetailStateMapper {
    fun map(
        inputs: SpotDetailInputs,
        riderZone: TimeZone,
    ): SpotDetailState {
        val snapshot = inputs.snapshot
        val now = inputs.now
        val loading = SpotDetailState(inputs = inputs, isRefreshing = inputs.refresh.refreshing)
        if (snapshot == null || now == null) return loading
        val spot = snapshot.spot.value
        val profile = snapshot.profile.value
        val header = spot?.let { SpotHeaderUi(it.spot.name, it.drive) }
        return when {
            spot == null -> {
                loading.copy(content = Content.Missing(Missing.SPOT))
            }

            profile == null -> {
                loading.copy(content = Content.Missing(Missing.PROFILE), header = header)
            }

            else -> {
                val evaluation = SpotEvaluationMapper.evaluate(profile, spot, snapshot.forecast.value, now)
                val forecast = SpotForecastMapper(profile, evaluation, now, riderZone)
                if (forecast.isEmpty) {
                    loading.copy(content = waitingContent(snapshot, inputs.refresh), header = header)
                } else {
                    val age = snapshot.forecast.updatedAt?.let { (now - it).coerceAtLeast(Duration.ZERO) }
                    loading.copy(
                        content = ready(forecast, inputs),
                        header = header,
                        updatedAgo = age,
                        notice = RefreshNoticeMapper.notice(inputs.refresh.error, age),
                        evaluation = evaluation,
                    )
                }
            }
        }
    }

    /**
     * [state] with the hour [selection] points to. Only the selection changed, so the engine's answer and the chart stay
     * and only that hour is worked out; without a chart on screen, everything is computed as by [map].
     */
    fun select(
        state: SpotDetailState,
        selection: Selection,
        riderZone: TimeZone,
    ): SpotDetailState {
        val inputs = state.inputs.copy(selection = selection)
        val content = state.content as? Content.Ready
        val forecast = state.evaluation?.let { forecastOf(inputs, it, riderZone) }
        if (content == null || forecast == null) return map(inputs, riderZone)
        return state.copy(inputs = inputs, content = content.copy(selected = forecast.details(forecast.indexOf(selection))))
    }

    /** The chart's forecast for [inputs] from an [evaluation] already made; null without a profile or a time. */
    private fun forecastOf(
        inputs: SpotDetailInputs,
        evaluation: SpotEvaluationEntity,
        riderZone: TimeZone,
    ): SpotForecastMapper? {
        val profile = inputs.snapshot?.profile?.value
        val now = inputs.now
        return if (profile != null && now != null) SpotForecastMapper(profile, evaluation, now, riderZone) else null
    }

    private fun ready(
        forecast: SpotForecastMapper,
        inputs: SpotDetailInputs,
    ): Content.Ready =
        Content.Ready(
            chart = forecast.chart(),
            days = forecast.days(),
            windows = forecast.windows(),
            selected = forecast.details(forecast.indexOf(inputs.selection)),
            nowOffset = forecast.nowOffset,
        )

    /** What to show while there is no hour to show. */
    private fun waitingContent(
        snapshot: SpotDetailSnapshotEntity,
        refresh: RefreshStatus,
    ): Content {
        val error = refresh.error
        val startedAt = refresh.startedAt
        return when {
            refresh.refreshing || !refresh.finished -> Content.Loading

            error != null -> Content.Error(error.message())

            // The refresh worked, but the cache has not caught up with it yet.
            startedAt != null && !snapshot.reflects(startedAt) -> Content.Loading

            else -> Content.Error(Res.string.spot_no_forecast)
        }
    }

    /** True when the cached forecast was written by a refresh started at [startedAt] or later. */
    private fun SpotDetailSnapshotEntity.reflects(startedAt: Instant): Boolean = forecast.updatedAt?.let { it >= startedAt } == true
}
