package io.github.esseckers.pursuitofwind.data.internal.local

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlin.coroutines.CoroutineContext

/**
 * The offline cache. It is a cache, not a store of record: everything in it can be fetched again, so a schema change
 * that has no migration drops the tables instead of failing (see [build]).
 */
@Database(
    entities = [
        SpotEntity::class,
        MySpotEntity::class,
        ForecastHourEntity::class,
        CrewEntity::class,
        MemberEntity::class,
        RideEntity::class,
        SyncStateEntity::class,
    ],
    version = 2,
)
@ConstructedBy(PursuitOfWindDatabaseConstructor::class)
internal abstract class PursuitOfWindDatabase : RoomDatabase() {
    abstract fun spots(): SpotDao

    abstract fun forecasts(): ForecastDao

    abstract fun crews(): CrewDao

    abstract fun rides(): RideDao

    abstract fun syncState(): SyncStateDao

    companion object {
        /**
         * Finishes a platform builder (file or in-memory) the same way everywhere. Tests pass their dispatcher as
         * [queryContext] so database work runs in virtual time with everything else, and may wrap the [driver] to make
         * the disk fail.
         */
        fun build(
            builder: Builder<PursuitOfWindDatabase>,
            queryContext: CoroutineContext = Dispatchers.IO,
            driver: SQLiteDriver = BundledSQLiteDriver(),
        ): PursuitOfWindDatabase =
            builder
                .fallbackToDestructiveMigration(dropAllTables = true)
                .addCallback(ClearPendingSeatsOnOpen)
                .setQueryCoroutineContext(queryContext)
                .setDriver(driver)
                .build()
    }
}

/**
 * A pending seat change belongs to a request of this process. If the process died before the answer, the overlay would
 * stay forever; on the next start the server's copy is shown instead and the next sync reconciles.
 */
private object ClearPendingSeatsOnOpen : RoomDatabase.Callback() {
    override suspend fun onOpen(connection: SQLiteConnection) {
        connection.execSQL("UPDATE rides SET pendingSeat = NULL, pendingUserId = NULL, pendingUserName = NULL")
    }
}

@Suppress("KotlinNoActualForExpect") // Room's compiler generates the actual for every target.
internal expect object PursuitOfWindDatabaseConstructor : RoomDatabaseConstructor<PursuitOfWindDatabase> {
    override fun initialize(): PursuitOfWindDatabase
}
