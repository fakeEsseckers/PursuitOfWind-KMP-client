package io.github.esseckers.pursuitofwind.feature.spots.data.repository

import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.data.model.Outcome
import io.github.esseckers.pursuitofwind.data.model.errorOrNull
import io.github.esseckers.pursuitofwind.data.model.outcomeOf
import io.github.esseckers.pursuitofwind.data.model.valueOrNull
import io.github.esseckers.pursuitofwind.data.repository.AccountRepository
import io.github.esseckers.pursuitofwind.data.repository.SpotRepository
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spots.domain.entity.SpotSettingsEntity
import io.github.esseckers.pursuitofwind.feature.spots.domain.entity.SpotsSnapshotEntity
import io.github.esseckers.pursuitofwind.feature.spots.domain.repository.SpotsRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [SpotsRepository] over the shared [SpotRepository], which stays the single source of truth. One instance serves the
 * app, so its writes keep their order across screens and survive the screen that asked for them.
 */
internal class SpotsRepositoryImpl(
    private val shared: SpotRepository,
    account: AccountRepository,
) : SpotsRepository {
    /** One write at a time, in the order asked for: the lock is fair, and a write takes it before it suspends. */
    private val writes = Mutex()

    override val snapshot: Flow<SpotsSnapshotEntity> = combine(shared.mySpots, account.userId, ::SpotsSnapshotEntity)

    override suspend fun refresh(): AppError? = shared.refreshMySpots().errorOrNull()

    override suspend fun save(
        spotId: SpotId,
        wanted: () -> SpotSettingsEntity?,
    ): Outcome<Cached<List<MySpot>>?> =
        write {
            var sent: SpotSettingsEntity? = null
            var next = wanted()
            var error: AppError? = null
            var saved: Cached<List<MySpot>>? = null
            while (next != null && next != sent && error == null) {
                error = shared.follow(spotId, next.drive, next.favorite).errorOrNull()
                sent = next
                // Read before asking again, so a change made while reading is still sent.
                if (error == null) saved = current()
                next = wanted()
            }
            error?.let { Outcome.Failure(it) } ?: Outcome.Success(saved)
        }

    override suspend fun remove(spotId: SpotId): Outcome<Cached<List<MySpot>>?> =
        write {
            when (val removed = shared.unfollow(spotId)) {
                is Outcome.Success -> Outcome.Success(current())
                is Outcome.Failure -> removed
            }
        }

    override suspend fun restore(
        spotId: SpotId,
        settings: SpotSettingsEntity,
    ): Outcome<Cached<List<MySpot>>?> =
        write {
            val before = current()
            if (before?.value.orEmpty().any { it.spot.id == spotId }) {
                Outcome.Success(before)
            } else {
                val error = shared.follow(spotId, settings.drive, settings.favorite).errorOrNull()
                error?.let { Outcome.Failure(it) } ?: Outcome.Success(current())
            }
        }

    /** Runs [block] after the writes asked for before it, and to its end even when the caller is cancelled. */
    private suspend fun <T> write(block: suspend () -> T): T = withContext(NonCancellable) { writes.withLock { block() } }

    /**
     * The followed spots as cached right now, so read after a write they show it. Null when the cache cannot be read (the
     * data layer logs why): the tab goes on with what the cache emits.
     */
    private suspend fun current(): Cached<List<MySpot>>? = outcomeOf { shared.mySpots.first() }.valueOrNull()
}
