package io.github.esseckers.pursuitofwind.data.repository

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.esseckers.pursuitofwind.data.fake.THURSDAY_MORNING
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.NotifiedWindow
import io.github.esseckers.pursuitofwind.data.model.errorOrNull
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/** What the app keeps to announce a session window once: the rider's choice and the windows already announced. */
class WindowAlertsRepositoryTest {
    private val saturday =
        NotifiedWindow(
            spotId = SpotId("salt-lagoon"),
            date = LocalDate(2026, 7, 18),
            start = THURSDAY_MORNING + 2.days + 5.hours,
            end = THURSDAY_MORNING + 2.days + 9.hours,
        )
    private val sunday = saturday.copy(spotId = SpotId("nordstrand-bay"), date = LocalDate(2026, 7, 19), start = saturday.start + 1.days)

    @Test
    fun `alerts are off until the rider turns them on and the choice stays with the device`() =
        runTest {
            world {
                val alex = device()

                assertEquals(false, alex.alerts.enabled.first())
                alex.alerts.setEnabled(true).value()
                alex.settings.switchOwner("usr_9999")

                assertEquals(true, alex.alerts.enabled.first())
                alex.alerts.setEnabled(false).value()
                assertEquals(false, alex.alerts.enabled.first())
            }
        }

    @Test
    fun `the announced windows are kept as saved and replaced by the next save`() =
        runTest {
            world {
                val alex = device()

                assertEquals(emptyList(), alex.alerts.notified().value())
                alex.alerts.saveNotified(listOf(saturday, sunday)).value()
                assertEquals(listOf(saturday, sunday), alex.alerts.notified().value())

                alex.alerts.saveNotified(listOf(sunday)).value()
                assertEquals(listOf(sunday), alex.alerts.notified().value())
            }
        }

    @Test
    fun `another user starts with no announced windows`() =
        runTest {
            world {
                val alex = device()
                alex.alerts.saveNotified(listOf(saturday)).value()

                alex.settings.switchOwner("usr_9999")

                assertEquals(emptyList(), alex.alerts.notified().value())
            }
        }

    @Test
    fun `an entry that cannot be read is left out and the others stay`() =
        runTest {
            world {
                val alex = device()
                alex.alerts.saveNotified(listOf(saturday, sunday)).value()
                alex.settingsFile.edit { stored -> stored[NOTIFIED] = stored[NOTIFIED]!!.replace("\"2026-07-18\"", "\"someday\"") }

                assertEquals(listOf(sunday), alex.alerts.notified().value())
            }
        }

    @Test
    fun `announced windows that cannot be read at all count as none`() =
        runTest {
            world {
                val alex = device()
                alex.settingsFile.edit { stored -> stored[NOTIFIED] = "not json" }

                assertEquals(emptyList(), alex.alerts.notified().value())
            }
        }

    @Test
    fun `a full or unreadable disk is a storage failure`() =
        runTest {
            world {
                val alex = device()
                alex.disk.full = true

                assertEquals(AppError.Storage, alex.alerts.setEnabled(true).errorOrNull())
                assertEquals(AppError.Storage, alex.alerts.saveNotified(listOf(saturday)).errorOrNull())

                alex.disk.unreadable = true
                assertEquals(AppError.Storage, alex.alerts.notified().errorOrNull())
            }
        }

    private companion object {
        val NOTIFIED = stringPreferencesKey("notified_windows")
    }
}
