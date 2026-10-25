package io.github.esseckers.pursuitofwind.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.io.IOException

/** [real], until [full]: then every read and write fails as on a device with no space left. */
internal class FullDisk(
    private val real: DataStore<Preferences>,
) : DataStore<Preferences> {
    var full = false

    override val data: Flow<Preferences> = real.data.onEach { if (full) throw IOException(NO_SPACE) }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        if (full) throw IOException(NO_SPACE) else real.updateData(transform)

    private companion object {
        const val NO_SPACE = "No space left on device"
    }
}
