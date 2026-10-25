package io.github.esseckers.pursuitofwind.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteException
import androidx.sqlite.SQLiteStatement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.io.IOException

/**
 * The disk under a device's database and settings file. With no space left ([full]) every write fails and reads still
 * work, as on a real device; [unreadable] makes reading the settings file fail too.
 */
internal class DeviceDisk {
    var full = false
    var unreadable = false

    /** [real], whose writes fail while the disk is [full] and reads while it is [unreadable]. */
    fun settings(real: DataStore<Preferences>): DataStore<Preferences> =
        object : DataStore<Preferences> {
            override val data: Flow<Preferences> = real.data.onEach { if (unreadable) throw IOException(UNREADABLE) }

            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                if (full) throw IOException(NO_SPACE) else real.updateData(transform)
        }

    /** [real], whose writes of the app's data fail while the disk is [full]. */
    fun database(real: SQLiteDriver): SQLiteDriver =
        object : SQLiteDriver by real {
            override fun open(fileName: String): SQLiteConnection = Connection(real.open(fileName))
        }

    private inner class Connection(
        private val real: SQLiteConnection,
    ) : SQLiteConnection by real {
        override fun prepare(sql: String): SQLiteStatement = real.prepare(sql).let { if (writesData(sql)) Write(it) else it }
    }

    private inner class Write(
        private val real: SQLiteStatement,
    ) : SQLiteStatement by real {
        override fun step(): Boolean = if (full) throw SQLiteException("$NO_SPACE (SQLITE_FULL)") else real.step()
    }

    private companion object {
        const val NO_SPACE = "No space left on device"
        const val UNREADABLE = "Input/output error"
        val WRITES = listOf("INSERT", "UPDATE", "DELETE", "REPLACE")

        /** A write of the app's tables; Room's own change log is a temporary table in memory and keeps working. */
        fun writesData(sql: String): Boolean {
            val statement = sql.trimStart().uppercase()
            return WRITES.any { statement.startsWith(it) } && "ROOM_TABLE_MODIFICATION_LOG" !in statement
        }
    }
}
