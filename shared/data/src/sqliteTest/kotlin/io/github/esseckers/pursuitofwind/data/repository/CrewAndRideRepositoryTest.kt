package io.github.esseckers.pursuitofwind.data.repository

import io.github.esseckers.pursuitofwind.contract.ProblemCode
import io.github.esseckers.pursuitofwind.data.fake.NetworkMode
import io.github.esseckers.pursuitofwind.data.fake.THURSDAY_MORNING
import io.github.esseckers.pursuitofwind.data.internal.local.SyncKeys
import io.github.esseckers.pursuitofwind.data.internal.local.SyncStateEntity
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Ride
import io.github.esseckers.pursuitofwind.data.model.RidePlan
import io.github.esseckers.pursuitofwind.data.model.RideStatus
import io.github.esseckers.pursuitofwind.data.model.errorOrNull
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

class CrewAndRideRepositoryTest {
    private suspend fun DataHarness.ride(
        crewId: String,
        rideId: String,
    ): Ride =
        rides
            .rides(crewId)
            .first()
            .value
            .single { it.id == rideId }

    private fun plan(seats: Int) =
        RidePlan(
            spotId = SpotId("salt-lagoon"),
            windowStart = THURSDAY_MORNING + 2.days + 5.hours,
            windowEnd = THURSDAY_MORNING + 2.days + 9.hours,
            departAt = THURSDAY_MORNING + 2.days + 2.hours,
            meetingPoint = "Station",
            seatsTotal = seats,
        )

    @Test
    fun `joining and the first sync load the crew with its members and rides`() =
        runTest {
            world {
                val alex = device()

                val crew = alex.crews.join("wind-4kq7").value()
                alex.crews.sync(crew.id).value()

                assertEquals(
                    listOf("Weekend Crew"),
                    alex.crews.crews
                        .first()
                        .value
                        .map { it.name },
                )
                assertEquals(
                    4,
                    alex.crews
                        .members(crew.id)
                        .first()
                        .value.size,
                )
                val ride = alex.ride(crew.id, "rid_7f3c")
                assertEquals(2, ride.seatsFree)
                assertEquals(listOf("Mira"), ride.passengers.map { it.displayName })
                assertEquals(
                    THURSDAY_MORNING,
                    alex.rides
                        .rides(crew.id)
                        .first()
                        .updatedAt,
                )
            }
        }

    @Test
    fun `later syncs apply what other members did from the feed`() =
        runTest {
            world {
                val alex = device()
                val jonas = device()
                alex.crews.join("WIND-4KQ7").value()
                alex.crews.sync("crw_weekend").value()
                jonas.profiles.save(ALEX.copy(displayName = "Jo")).value()
                jonas.crews.join("WIND-4KQ7").value()
                jonas.rides.takeSeat("rid_7f3c").value()
                val offered = jonas.rides.offer("crw_weekend", plan(seats = 3)).value()
                val server = jonas.network.api.ride("rid_7f3c")

                alex.crews.sync("crw_weekend").value()

                val seeded = alex.ride("crw_weekend", "rid_7f3c")
                assertEquals(server.version, seeded.version)
                assertEquals(1, seeded.seatsFree)
                assertEquals(listOf("Mira", "Jo"), seeded.passengers.map { it.displayName })
                assertEquals(offered, alex.ride("crw_weekend", offered.id))
                assertEquals(
                    5,
                    alex.crews
                        .members("crw_weekend")
                        .first()
                        .value.size,
                )
                assertEquals(
                    5,
                    alex.crews.crews
                        .first()
                        .value
                        .single()
                        .memberCount,
                )
            }
        }

