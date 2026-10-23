package io.github.esseckers.pursuitofwind.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import co.touchlab.kermit.Logger
import io.github.esseckers.pursuitofwind.data.model.AppLanguage
import io.github.esseckers.pursuitofwind.data.repository.PreferencesRepository
import io.github.esseckers.pursuitofwind.data.repository.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import org.koin.compose.koinInject

// What the app needs before it shows anything: where to start, and in which language.

/**
 * Where a fresh launch starts, once the cached profile is read; null until then. The answer is saved with the screen,
 * so after a configuration change or process death the restored back stack shows at once, without reading again. A
 * profile that cannot be read starts at Welcome: setting it up again replaces it.
 */
@Composable
internal fun rememberStartDestination(profiles: ProfileRepository = koinInject()): Destination? {
    var welcome by rememberSaveable { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(profiles) {
        if (welcome == null) welcome = readStart(profiles) == Destination.Welcome
    }
    return welcome?.let { if (it) Destination.Welcome else Destination.Today }
}

private suspend fun readStart(profiles: ProfileRepository): Destination =
    try {
        startDestination(profiles.profile.first().value)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (
        // Any storage failure: the rider sets the profile up again.
        @Suppress("TooGenericExceptionCaught")
        failure: Exception,
    ) {
        Logger.withTag("AppNavigation").e(failure) { "Reading the profile failed" }
        Destination.Welcome
    }

/**
 * The rider's language, null until it is first read; it changes as the rider picks another. Unreadable means the
 * phone's. The last value is saved with the screen, so after a configuration change the app shows at once in it.
 */
@Composable
internal fun rememberAppLanguage(preferences: PreferencesRepository = koinInject()): AppLanguage? {
    // Saved by name: every platform's saved state takes a String.
    var language by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(preferences) {
        // The language only changes from the profile, so collecting while the app is stopped costs nothing.
        preferences.language
            .catch { failure ->
                Logger.withTag("AppNavigation").e(failure) { "Reading the language failed" }
                emit(AppLanguage.SYSTEM)
            }.collect { language = it.name }
    }
    return language?.let(AppLanguage::valueOf)
}
