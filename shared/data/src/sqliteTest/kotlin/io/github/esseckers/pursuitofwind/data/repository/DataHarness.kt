package io.github.esseckers.pursuitofwind.data.repository

import androidx.room3.RoomDatabase
import io.github.esseckers.pursuitofwind.data.fake.ControllableClock
import io.github.esseckers.pursuitofwind.data.internal.local.LocalSettings
import io.github.esseckers.pursuitofwind.data.internal.local.PursuitOfWindDatabase
import io.github.esseckers.pursuitofwind.data.internal.local.UserCache
import io.github.esseckers.pursuitofwind.data.internal.sync.CrewFeedSync
import io.github.esseckers.pursuitofwind.data.internal.sync.CrewStore
import io.github.esseckers.pursuitofwind.data.network.NetworkHarness
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.uuid.Uuid

/** An in-memory database: on the JVM and on iOS the bundled SQLite driver runs in tests. */
internal expect fun inMemoryDatabaseBuilder(): RoomDatabase.Builder<PursuitOfWindDatabase>

/** A path for a file that does not exist yet, in the platform's temporary directory. */
internal expect fun temporaryFilePath(name: String): String

/**
 * One device: the data layer as the app wires it, on an in-memory database, talking to [engine]. Database and settings
 * run on [dispatcher], the test's, so optimistic steps can be observed between virtual-time delays.
 */
internal class DataHarness(
    engine: HttpClientEngine,
    val clock: ControllableClock,
    dispatcher: CoroutineDispatcher,
    feedPageSize: Int,
) : AutoCloseable {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    val database = PursuitOfWindDatabase.build(inMemoryDatabaseBuilder(), dispatcher)

    /** The device's settings file; [FullDisk.full] makes every read and write of it fail. */
    val disk = FullDisk(LocalSettings.dataStore(temporaryFilePath("${Uuid.random()}.preferences_pb"), scope))
    val settings = LocalSettings(disk, scope)
    val userCache = UserCache(database, settings)
    val network = NetworkHarness(engine, onSignedIn = userCache::claimFor)
    private val store = CrewStore(network.api, database, clock)
    private val feed = CrewFeedSync(network.api, database, store, clock, feedPageSize)

    val account = AccountRepository(network.session)
    val profiles = ProfileRepository(network.api, settings, clock)
    val preferences = PreferencesRepository(settings)
    val spots = SpotRepository(network.api, database, clock)
    val forecasts = ForecastRepository(network.api, database, clock)
    val crews = CrewRepository(network.api, database, store, feed, clock)
    val rides = RideRepository(network.api, database, store, network.session, settings)

    override fun close() {
        database.close()
        scope.cancel()
    }
}