    @Test
    fun `a feed the server reset is loaded again from scratch`() =
        runTest {
            world {
                val alex = device()
                alex.crews.join("WIND-4KQ7").value()
                alex.crews.sync("crw_weekend").value()
                alex.database.syncState().upsert(SyncStateEntity(SyncKeys.feed("crw_weekend"), 0, "evt_from_before_a_reset"))

                val result = alex.crews.sync("crw_weekend")

                assertEquals(Unit, result.value())
                assertTrue(
                    alex.database
                        .syncState()
                        .get(SyncKeys.feed("crw_weekend"))
                        ?.cursor
                        ?.startsWith("evt_0") == true,
                )
                assertEquals(2, alex.ride("crw_weekend", "rid_7f3c").seatsFree)
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a crew created during a refresh of the list is sent after it so the refresh cannot drop it`() =
        runTest {
            world {
                val alex = device()
                alex.crews.refreshCrews().value()
                server.networkMode = NetworkMode.SLOW
                val start = currentTime

                val refreshing = async { alex.crews.refreshCrews() }
                runCurrent()
                val creating = async { alex.crews.create("Night Owls") }
                refreshing.await().value()
                creating.await().value()
                val crews =
                    alex.crews.crews
                        .first()
                        .value

                // Each answer takes 2 s in SLOW mode: run side by side, both would be done after 2 s.
                assertTrue(currentTime - start >= 4_000, "took ${currentTime - start} ms")
                assertEquals(listOf("Night Owls"), crews.map { it.name })
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a seat shows as taken at once and stays taken when the server agrees`() =
        runTest {
            world {
                val alex = device()
                alex.crews.join("WIND-4KQ7").value()
                alex.crews.sync("crw_weekend").value()
                val before = alex.ride("crw_weekend", "rid_7f3c")
                server.networkMode = NetworkMode.SLOW

                val taking = async { alex.rides.takeSeat("rid_7f3c") }
                advanceTimeBy(1_000.milliseconds)
                runCurrent()
                val meanwhile = alex.ride("crw_weekend", "rid_7f3c")
                val taken = taking.await().value()
                val after = alex.ride("crw_weekend", "rid_7f3c")

                assertTrue(meanwhile.pending)
                assertEquals(before.seatsFree - 1, meanwhile.seatsFree)
                assertEquals(before.version + 1, taken.version)
                assertEquals(taken, after)
                assertFalse(after.pending)
            }
        }

    @Test
    fun `losing the race for the last seat puts the ride back and shows the server state`() =
        runTest {
            world {
                val driver = device()
                val alex = device()
                val mira = device()
                val crew = driver.crews.create("Lagoon").value()
                listOf(alex, mira).forEach { it.crews.join(crew.inviteCode).value() }
                val ride = driver.rides.offer(crew.id, plan(seats = 1)).value()
                alex.crews.sync(crew.id).value()
                mira.rides.takeSeat(ride.id).value()

                val result = alex.rides.takeSeat(ride.id)
                val shown = alex.ride(crew.id, ride.id)

                assertEquals(AppError.Conflict(ProblemCode.SEATS_FULL), result.errorOrNull())
                assertEquals(0, shown.seatsFree)
                assertEquals(listOf(mira.account.userId.value), shown.passengers.map { it.userId })
                assertFalse(shown.pending)
            }
        }

    @Test
    fun `a failed seat change offline is rolled back`() =
        runTest {
            world {
                val alex = device()
                alex.crews.join("WIND-4KQ7").value()
                alex.crews.sync("crw_weekend").value()
                val before = alex.ride("crw_weekend", "rid_7f3c")
                server.networkMode = NetworkMode.OFFLINE

                val result = alex.rides.takeSeat("rid_7f3c")

                assertEquals(AppError.Network, result.errorOrNull())
                assertEquals(before, alex.ride("crw_weekend", "rid_7f3c"))
            }
        }

    @Test
    fun `a cancelled ride stays visible as cancelled`() =
        runTest {
            world {
                val driver = device()
                val crew = driver.crews.create("Lagoon").value()
                val ride = driver.rides.offer(crew.id, plan(seats = 2)).value()

                driver.rides.cancel(ride.id).value()
                driver.crews.sync(crew.id).value()

                assertEquals(RideStatus.CANCELLED, driver.ride(crew.id, ride.id).status)
            }
        }
}
