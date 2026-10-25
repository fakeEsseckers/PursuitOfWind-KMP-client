package io.github.esseckers.pursuitofwind.data.repository

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.esseckers.pursuitofwind.data.model.RideStatus
import io.github.esseckers.pursuitofwind.data.model.WindUnit
import io.github.esseckers.pursuitofwind.engine.model.SkillLevel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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

    @Test
    fun `a cached profile with a level and a wind unit this version does not know reads as a beginner in knots`() =
        runTest {
            world {
                val alex = device()
                alex.profiles.save(ALEX.copy(windUnit = WindUnit.METERS_PER_SECOND)).value()
                alex.settingsFile.edit { stored ->
                    val newer = stored[PROFILE]!!.replace("\"INTERMEDIATE\"", "\"PRO\"")
                    stored[PROFILE] = newer.replace("\"METERS_PER_SECOND\"", "\"FURLONGS\"")
                }

                val profile =
                    assertNotNull(
                        alex.profiles.profile
                            .first()
                            .value,
                    )

                assertEquals(SkillLevel.BEGINNER, profile.rider.level)
                assertEquals(WindUnit.KNOTS, profile.windUnit)
                assertEquals(ALEX.rider.weightKg, profile.rider.weightKg)
            }
        }

    @Test
    fun `a cached profile that cannot be read counts as never fetched and the next refresh replaces it`() =
        runTest {
            world {
                val alex = device()
                alex.profiles.save(ALEX).value()
                alex.settingsFile.edit { it[PROFILE] = "{\"displayName\":" }

                assertEquals(
                    null,
                    alex.profiles.profile
                        .first()
                        .updatedAt,
                )

                alex.profiles.refresh().value()
                assertEquals(
                    ALEX.displayName,
                    alex.profiles.profile
                        .first()
                        .value
                        ?.displayName,
                )
            }
        }

    private companion object {
        /** Where the settings file keeps the profile, the API's JSON. */
        val PROFILE = stringPreferencesKey("profile")
    }
}
