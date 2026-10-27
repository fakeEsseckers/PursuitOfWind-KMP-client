package io.github.esseckers.pursuitofwind.data.repository

import io.github.esseckers.pursuitofwind.data.fake.THURSDAY_MORNING
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.AppLanguage
import io.github.esseckers.pursuitofwind.data.model.RidePlan
import io.github.esseckers.pursuitofwind.data.model.errorOrNull
import io.github.esseckers.pursuitofwind.data.model.outcomeOf
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * A device whose own storage fails says so, and does not send a change it cannot keep; a change the server made is not
 * reported as failed because only keeping the answer failed.
 */
class StorageFailureTest {
    private val plan =
        RidePlan(
            spotId = SpotId("salt-lagoon"),
            windowStart = THURSDAY_MORNING + 2.days + 5.hours,
            windowEnd = THURSDAY_MORNING + 2.days + 9.hours,
            departAt = THURSDAY_MORNING + 2.days + 2.hours,
            meetingPoint = "Station",
            seatsTotal = 3,
        )

    @Test
    fun `a full disk is a storage failure and not a lost connection`() =
        runTest {
            world {
                val alex = device()
                alex.profiles.refresh().value()
                alex.disk.full = true

                assertEquals(AppError.Storage, alex.preferences.setLanguage(AppLanguage.RUSSIAN).errorOrNull())
                assertEquals(AppError.Storage, alex.profiles.refresh().errorOrNull())
            }
        }

    @Test
    fun `a settings file that cannot be read is a storage failure`() =
        runTest {
            world {
                val alex = device()
                alex.profiles.refresh().value()
                alex.disk.unreadable = true

                assertEquals(AppError.Storage, outcomeOf { alex.profiles.profile.first() }.errorOrNull())
                assertEquals(AppError.Storage, outcomeOf { alex.preferences.language.first() }.errorOrNull())
            }
        }

    @Test
    fun `a seat change on a full disk fails as a storage failure and is not sent`() =
        runTest {
            world {
                val alex = device()
                alex.crews.join("WIND-4KQ7").value()
                alex.crews.sync("crw_weekend").value()
                alex.disk.full = true

                // Showing the change at once is a write of the database, so it fails before anything is sent.
                assertEquals(AppError.Storage, alex.rides.takeSeat("rid_7f3c").errorOrNull())

                alex.disk.full = false
                alex.crews.sync("crw_weekend").value()
                val ride =
                    alex.rides
                        .rides("crw_weekend")
                        .first()
                        .value
                        .single { it.id == "rid_7f3c" }
                assertTrue(ride.passengers.none { it.userId == alex.account.userId.value }, ride.toString())
            }
        }

    @Test
    fun `changes the server made on a full disk succeed once and the next sync caches them`() =
        runTest {
            world {
                val alex = device()
                alex.profiles.refresh().value()
                alex.crews.join("WIND-4KQ7").value()
                alex.crews.sync("crw_weekend").value()
                alex.disk.full = true

                val offered = alex.rides.offer("crw_weekend", plan).value()
                assertEquals(
                    "Alexandra",
                    alex.profiles
                        .save(ALEX.copy(displayName = "Alexandra"))
                        .value()
                        .displayName,
                )

                alex.disk.full = false
                alex.crews.sync("crw_weekend").value()
                alex.profiles.refresh().value()
                val driving =
                    alex.rides
                        .rides("crw_weekend")
                        .first()
                        .value
                        .filter { it.driver.userId == alex.account.userId.value }
                assertEquals(listOf(offered.id), driving.map { it.id })
                assertEquals(
                    "Alexandra",
                    alex.profiles.profile
                        .first()
                        .value
                        ?.displayName,
                )
            }
        }
}
