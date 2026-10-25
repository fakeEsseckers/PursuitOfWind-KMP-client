package io.github.esseckers.pursuitofwind.data.repository

import io.github.esseckers.pursuitofwind.data.fake.NetworkMode
import io.github.esseckers.pursuitofwind.data.fake.THURSDAY_MORNING
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Ride
import io.github.esseckers.pursuitofwind.data.model.RidePlan
import io.github.esseckers.pursuitofwind.data.model.RideStatus
import io.github.esseckers.pursuitofwind.data.model.errorOrNull
import io.github.esseckers.pursuitofwind.data.network.forwardingEngine
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

class SeatAndFeedEdgeCaseTest {
    private val plan =
        RidePlan(
            spotId = SpotId("salt-lagoon"),
            windowStart = THURSDAY_MORNING + 2.days + 5.hours,
            windowEnd = THURSDAY_MORNING + 2.days + 9.hours,
            departAt = THURSDAY_MORNING + 2.days + 2.hours,
            meetingPoint = "Station",
            seatsTotal = 2,
        )

    private suspend fun DataHarness.ride(rideId: String): Ride =
        rides
            .rides("crw_weekend")
            .first()
            .value
            .single { it.id == rideId }

    private suspend fun DataHarness.joinWeekendCrew() {
        crews.join("WIND-4KQ7").value()
        crews.sync("crw_weekend").value()
    }

    @Test
    fun `released seats and cancelled rides reach other members through the feed`() =
        runTest {
            world {
                val alex = device()
                val jonas = device()
                alex.joinWeekendCrew()
                jonas.joinWeekendCrew()
                val offered = jonas.rides.offer("crw_weekend", plan).value()
                alex.crews.sync("crw_weekend").value()

                jonas.rides.takeSeat("rid_7f3c").value()
                jonas.rides.releaseSeat("rid_7f3c").value()
                jonas.rides.cancel(offered.id).value()
                alex.crews.sync("crw_weekend").value()

                val seeded = alex.ride("rid_7f3c")
                assertEquals(
                    jonas.network.api
                        .ride("rid_7f3c")
                        .version,
                    seeded.version,
                )
                assertEquals(listOf("Mira"), seeded.passengers.map { it.displayName })
                assertEquals(2, seeded.seatsFree)
                assertEquals(RideStatus.CANCELLED, alex.ride(offered.id).status)
            }
        }

    @Test
    fun `a long feed is read page by page`() =
        runTest {
            world {
                val alex = device(feedPageSize = 2)
                val jonas = device()
                alex.joinWeekendCrew()
                jonas.joinWeekendCrew()
                repeat(3) {
                    jonas.rides.takeSeat("rid_7f3c").value()
                    jonas.rides.releaseSeat("rid_7f3c").value()
                }
                jonas.rides.takeSeat("rid_7f3c").value()

                alex.crews.sync("crw_weekend").value()

                val seeded = alex.ride("rid_7f3c")
                assertEquals(
                    jonas.network.api
                        .ride("rid_7f3c")
                        .version,
                    seeded.version,
                )
                assertEquals(2, seeded.passengers.size)
                assertEquals(
                    5,
                    alex.crews
                        .members("crw_weekend")
                        .first()
                        .value.size,
                )
            }
        }

    @Test
    fun `the member count stays right when crews are reloaded before a sync`() =
        runTest {
            world {
                val alex = device()
                val jonas = device()
                alex.joinWeekendCrew()
                jonas.crews.join("WIND-4KQ7").value()

                alex.crews.refreshCrews().value()
                alex.crews.sync("crw_weekend").value()

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
    fun `a release that fails offline leaves the seat taken`() =
        runTest {
            world {
                val alex = device()
                alex.joinWeekendCrew()
                alex.rides.takeSeat("rid_7f3c").value()
                val seated = alex.ride("rid_7f3c")
                server.networkMode = NetworkMode.OFFLINE

                val result = alex.rides.releaseSeat("rid_7f3c")

                assertEquals(AppError.Network, result.errorOrNull())
                assertEquals(seated, alex.ride("rid_7f3c"))
            }
        }

    @Test
    fun `taking a seat twice is not an error`() =
        runTest {
            world {
                val alex = device()
                alex.joinWeekendCrew()

                alex.rides.takeSeat("rid_7f3c").value()
                val again = alex.rides.takeSeat("rid_7f3c").value()

                assertTrue(again.passengers.any { it.userId == alex.account.userId.value })
                assertFalse(again.pending)
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a seat request cancelled halfway leaves the ride as the server has it`() =
        runTest {
            world {
                val alex = device()
                alex.joinWeekendCrew()
                val before = alex.ride("rid_7f3c")
                server.networkMode = NetworkMode.SLOW

                val taking = launch { alex.rides.takeSeat("rid_7f3c") }
                advanceTimeBy(1_000.milliseconds)
                runCurrent()
                val meanwhile = alex.ride("rid_7f3c")
                taking.cancel()
                runCurrent()

                assertTrue(meanwhile.pending)
                assertEquals(before, alex.ride("rid_7f3c"))
            }
        }

    @Test
    fun `changes that arrive while a seat request fails stay after the rollback`() =
        runTest {
            world {
                val jonas = device()
                var interrupted = false
                lateinit var alexRef: DataHarness
                val alex =
                    device(
                        forwardingEngine(server) { request, forward ->
                            val takingSeat = request.method.value == "POST" && request.url.encodedPath.endsWith("/seats")
                            if (takingSeat && !interrupted) {
                                // While Alex's request is out, Jonas takes a seat and Alex's device syncs.
                                interrupted = true
                                jonas.rides.takeSeat("rid_7f3c").value()
                                alexRef.crews.sync("crw_weekend").value()
                            }
                            if (takingSeat) throw IOException("connection reset") else forward()
                        },
                    )
                alexRef = alex
                alex.joinWeekendCrew()
                jonas.joinWeekendCrew()

                val result = alex.rides.takeSeat("rid_7f3c")

                val shown = alex.ride("rid_7f3c")
                assertEquals(AppError.Network, result.errorOrNull())
                assertEquals(
                    listOf("Mira", jonas.account.userId.value),
                    shown.passengers.map {
                        if (it.displayName ==
                            "Mira"
                        ) {
                            "Mira"
                        } else {
                            it.userId
                        }
                    },
                )
                assertEquals(1, shown.seatsFree)
                assertFalse(shown.pending)
            }
        }
}
