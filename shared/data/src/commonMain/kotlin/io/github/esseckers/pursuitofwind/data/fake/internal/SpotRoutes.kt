package io.github.esseckers.pursuitofwind.data.fake.internal

import io.github.esseckers.pursuitofwind.contract.ApiLimits
import io.github.esseckers.pursuitofwind.contract.MySpotDto
import io.github.esseckers.pursuitofwind.contract.MySpotListDto
import io.github.esseckers.pursuitofwind.contract.MySpotUpdateDto
import io.github.esseckers.pursuitofwind.contract.SpotForecastDto
import io.github.esseckers.pursuitofwind.contract.SpotPageDto
import io.github.esseckers.pursuitofwind.data.internal.startOfHour
import kotlin.time.Duration.Companion.hours

/** Catalog, followed spots and forecasts. */
internal class SpotRoutes(
    private val state: FakeState,
    private val forecastSeed: Long,
) {
    fun catalog(call: FakeCall): FakeResponse {
        val limit = call.intQuery("limit", LIMITS, DEFAULT_LIMIT)
        val after =
            call.query["cursor"]?.let { cursor ->
                check422(cursor.startsWith(CURSOR_PREFIX)) { "cursor is not a cursor from this API" }
                cursor.removePrefix(CURSOR_PREFIX)
            }
        val remaining =
            state.spots.values
                .map { it.spot }
                .sortedBy { it.id }
                .filter { after == null || it.id > after }
        val page = remaining.take(limit)
        val next = if (remaining.size > limit) CURSOR_PREFIX + page.last().id else null
        return json(SpotPageDto.serializer(), SpotPageDto(page, next))
    }

    fun mySpots(call: FakeCall): FakeResponse {
        val items =
            state.followedSpots[call.userId]
                .orEmpty()
                .map { (spotId, settings) -> MySpotDto(state.spots.getValue(spotId).spot, settings.driveMinutes, settings.favorite) }
                .sortedBy { it.spot.id }
        return json(MySpotListDto.serializer(), MySpotListDto(items))
    }

    fun follow(call: FakeCall): FakeResponse {
        val spot = state.spots[call.pathParameter("spotId")]?.spot ?: throw notFound("Spot ${call.pathParameter("spotId")}")
        val update = call.decode(MySpotUpdateDto.serializer())
        Validation.requireDriveMinutes(update.driveMinutes)
        state.followedSpots.getOrPut(call.userId) { mutableMapOf() }[spot.id] = update
        return json(MySpotDto.serializer(), MySpotDto(spot, update.driveMinutes, update.favorite))
    }

    fun unfollow(call: FakeCall): FakeResponse {
        state.followedSpots[call.userId]?.remove(call.pathParameter("spotId"))
        return noContent()
    }

    fun forecast(call: FakeCall): FakeResponse {
        val spot = state.spots[call.pathParameter("spotId")] ?: throw notFound("Spot ${call.pathParameter("spotId")}")
        val from = call.instantQuery("from", call.now).startOfHour()
        val hours = call.intQuery("hours", HOURS, DEFAULT_HOURS)
        val horizon = call.now.startOfHour() + HORIZON
        val count = hours.coerceAtMost(((horizon - from).inWholeHours).toInt().coerceAtLeast(0))
        // Every request is a new model run, as on a live server, so a refresh always brings a slightly different forecast.
        val run = ModelRun(number = (state.forecastRuns[spot.spot.id]?.number ?: 0) + 1, at = call.now)
        state.forecastRuns[spot.spot.id] = run
        val forecast = SpotForecastDto(spot.spot.id, call.now, ForecastGenerator.hours(spot, from, count, forecastSeed, run))
        return json(SpotForecastDto.serializer(), forecast)
    }

    private companion object {
        const val CURSOR_PREFIX = "c_"
        const val DEFAULT_LIMIT = 50
        const val DEFAULT_HOURS = 72
        val LIMITS = 1..ApiLimits.Paging.LIMIT_MAX
        val HOURS = 1..ApiLimits.Forecast.HOURS_MAX

        /** Like real models, the fake forecasts ten days ahead. */
        val HORIZON = 240.hours
    }
}
