package io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.view

import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.GeoPoint
import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.data.model.Profile
import io.github.esseckers.pursuitofwind.data.model.Spot
import io.github.esseckers.pursuitofwind.data.model.WindUnit
import io.github.esseckers.pursuitofwind.engine.model.Bearing
import io.github.esseckers.pursuitofwind.engine.model.ForecastHour
import io.github.esseckers.pursuitofwind.engine.model.ModelId
import io.github.esseckers.pursuitofwind.engine.model.ModelWind
import io.github.esseckers.pursuitofwind.engine.model.RiderProfile
import io.github.esseckers.pursuitofwind.engine.model.Sector
import io.github.esseckers.pursuitofwind.engine.model.SkillLevel
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.engine.model.ThunderRisk
import io.github.esseckers.pursuitofwind.engine.model.WindSpeed
import io.github.esseckers.pursuitofwind.feature.spotdetail.domain.model.SpotDetailSnapshot
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.RefreshStatus
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.Selection
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.SpotDetailInputs
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.SpotDetailState
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.viewmodel.SpotDetailReducer
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.viewmodel.SpotDetailResult
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * States of the spot detail screen for previews and UI tests: the sample rider (Alex, 80 kg, kites 7 / 9 / 12 m²) at
 * Nordstrand Bay from Saturday 08:00. Saturday builds to 18–22 kn from the southwest between 13:00 and 17:00, Sunday is
 * windy but gusty, Monday turns offshore. The models disagree a little everywhere, so the band shows. The states are
 * computed by the real mapper and engine, so they are what the app would show for this forecast.
 */
internal object SpotDetailPreviewData {
    val zone: TimeZone = TimeZone.of("Europe/Berlin")
    val saturday8am: Instant = LocalDateTime(2026, 10, 24, 8, 0).toInstant(zone)
    val spotId = SpotId("nordstrand-bay")

    private val profile = Profile("Alex", RiderProfile(80.0, SkillLevel.INTERMEDIATE, listOf(7.0, 9.0, 12.0)), WindUnit.KNOTS)

    val hours: List<ForecastHour> = (0 until 96).map { forecastHour(saturday8am + it.hours) }

    val ready: SpotDetailState = state()
    val unverified: SpotDetailState = state(spot = spot(verified = false))
    val offline: SpotDetailState = state(refresh = RefreshStatus(finished = true, error = AppError.Network, startedAt = saturday8am))
    val loading: SpotDetailState = state(hours = emptyList(), refresh = RefreshStatus(refreshing = true, startedAt = saturday8am))
    val error: SpotDetailState = state(hours = emptyList(), refresh = RefreshStatus(finished = true, error = AppError.Network))
    val notFollowed: SpotDetailState = state(spot = null)

    fun spot(verified: Boolean = true): MySpot =
        MySpot(
            spot = Spot(spotId, "Nordstrand Bay", GeoPoint(54.5, 8.8), zone, listOf(Sector.of(180.0, 300.0)), emptyList(), verified),
            drive = 2.hours,
            favorite = true,
        )

    fun state(
        spot: MySpot? = spot(),
        hours: List<ForecastHour> = this.hours,
        refresh: RefreshStatus = RefreshStatus(finished = true, startedAt = saturday8am - 5.minutes),
        now: Instant = saturday8am + 20.minutes,
        selection: Selection = Selection.First,
    ): SpotDetailState {
        val fetched = saturday8am - 5.minutes
        val snapshot = SpotDetailSnapshot(Cached(profile, fetched), Cached(spot, fetched), Cached(hours, fetched))
        // Through the real reducer, as the screen gets it: the selection result maps these inputs.
        val inputs = SpotDetailInputs(spotId, selection, snapshot, now, refresh)
        return SpotDetailReducer(zone).reduce(SpotDetailState(inputs = inputs), SpotDetailResult.Selected(selection))
    }

    private fun forecastHour(start: Instant): ForecastHour {
        val local = start.toLocalDateTime(zone)
        val day = (start - saturday8am).inWholeDays.toInt()
        val (meanKn, gustKn, bearing) =
            when (day) {
                0 -> Triple(saturdayWind(local.hour), saturdayWind(local.hour) + 5.0, 235.0 + local.hour)
                1 -> Triple(17.0, 31.0, 260.0)
                else -> Triple(if (local.hour in 6..18) 16.0 else 9.0, 21.0, 95.0)
            }
        val model = { offset: Double -> ModelWind(WindSpeed.knots(meanKn + offset), WindSpeed.knots(gustKn + offset), Bearing(bearing)) }
        return ForecastHour(
            start = start,
            byModel = mapOf(ModelId.ECMWF to model(0.0), ModelId.ICON to model(-1.5), ModelId.GFS to model(2.0)),
            precipMm = 0.0,
            tempC = 14.0,
            thunderRisk = ThunderRisk.LOW,
            isDaylight = local.hour in 8..16,
        )
    }

    private fun saturdayWind(hour: Int): Double =
        when (hour) {
            in 13..16 -> 20.0 + (hour - 13)
            12, 17 -> 15.0
            else -> 9.0
        }
}
