package io.github.esseckers.pursuitofwind.data.internal.local

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.esseckers.pursuitofwind.contract.ContractJson
import io.github.esseckers.pursuitofwind.contract.RiderProfileDto
import io.github.esseckers.pursuitofwind.data.model.AppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath
import kotlin.time.Instant

/** The profile as last fetched; [profile] is null when the server had none. */
internal data class StoredProfile(
    val profile: RiderProfileDto?,
    val updatedAt: Instant,
)

/**
 * Small values in DataStore Preferences: the rider's profile, the id of the user the cache belongs to, and the app's
 * language. Later also notification de-duplication (Issue #11).
 */
internal class LocalSettings(
    private val store: DataStore<Preferences>,
    private val scope: CoroutineScope? = null,
) : AutoCloseable {
    /** Null until the profile was fetched once. */
    val profile: Flow<StoredProfile?> =
        store.data.map { preferences ->
            preferences[PROFILE_UPDATED_AT]?.let { updatedAt ->
                StoredProfile(
                    profile = preferences[PROFILE]?.let { ContractJson.decodeFromString(RiderProfileDto.serializer(), it) },
                    updatedAt = Instant.fromEpochMilliseconds(updatedAt),
                )
            }
        }

    /** The app's language; a device choice, so a new owner of the cache keeps it. */
    val language: Flow<AppLanguage> = store.data.map { AppLanguage.of(it[LANGUAGE]) }

    suspend fun saveProfile(
        profile: RiderProfileDto?,
        updatedAt: Instant,
    ) {
        store.edit { preferences ->
            if (profile == null) {
                preferences.remove(PROFILE)
            } else {
                preferences[PROFILE] = ContractJson.encodeToString(RiderProfileDto.serializer(), profile)
            }
            preferences[PROFILE_UPDATED_AT] = updatedAt.toEpochMilliseconds()
        }
    }

    suspend fun saveLanguage(language: AppLanguage) {
        store.edit { preferences ->
            val tag = language.tag
            if (tag == null) preferences.remove(LANGUAGE) else preferences[LANGUAGE] = tag
        }
    }

    /** The user whose data the cache holds, null on a fresh install. */
    suspend fun cacheOwner(): String? = store.data.first()[CACHE_OWNER]

    /** Drops the profile and records [userId] as the new owner of the cache. */
    suspend fun switchOwner(userId: String) {
        store.edit { preferences ->
            preferences.remove(PROFILE)
            preferences.remove(PROFILE_UPDATED_AT)
            preferences[CACHE_OWNER] = userId
        }
    }

    /** Stops the store, so another one can open the same file (when the Koin graph is closed and started again). */
    override fun close() {
        scope?.cancel()
    }

    companion object {
        private val PROFILE = stringPreferencesKey("profile")
        private val PROFILE_UPDATED_AT = longPreferencesKey("profile_updated_at")
        private val CACHE_OWNER = stringPreferencesKey("cache_owner")
        private val LANGUAGE = stringPreferencesKey("language")

        /**
         * One open store per file per process: open it once (Koin `single`) and [close] it before opening the file
         * again. A corrupt file starts over empty; it only holds values that can be fetched again. Tests pass a
         * [scope] on their dispatcher so reads and writes run in virtual time.
         */
        fun open(
            path: String,
            scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        ): LocalSettings =
            LocalSettings(
                store =
                    PreferenceDataStoreFactory.createWithPath(
                        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                        scope = scope,
                        produceFile = { path.toPath() },
                    ),
                scope = scope,
            )
    }
}
