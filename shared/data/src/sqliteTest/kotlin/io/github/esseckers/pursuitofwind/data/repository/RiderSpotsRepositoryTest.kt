package io.github.esseckers.pursuitofwind.data.repository

import io.github.esseckers.pursuitofwind.contract.ProblemCode
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.GeoPoint
import io.github.esseckers.pursuitofwind.data.model.Outcome
import io.github.esseckers.pursuitofwind.data.model.Spot
import io.github.esseckers.pursuitofwind.data.model.errorOrNull
import io.github.esseckers.pursuitofwind.data.network.forwardingEngine
import io.github.esseckers.pursuitofwind.engine.DecisionEngine
import io.github.esseckers.pursuitofwind.engine.model.Bearing
import io.github.esseckers.pursuitofwind.engine.model.ShoreSectors
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

/** Spots riders add to the catalog: everyone sees them, and only the rider who added one can change it. */
class RiderSpotsRepositoryTest {
    @Test
    fun `a spot a rider adds is in everyone's catalog with the sectors of its water`() =
        runTest {
            world {
                val alex = device()
                val jonas = device()

                val added = alex.addSandbank()
                jonas.spots.refreshCatalog().value()
                val seen =
                    jonas.spots.catalog
                        .first()
                        .value
                        .single { it.id == added.id }
                val cached =
                    alex.spots.catalog
                        .first()
                        .value
                        .single { it.id == added.id }

                assertEquals(added, seen)
                assertEquals(added, cached)
                assertEquals(alex.account.userId.value, added.createdBy)
                assertEquals(clock.now(), added.createdAt)
                assertEquals(Bearing(WEST_SOUTH_WEST), added.water)
                assertEquals(
                    DecisionEngine.sectorsFacing(added.water),
                    ShoreSectors(added.rideableSectors.single(), added.dangerSectors.single()),
                )
            }
        }

    @Test
    fun `only the rider who added a spot can change it`() =
        runTest {
            world {
                val alex = device()
                val jonas = device()
                val added = alex.addSandbank()
                clock.advanceBy(3.minutes)

                val moved = alex.spots.changeSpot(added.id, "Sandbank South", GeoPoint(54.6, 8.35), Bearing(SOUTH_WEST)).value()
                val refused = jonas.spots.changeSpot(added.id, "Mine now", added.location, added.water)

                assertEquals("Sandbank South", moved.name)
                assertEquals(added.createdAt, moved.createdAt)
                assertEquals(DecisionEngine.sectorsFacing(Bearing(SOUTH_WEST)).rideable, moved.rideableSectors.single())
                assertEquals(AppError.Rejected(403, ProblemCode.NOT_THE_CREATOR), refused.errorOrNull())
            }
        }

    @Test
    fun `a catalog refresh answered before a change cannot undo it`() =
        runTest {
            val scope = this
            world {
                val held = Held(path = "/v1/spots/catalog")
                val alex = device(scope.forwardingEngine({ server }, held.intercept))
                val added = alex.addSandbank()

                val cached =
                    scope.raceChange(held, added, { alex.renameToSouth(it) }, refresh = { alex.spots.refreshCatalog() }) {
                        alex.spots.catalog
                            .first()
                            .value
                            .single { it.id == added.id }
                    }

                assertEquals("Sandbank South", cached.name)
            }
        }

    @Test
    fun `a refresh of the followed spots answered before a change cannot undo it`() =
        runTest {
            val scope = this
            world {
                val held = Held(path = "/v1/me/spots")
                val alex = device(scope.forwardingEngine({ server }, held.intercept))
                val added = alex.addSandbank()
                alex.spots.follow(added.id, drive = 30.minutes, favorite = true).value()

                val cached =
                    scope.raceChange(held, added, { alex.renameToSouth(it) }, refresh = { alex.spots.refreshMySpots() }) {
                        alex.spots.mySpots
                            .first()
                            .value
                            .single { it.spot.id == added.id }
                            .spot
                    }

                assertEquals("Sandbank South", cached.name)
            }
        }

    /**
     * Starts [refresh], lets the server answer it, and keeps the answer from the device while the rider renames [spot]
     * to "Sandbank South"; then lets it through and gives what [read] finds once both are done.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun <T> TestScope.raceChange(
        held: Held,
        spot: Spot,
        change: suspend (Spot) -> Outcome<Spot>,
        refresh: suspend () -> Outcome<Unit>,
        read: suspend () -> T,
    ): T {
        held.holding = true
        val refreshing = async { refresh() }
        runCurrent()
        val changing = async { change(spot) }
        runCurrent()
        held.release()
        refreshing.await().value()
        changing.await().value()
        return read()
    }

    private suspend fun DataHarness.renameToSouth(spot: Spot): Outcome<Spot> =
        spots.changeSpot(spot.id, "Sandbank South", spot.location, spot.water)

    private suspend fun DataHarness.addSandbank() =
        spots.addSpot("Sandbank North", GeoPoint(54.6102, 8.3569), Bearing(WEST_SOUTH_WEST), TimeZone.of("Europe/Berlin")).value()

    private companion object {
        const val WEST_SOUTH_WEST = 250.0
        const val SOUTH_WEST = 225.0
    }
}

/** Keeps the server's answers to `GET` [path] from the device while [holding], until [release]. */
private class Held(
    private val path: String,
) {
    var holding = false
    private val gate = CompletableDeferred<Unit>()

    /** For [forwardingEngine]: passes every request on, and holds the answers to [path] while [holding]. */
    val intercept: suspend MockRequestHandleScope.(HttpRequestData, suspend () -> HttpResponseData) -> HttpResponseData =
        { request, forward ->
            val answer = forward()
            if (holding && request.method == HttpMethod.Get && request.url.encodedPath == path) gate.await()
            answer
        }

    fun release() {
        gate.complete(Unit)
    }
}
