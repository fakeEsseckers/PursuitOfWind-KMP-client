package io.github.esseckers.pursuitofwind.data.repository

import io.github.esseckers.pursuitofwind.data.fake.ControllableClock
import io.github.esseckers.pursuitofwind.data.fake.THURSDAY_MORNING
import io.github.esseckers.pursuitofwind.data.model.Outcome
import io.github.esseckers.pursuitofwind.data.model.Profile
import io.github.esseckers.pursuitofwind.data.model.WindUnit
import io.github.esseckers.pursuitofwind.data.network.fakeServer
import io.github.esseckers.pursuitofwind.engine.model.RiderProfile
import io.github.esseckers.pursuitofwind.engine.model.SkillLevel
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes

/** One fake backend and its clock; every [device] is a separate installation (a separate user) talking to it. */
internal class World(
    scope: TestScope,
) {
    val clock = ControllableClock(THURSDAY_MORNING)
    val server = scope.fakeServer(clock)
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private val devices = mutableListOf<DataHarness>()

    /** A device talking to the server directly, or through [engine] (for example one that loses answers). */
    fun device(
        engine: HttpClientEngine = server.engine,
        feedPageSize: Int = 100,
    ): DataHarness = DataHarness(engine, clock, dispatcher, feedPageSize).also { devices += it }

    fun close() {
        devices.forEach { it.close() }
    }
}

internal suspend fun TestScope.world(block: suspend World.() -> Unit) {
    val world = World(this)
    try {
        world.block()
    } finally {
        world.close()
    }
}

/** The value of a successful outcome; fails the test otherwise. */
internal fun <T> Outcome<T>.value(): T = assertIs<Outcome.Success<T>>(this, toString()).value

internal val ALEX: Profile =
    Profile(
        displayName = "Alex",
        rider =
            RiderProfile(
                weightKg = 80.0,
                level = SkillLevel.INTERMEDIATE,
                quiverM2 = listOf(7.0, 9.0, 12.0),
                minSession = 120.minutes,
                maxDrive = 180.minutes,
            ),
        windUnit = WindUnit.KNOTS,
    )
