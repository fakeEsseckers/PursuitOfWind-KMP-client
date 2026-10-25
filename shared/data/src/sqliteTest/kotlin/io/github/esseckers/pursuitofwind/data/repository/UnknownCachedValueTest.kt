package io.github.esseckers.pursuitofwind.data.repository

import io.github.esseckers.pursuitofwind.data.model.RideStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** A cache written by a newer version of the app still reads after a downgrade. */
class UnknownCachedValueTest {
    @Test
    fun `a cached ride with a status this version does not know shows as cancelled`() =
        runTest {
            world {
                val alex = device()
                alex.crews.join("WIND-4KQ7").value()
                alex.crews.sync("crw_weekend").value()
                val cached = alex.database.rides().ride("rid_7f3c")!!
                alex.database.rides().upsert(listOf(cached.copy(status = "POSTPONED")))

                val ride =
                    alex.rides
                        .rides("crw_weekend")
                        .first()
                        .value
                        .single { it.id == "rid_7f3c" }

                assertEquals(RideStatus.CANCELLED, ride.status)
            }
        }
}
