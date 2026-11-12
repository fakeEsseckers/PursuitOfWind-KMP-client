package io.github.esseckers.pursuitofwind.feature.alerts.data.repository

import co.touchlab.kermit.Logger
import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.data.model.NotifiedWindow
import io.github.esseckers.pursuitofwind.data.model.Outcome
import io.github.esseckers.pursuitofwind.data.model.Profile
import io.github.esseckers.pursuitofwind.data.model.errorOrNull
import io.github.esseckers.pursuitofwind.data.model.outcomeOf
import io.github.esseckers.pursuitofwind.data.model.valueOrNull
import io.github.esseckers.pursuitofwind.data.repository.ForecastRepository
import io.github.esseckers.pursuitofwind.data.repository.ProfileRepository
import io.github.esseckers.pursuitofwind.data.repository.SpotRepository
import io.github.esseckers.pursuitofwind.data.repository.WindowAlertsRepository
import io.github.esseckers.pursuitofwind.engine.model.SpotForecast
import io.github.esseckers.pursuitofwind.feature.alerts.domain.entity.AlertsSnapshotEntity
import io.github.esseckers.pursuitofwind.feature.alerts.domain.repository.AlertsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.retryWhen
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * [AlertsRepository] over the app's shared repositories. The profile and the followed spots come from the cache: only
 * this device changes them, and it caches what it saves. Only the forecasts are fetched, which keeps a check small.
 */
internal class AlertsRepositoryImpl(
    private val profiles: ProfileRepository,
    private val spots: SpotRepository,
    private val forecasts: ForecastRepository,
    private val alerts: WindowAlertsRepository,
) : AlertsRepository {
    override val choice: Flow<Boolean> =
        alerts.enabled
            .retryWhen { failure, attempt ->
                Logger.withTag("Alerts").e(failure) { "Reading the alerts choice failed" }
                delay(retryDelay(attempt))
                true
            }.distinctUntilChanged()

    override suspend fun enabled(): Boolean = outcomeOf { alerts.enabled.first() }.valueOrNull() ?: false

    override suspend fun fetch(): Outcome<AlertsSnapshotEntity?> =
        when (val cached = outcomeOf { cached() }) {
            is Outcome.Failure -> {
                cached
            }

            is Outcome.Success -> {
                cached.value?.let { (profile, favorites) -> outcomeOf { fetch(profile, favorites) } }
                    ?: Outcome.Success(null)
            }
        }

    override suspend fun notified(): Outcome<List<NotifiedWindow>> = alerts.notified()

    override suspend fun saveNotified(windows: List<NotifiedWindow>): Outcome<Unit> = alerts.saveNotified(windows)

    /** The cached profile and favorite spots, or null when either is missing. */
    private suspend fun cached(): Pair<Profile, List<MySpot>>? {
        val profile = profiles.profile.first().value
        val favorites =
            spots.mySpots
                .first()
                .value
                .filter { it.favorite }
        return if (profile == null || favorites.isEmpty()) null else profile to favorites
    }

    /**
     * Fetches every favorite's forecast at the same time. One that fails (a spot the server no longer has, a server
     * error) costs the rider that spot only, not the alerts for all the others.
     */
    private suspend fun fetch(
        profile: Profile,
        favorites: List<MySpot>,
    ): AlertsSnapshotEntity {
        val refreshed = coroutineScope { favorites.map { spot -> async { spot to forecasts.refresh(spot.spot.id) } }.awaitAll() }
        val fetched = refreshed.filter { (_, outcome) -> outcome is Outcome.Success }.map { (spot, _) -> spot }
        return AlertsSnapshotEntity(
            profile = profile,
            forecasts = fetched.map { SpotForecast(it.toSpotConfig(), hoursOf(it)) },
            failures = refreshed.mapNotNull { (_, outcome) -> outcome.errorOrNull() },
        )
    }

    private suspend fun hoursOf(spot: MySpot) = forecasts.forecast(spot.spot.id).first().value

    private companion object {
        const val MAX_DOUBLINGS = 6L

        /** One second, doubling up to about a minute: a settings file that fails now may read a moment later. */
        fun retryDelay(attempt: Long): Duration = (1 shl attempt.coerceAtMost(MAX_DOUBLINGS).toInt()).seconds
    }
}
