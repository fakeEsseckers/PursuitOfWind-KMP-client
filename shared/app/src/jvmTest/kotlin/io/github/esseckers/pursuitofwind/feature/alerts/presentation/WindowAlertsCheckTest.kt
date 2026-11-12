package io.github.esseckers.pursuitofwind.feature.alerts.presentation

import io.github.esseckers.pursuitofwind.app.checkForNewWindows
import io.github.esseckers.pursuitofwind.app.language.AppLocale
import io.github.esseckers.pursuitofwind.core.platform.BackgroundRefreshScheduler
import io.github.esseckers.pursuitofwind.core.platform.LocalNotifier
import io.github.esseckers.pursuitofwind.core.platform.NotificationPermission
import io.github.esseckers.pursuitofwind.data.fake.ControllableClock
import io.github.esseckers.pursuitofwind.data.fake.FakePursuitOfWindServer
import io.github.esseckers.pursuitofwind.data.fake.NetworkMode
import io.github.esseckers.pursuitofwind.data.model.AppLanguage
import io.github.esseckers.pursuitofwind.data.repository.PreferencesRepository
import io.github.esseckers.pursuitofwind.data.repository.ProfileRepository
import io.github.esseckers.pursuitofwind.data.repository.SpotRepository
import io.github.esseckers.pursuitofwind.data.repository.WindowAlertsRepository
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.alerts.domain.entity.AlertsCheckResultEntity
import io.github.esseckers.pursuitofwind.feature.today.SATURDAY
import io.github.esseckers.pursuitofwind.feature.today.THURSDAY_8AM
import io.github.esseckers.pursuitofwind.feature.today.alex
import io.github.esseckers.pursuitofwind.testing.HeldRequests
import io.github.esseckers.pursuitofwind.testing.RecordingAppLocale
import io.github.esseckers.pursuitofwind.testing.RecordingBackgroundRefreshScheduler
import io.github.esseckers.pursuitofwind.testing.RecordingLocalNotifier
import io.github.esseckers.pursuitofwind.testing.RecordingNotificationPermission
import io.github.esseckers.pursuitofwind.testing.testGraph
import io.github.esseckers.pursuitofwind.testing.value
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.koin.core.Koin
import org.koin.dsl.module
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * The background check on the real data layer: the in-app fake backend over HTTP, in-memory Room and DataStore, wired
 * by the app's own Koin modules. The forecast is a steady 18 kn, so every day is a GO at the rider's favorite spot.
 */
class WindowAlertsCheckTest {
    private val systemLocale = Locale.getDefault()
    private val clock = ControllableClock(THURSDAY_8AM)
    private val forecasts = HeldRequests { it.url.encodedPath.endsWith("/forecast") }
    private val koin: Koin = testGraph(clock, module { single<HttpClientEngine> { forecasts.engine(get<FakePursuitOfWindServer>()) } }).koin
    private val server get() = koin.get<FakePursuitOfWindServer>()
    private val notifier get() = koin.get<LocalNotifier>() as RecordingLocalNotifier
    private val permission get() = koin.get<NotificationPermission>() as RecordingNotificationPermission
    private val scheduler get() = koin.get<BackgroundRefreshScheduler>() as RecordingBackgroundRefreshScheduler
    private val alerts get() = koin.get<WindowAlertsRepository>()

