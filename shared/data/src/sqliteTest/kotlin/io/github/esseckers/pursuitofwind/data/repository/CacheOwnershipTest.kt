package io.github.esseckers.pursuitofwind.data.repository

import androidx.room3.useReaderConnection
import io.github.esseckers.pursuitofwind.data.internal.local.SyncKeys
import io.github.esseckers.pursuitofwind.data.model.AppLanguage
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** Every table of the cache is either the user's or everyone's, and a new user starts with none of the previous one's. */
class CacheOwnershipTest {
    private val saltLagoon = SpotId("salt-lagoon")

    private suspend fun DataHarness.query(sql: String): List<String> =
        database.useReaderConnection { connection ->
            connection.usePrepared(sql) { statement ->
                buildList { while (statement.step()) add(statement.getText(0)) }
            }
        }

    private suspend fun DataHarness.rows(table: String): Int = query("SELECT COUNT(*) FROM $table").single().toInt()

    @Test
    fun `every table of the cache belongs to the user or to everyone and none to both`() =
        runTest {
            world {
                val tables =
                    device()
                        .query("SELECT name FROM sqlite_master WHERE type = 'table'")
                        .filterNot { it.startsWith("sqlite_") || it == "room_master_table" || it == "android_metadata" }

                val classified = CacheOwnership.USER_TABLES + CacheOwnership.SHARED_TABLES + CacheOwnership.MIXED_TABLE
                assertEquals(classified.sorted(), tables.sorted())
                assertTrue((CacheOwnership.USER_TABLES intersect CacheOwnership.SHARED_TABLES).isEmpty())
            }
        }

    @Test
    fun `another user starts with none of the previous one's data and keeps what is everyone's`() =
        runTest {
            world {
                val alex = device()
                alex.profiles.refresh().value()
                alex.spots.refreshCatalog().value()
                alex.spots.follow(saltLagoon, drive = 160.minutes, favorite = true).value()
                alex.forecasts.refresh(saltLagoon).value()
                alex.crews.join("WIND-4KQ7").value()
                alex.crews.sync("crw_weekend").value()
                alex.preferences.setLanguage(AppLanguage.RUSSIAN).value()
                CacheOwnership.USER_TABLES.forEach { assertTrue(alex.rows(it) > 0, it) }
                val sharedKeys = alex.query("SELECT `key` FROM ${CacheOwnership.MIXED_TABLE}").filter(CacheOwnership::isSharedKey)
                assertTrue(SyncKeys.CATALOG in sharedKeys, sharedKeys.toString())
                assertTrue(sharedKeys.any { it.startsWith(SyncKeys.FORECAST_PREFIX) }, sharedKeys.toString())

                alex.userCache.claimFor("usr_someone_else", newUser = false)

                CacheOwnership.USER_TABLES.forEach { assertEquals(0, alex.rows(it), it) }
                CacheOwnership.SHARED_TABLES.forEach { assertTrue(alex.rows(it) > 0, it) }
                // The user's fetch times and feed cursors are gone, and everyone's stay.
                assertEquals(sharedKeys.sorted(), alex.query("SELECT `key` FROM ${CacheOwnership.MIXED_TABLE}").sorted())
                assertEquals(
                    null,
                    alex.profiles.profile
                        .first()
                        .updatedAt,
                )
                assertEquals(AppLanguage.RUSSIAN, alex.preferences.language.first())
            }
        }
}
