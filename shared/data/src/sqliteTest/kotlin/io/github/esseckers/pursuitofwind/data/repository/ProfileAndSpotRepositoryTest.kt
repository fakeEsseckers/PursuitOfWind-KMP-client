package io.github.esseckers.pursuitofwind.data.repository

import io.github.esseckers.pursuitofwind.data.fake.NetworkMode
import io.github.esseckers.pursuitofwind.data.fake.THURSDAY_MORNING
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.errorOrNull
import io.github.esseckers.pursuitofwind.data.network.fakeServer
import io.github.esseckers.pursuitofwind.data.network.forwardingEngine
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class ProfileAndSpotRepositoryTest {
    @Test
    fun `a new rider has no profile until one is saved`() =
        runTest {
            world {
                val alex = device()

                val neverFetched = alex.profiles.profile.first()
                alex.profiles.refresh().value()
                val fetched = alex.profiles.profile.first()
                alex.profiles.save(ALEX).value()

                assertEquals(Cached(null, null), neverFetched)
                assertEquals(Cached(null, THURSDAY_MORNING), fetched)
                assertEquals(
                    ALEX,
                    alex.profiles.profile
                        .first()
                        .value,
                )
            }
        }

    @Test
    fun `offline the cached profile stays and the refresh reports Network`() =
        runTest {
            world {
                val alex = device()
                alex.profiles.save(ALEX).value()
                server.networkMode = NetworkMode.OFFLINE

                val result = alex.profiles.refresh()

                assertEquals(AppError.Network, result.errorOrNull())
                assertEquals(
                    ALEX,
                    alex.profiles.profile
                        .first()
                        .value,
                )
            }
        }

    @Test
    fun `the catalog is cached with its data quality`() =
        runTest {
            world {
                val alex = device()

                alex.spots.refreshCatalog().value()
                val catalog = alex.spots.catalog.first()

                assertEquals(
                    listOf("Dune Point", "Harbor Flats", "Lake Weiss", "Nordstrand Bay", "Salt Lagoon"),
                    catalog.value.map { it.name },
                )
                assertEquals(
                    setOf("harbor-flats", "lake-weiss"),
                    catalog.value
                        .filterNot { it.verified }
                        .map { it.id.value }
                        .toSet(),
                )
                assertEquals(THURSDAY_MORNING, catalog.updatedAt)
            }
        }

    @Test
    fun `followed spots carry the drive time the engine needs`() =
        runTest {
            world {
                val alex = device()

                alex.spots.follow(SpotId("nordstrand-bay"), drive = 120.minutes, favorite = true).value()
                alex.spots.follow(SpotId("lake-weiss"), drive = 45.minutes, favorite = false).value()
                alex.spots.unfollow(SpotId("lake-weiss")).value()
                alex.spots.refreshMySpots().value()
                val mine =
                    alex.spots.mySpots
                        .first()
                        .value

                assertEquals(listOf("nordstrand-bay"), mine.map { it.spot.id.value })
                assertEquals(120.minutes, mine.single().toSpotConfig().drive)
                assertTrue(mine.single().favorite)
            }
        }

    @Test
    fun `another user signing in drops what belonged to the previous one except the catalog`() =
        runTest {
            world {
                val alex = device()
                alex.spots.refreshCatalog().value()
                alex.spots.follow(SpotId("salt-lagoon"), drive = 160.minutes, favorite = true).value()
                alex.crews.join("WIND-4KQ7").value()
                // A new installation: the server creates a new user on the next call.
                alex.network.tokens.delete("installation_id")
                alex.network.tokens.delete("session")
                alex.network.clients.forgetTokens()

                alex.crews.refreshCrews().value()

                assertNotEquals("usr_0001", alex.account.userId.value)
                assertEquals(
                    emptyList(),
                    alex.spots.mySpots
                        .first()
                        .value,
                )
                assertEquals(
                    emptyList(),
                    alex.crews.crews
                        .first()
                        .value,
                )
                assertEquals(
                    5,
                    alex.spots.catalog
                        .first()
                        .value.size,
                )
                assertEquals(
                    null,
                    alex.profiles.profile
                        .first()
                        .updatedAt,
                )
            }
        }

    @Test
    fun `a server that forgot its users empties the cache even when it hands out the same id`() =
        runTest {
            val scope = this
            world {
                var current = server
                val alex = device(scope.forwardingEngine({ current }))
                alex.spots.follow(SpotId("salt-lagoon"), drive = 160.minutes, favorite = true).value()
                val before = alex.account.userId.value
                // The fake backend restarted: it knows no tokens and hands out the same ids again.
                current = scope.fakeServer(clock)

                alex.crews.refreshCrews().value()

                assertEquals(before, alex.account.userId.value)
                assertEquals(
                    emptyList(),
                    alex.spots.mySpots
                        .first()
                        .value,
                )
            }
        }
}