    @BeforeTest
    fun useEnglish() {
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun tearDown() {
        koin.close()
        Locale.setDefault(systemLocale)
    }

    /** Alex follows Nordstrand Bay as a favorite, asked for alerts, and the system shows notifications. */
    private suspend fun riderWithAlerts() {
        koin.get<ProfileRepository>().save(alex()).value()
        koin.get<SpotRepository>().follow(SpotId("nordstrand-bay"), 2.hours, favorite = true).value()
        alerts.setEnabled(true).value()
        permission.allowed = true
    }

    private suspend fun check(): AlertsCheckResultEntity = koin.get<WindowAlertsCheck>().run()

    @Test
    fun `the first check tells the rider about each GO day and the next one about none of them again`() =
        runTest {
            riderWithAlerts()

            assertEquals(AlertsCheckResultEntity.DONE, check())
            val first = notifier.shown.toList()
            // Every forecast request is a new model run, as on a live server.
            assertEquals(AlertsCheckResultEntity.DONE, check())

            assertEquals(
                listOf("Today: GO at Nordstrand Bay", "Tomorrow: GO at Nordstrand Bay", "Saturday: GO at Nordstrand Bay"),
                first.map { it.title },
            )
            assertEquals("window:nordstrand-bay:$SATURDAY", first.last().id)
            assertEquals(first, notifier.shown)
            assertEquals(3, alerts.notified().value().size)
        }

    @Test
    fun `a notification says when to ride and which kite to take and when to leave`() =
        runTest {
            riderWithAlerts()

            check()

            val saturday =
                notifier.shown
                    .last()
                    .text
                    .split(" · ")
            assertEquals(3, saturday.size, saturday.toString())
            assertTrue(saturday[0].matches(Regex("""\d\d:\d\d–\d\d:\d\d""")), saturday[0])
            assertEquals("Kite 9 m²", saturday[1])
            assertTrue(saturday[2].startsWith("Leave by "), saturday[2])
        }

    @Test
    fun `a day that comes into the forecast later is told about when it does`() =
        runTest {
            riderWithAlerts()
            check()

            clock.advanceBy(1.days)
            check()

            assertEquals(4, notifier.shown.size)
            assertEquals("Sunday: GO at Nordstrand Bay", notifier.shown.last().title)
        }

    @Test
    fun `nothing is fetched or shown unless the rider asked for alerts`() =
        runTest {
            riderWithAlerts()
            alerts.setEnabled(false).value()

            assertEquals(AlertsCheckResultEntity.DONE, check())

            assertEquals(0, forecasts.sent)
            assertEquals(emptyList(), notifier.shown)
        }

    @Test
    fun `nothing is fetched or shown while the system does not show notifications and the windows stay news`() =
        runTest {
            riderWithAlerts()
            permission.allowed = false

            assertEquals(AlertsCheckResultEntity.DONE, check())
            assertEquals(0, forecasts.sent)

            permission.allowed = true
            check()
            assertEquals(3, notifier.shown.size)
        }

    @Test
    fun `offline the check asks for another try and decides nothing on the old forecast`() =
        runTest {
            riderWithAlerts()
            server.networkMode = NetworkMode.OFFLINE

            assertEquals(AlertsCheckResultEntity.RETRY, check())
            assertEquals(emptyList(), notifier.shown)

            server.networkMode = NetworkMode.NORMAL
            assertEquals(AlertsCheckResultEntity.DONE, check())
            assertEquals(3, notifier.shown.size)
        }

    @Test
    fun `a rider without a profile or a favorite spot has nothing to check`() =
        runTest {
            alerts.setEnabled(true).value()
            permission.allowed = true

            assertEquals(AlertsCheckResultEntity.DONE, check())
            koin.get<ProfileRepository>().save(alex()).value()
            assertEquals(AlertsCheckResultEntity.DONE, check())

            assertEquals(emptyList(), notifier.shown)
        }

    @Test
    fun `the scheduled check speaks the language the rider picked for the app`() =
        runTest {
            riderWithAlerts()
            koin.get<PreferencesRepository>().setLanguage(AppLanguage.RUSSIAN).value()

            assertTrue(checkForNewWindows(koin))

            assertEquals(listOf<String?>("ru"), (koin.get<AppLocale>() as RecordingAppLocale).tags)
            assertEquals("Суббота: ПОГНАЛИ, Nordstrand Bay", notifier.shown.last().title)
            assertTrue(
                notifier.shown
                    .last()
                    .text
                    .contains("Кайт 9 м²"),
                notifier.shown.last().text,
            )
        }

    @Test
    fun `the scheduled check reports a lost connection as not done`() =
        runTest {
            riderWithAlerts()
            server.networkMode = NetworkMode.OFFLINE

            assertEquals(false, checkForNewWindows(koin))
        }

    @Test
    fun `the platform's schedule follows the rider's choice from the stored one on`() =
        runTest {
            val following = launch { koin.get<AlertsSchedule>().follow() }

            alerts.setEnabled(true).value()
            awaitCalls(2)
            alerts.setEnabled(false).value()
            awaitCalls(3)
            following.cancel()

            val schedule = RecordingBackgroundRefreshScheduler.SCHEDULE
            val cancel = RecordingBackgroundRefreshScheduler.CANCEL
            assertEquals(listOf(cancel, schedule, cancel), scheduler.calls)
        }

    @Test
    fun `a device that runs no background checks is left alone`() =
        runTest {
            scheduler.available = false
            alerts.setEnabled(true).value()

            koin.get<AlertsSchedule>().follow()

            assertEquals(emptyList(), scheduler.calls)
        }

    /** DataStore emits on its own threads, so the schedule follows a moment after the choice is stored. */
    private suspend fun awaitCalls(count: Int) {
        while (scheduler.calls.size < count) yield()
    }
}
