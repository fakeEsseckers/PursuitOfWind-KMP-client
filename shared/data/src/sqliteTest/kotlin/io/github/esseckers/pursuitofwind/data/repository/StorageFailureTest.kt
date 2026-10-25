package io.github.esseckers.pursuitofwind.data.repository

import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.AppLanguage
import io.github.esseckers.pursuitofwind.data.model.errorOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A device whose own storage fails says so, and does not send a change it cannot keep. */
class StorageFailureTest {
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
    fun `a seat change on a full disk fails as a storage failure and is not sent`() =
        runTest {
            world {
                val alex = device()
                alex.crews.join("WIND-4KQ7").value()
                alex.crews.sync("crw_weekend").value()
                alex.disk.full = true

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
}
