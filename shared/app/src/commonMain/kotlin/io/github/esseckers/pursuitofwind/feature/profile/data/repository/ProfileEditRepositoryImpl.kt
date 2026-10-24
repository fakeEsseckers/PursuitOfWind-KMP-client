package io.github.esseckers.pursuitofwind.feature.profile.data.repository

import co.touchlab.kermit.Logger
import io.github.esseckers.pursuitofwind.data.model.AppLanguage
import io.github.esseckers.pursuitofwind.data.model.Outcome
import io.github.esseckers.pursuitofwind.data.model.Profile
import io.github.esseckers.pursuitofwind.data.repository.PreferencesRepository
import io.github.esseckers.pursuitofwind.data.repository.ProfileRepository
import io.github.esseckers.pursuitofwind.feature.profile.domain.repository.ProfileEditRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * [ProfileEditRepository] over the shared [ProfileRepository] and [PreferencesRepository]. A cached profile that cannot
 * be read shows as none, so the rider can fill in the form again and the save replaces it.
 */
internal class ProfileEditRepositoryImpl(
    private val profiles: ProfileRepository,
    private val preferences: PreferencesRepository,
) : ProfileEditRepository {
    override val profile: Flow<Profile?> =
        profiles.profile
            .map<_, Profile?> { it.value }
            .catch { failure ->
                Logger.withTag("Profile").e(failure) { "Reading the cached profile failed" }
                emit(null)
            }.distinctUntilChanged()

    override val language: Flow<AppLanguage> =
        preferences.language.catch { failure ->
            Logger.withTag("Profile").e(failure) { "Reading the language failed" }
            emit(AppLanguage.SYSTEM)
        }

    override suspend fun save(profile: Profile): Outcome<Profile> = profiles.save(profile)

    override suspend fun setLanguage(language: AppLanguage): Outcome<Unit> = preferences.setLanguage(language)
}
