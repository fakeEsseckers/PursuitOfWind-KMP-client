package io.github.esseckers.pursuitofwind.data.repository

import androidx.room3.withWriteTransaction
import io.github.esseckers.pursuitofwind.data.internal.local.PursuitOfWindDatabase
import io.github.esseckers.pursuitofwind.data.internal.local.SyncKeys
import io.github.esseckers.pursuitofwind.data.internal.local.cached
import io.github.esseckers.pursuitofwind.data.internal.local.markUpdated
import io.github.esseckers.pursuitofwind.data.internal.mapping.toEntity
import io.github.esseckers.pursuitofwind.data.internal.mapping.toForecastHour
import io.github.esseckers.pursuitofwind.data.internal.network.PursuitOfWindApi
import io.github.esseckers.pursuitofwind.data.internal.network.outcome
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.Outcome
import io.github.esseckers.pursuitofwind.engine.model.ForecastHour
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Instant

/** Hourly forecasts per spot, as engine [ForecastHour]s, so callers pass them to the decision engine unchanged. */
public class ForecastRepository internal constructor(
    private val api: PursuitOfWindApi,
    private val database: PursuitOfWindDatabase,
    private val clock: Clock,
) {
    private val forecasts = database.forecasts()
    private val sync = database.syncState()

    /** The cached hours of [spotId], oldest first. */
    public fun forecast(spotId: SpotId): Flow<Cached<List<ForecastHour>>> =
        sync.cached(SyncKeys.forecast(spotId.value), forecasts.hours(spotId.value).map { list -> list.map { it.toForecastHour() } })

    /** Replaces the cached forecast of [spotId] with [hours] hours from the start of the current hour. */
    public suspend fun refresh(
        spotId: SpotId,
        hours: Int = DEFAULT_HOURS,
    ): Outcome<Unit> =
        outcome {
            val now = clock.now()
            val from = Instant.fromEpochSeconds(now.epochSeconds.floorDiv(SECONDS_PER_HOUR) * SECONDS_PER_HOUR)
            val forecast = api.forecast(spotId.value, from, hours)
            database.withWriteTransaction {
                forecasts.deleteSpot(spotId.value)
                forecasts.upsert(forecast.hours.map { it.toEntity(spotId.value) })
                sync.markUpdated(SyncKeys.forecast(spotId.value), now)
            }
        }

    public companion object {
        /** Today and the next two days, what the Today screen decides on. */
        public const val DEFAULT_HOURS: Int = 72
        private const val SECONDS_PER_HOUR = 3_600L
    }
}
