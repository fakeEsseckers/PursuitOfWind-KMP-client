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
import io.github.esseckers.pursuitofwind.data.model.Profile
import io.github.esseckers.pursuitofwind.data.model.WindUnit
import io.github.esseckers.pursuitofwind.data.repository.PreferencesRepository
import io.github.esseckers.pursuitofwind.data.repository.ProfileRepository
import io.github.esseckers.pursuitofwind.data.repository.SpotRepository
import io.github.esseckers.pursuitofwind.data.repository.WindowAlertsRepository
import io.github.esseckers.pursuitofwind.engine.model.RiderProfile
import io.github.esseckers.pursuitofwind.engine.model.SkillLevel
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.alerts.domain.entity.AlertsCheckResultEntity
import io.github.esseckers.pursuitofwind.testing.HeldRequests
import io.github.esseckers.pursuitofwind.testing.RecordingAppLocale
import io.github.esseckers.pursuitofwind.testing.RecordingBackgroundRefreshScheduler
import io.github.esseckers.pursuitofwind.testing.RecordingLocalNotifier
import io.github.esseckers.pursuitofwind.testing.RecordingNotificationPermission
import io.github.esseckers.pursuitofwind.testing.testGraph
import io.github.esseckers.pursuitofwind.testing.value
import io.ktor.client.engine.HttpClientEngine
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import org.koin.core.Koin
import org.koin.dsl.module
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * The background check on the real data layer: the in-app fake backend over HTTP, in-memory Room and DataStore, wired
 * by the app's own Koin modules. The forecast is a steady 18 kn, so every day is a GO at the rider's favorite spot.
 */
class WindowAlertsCheckTest {
    private val systemLocale = Locale.getDefault()
    private val saturday = LocalDate(2026, 10, 24)

    /** Thursday 08:00 in Berlin, where the test graph puts the rider. */
    private val clock = ControllableClock(LocalDate(2026, 10, 22).atTime(LocalTime(8, 0)).toInstant(TimeZone.of("Europe/Berlin")))
    private val alex =
        Profile(
            displayName = "Alex",
            rider = RiderProfile(weightKg = 80.0, level = SkillLevel.INTERMEDIATE, quiverM2 = listOf(7.0, 9.0, 12.0)),
            windUnit = WindUnit.KNOTS,
        )
    private val forecasts = HeldRequests { it.url.encodedPath.endsWith("/forecast") }
    private val lagoonForecast = HeldRequests { it.url.encodedPath == "/v1/spots/salt-lagoon/forecast" }
    private val koin: Koin =
        testGraph(
            clock,
            module { single<HttpClientEngine> { lagoonForecast.engine(forecasts.engine(get<FakePursuitOfWindServer>())) } },
        ).koin
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
        koin.get<ProfileRepository>().save(alex).value()
        koin.get<SpotRepository>().follow(SpotId("nordstrand-bay"), 2.hours, favorite = true).value()
        alerts.setEnabled(true).value()
        permission.allowed = true
    }

    /** Salt Lagoon becomes a favorite too, [drive] away. */
    private suspend fun alsoFollowingSaltLagoon(drive: Duration = 3.hours) {
        koin.get<SpotRepository>().follow(SpotId("salt-lagoon"), drive, favorite = true).value()
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
            assertEquals("window:nordstrand-bay:$saturday", first.last().id)
            assertEquals("pursuitofwind://spot/nordstrand-bay?date=$saturday", first.last().link)
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
            koin.get<ProfileRepository>().save(alex).value()
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
    fun `a new favorite does not have days told about again that were announced at another spot`() =
        runTest {
            riderWithAlerts()
            check()
            val first = notifier.shown.toList()

            // Closer than Nordstrand Bay, so it may well be a day's best spot now. The bay's window, the one announced,
            // is still among each day's windows.
            alsoFollowingSaltLagoon(drive = 1.hours)
            check()

            assertEquals(3, first.size)
            assertEquals(first, notifier.shown)
        }

    @Test
    fun `a favorite the server no longer has costs the rider that spot only`() =
        runTest {
            riderWithAlerts()
            alsoFollowingSaltLagoon()
            lagoonForecast.refused = HttpStatusCode.NotFound

            assertEquals(AlertsCheckResultEntity.DONE, check())

            assertEquals(List(3) { "Nordstrand Bay" }, notifier.shown.map { it.title.substringAfter("GO at ") })
        }

    @Test
    fun `a favorite whose forecast the server cannot give right now is worth another try and the others are told about`() =
        runTest {
            riderWithAlerts()
            alsoFollowingSaltLagoon()
            lagoonForecast.refused = HttpStatusCode.ServiceUnavailable

            assertEquals(AlertsCheckResultEntity.RETRY, check())
            assertEquals(3, notifier.shown.size)

            lagoonForecast.refused = null
            assertEquals(AlertsCheckResultEntity.DONE, check())
            assertEquals(3, notifier.shown.size)
        }

    @Test
    fun `the platform's schedule follows the rider's choice from the stored one on`() =
        runTest {
            val following = launch { koin.get<AlertsSchedule>().follow() }
            val schedule = RecordingBackgroundRefreshScheduler.SCHEDULE
            val cancel = RecordingBackgroundRefreshScheduler.CANCEL

            // The stored choice first: off, so whatever was scheduled is cancelled.
            assertEquals(listOf(cancel), scheduler.awaitCalls(1))
            alerts.setEnabled(true).value()
            assertEquals(listOf(cancel, schedule), scheduler.awaitCalls(2))
            alerts.setEnabled(false).value()
            assertEquals(listOf(cancel, schedule, cancel), scheduler.awaitCalls(3))
            following.cancel()
        }

    @Test
    fun `saving something else in the settings file does not touch the schedule`() =
        runTest {
            alerts.setEnabled(true).value()
            val following = launch { koin.get<AlertsSchedule>().follow() }
            scheduler.awaitCalls(1)

            // The profile and the language live in the same file as the choice.
            koin.get<ProfileRepository>().save(alex).value()
            koin.get<PreferencesRepository>().setLanguage(AppLanguage.RUSSIAN).value()
            alerts.setEnabled(false).value()

            assertEquals(
                listOf(RecordingBackgroundRefreshScheduler.SCHEDULE, RecordingBackgroundRefreshScheduler.CANCEL),
                scheduler.awaitCalls(2),
            )
            following.cancel()
        }

    @Test
    fun `a device that runs no background checks is left alone`() =
        runTest {
            scheduler.available = false
            alerts.setEnabled(true).value()

            koin.get<AlertsSchedule>().follow()

            assertEquals(emptyList(), scheduler.calls)
        }
}
