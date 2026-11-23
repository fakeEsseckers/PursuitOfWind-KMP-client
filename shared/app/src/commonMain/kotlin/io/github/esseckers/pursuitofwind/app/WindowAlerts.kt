package io.github.esseckers.pursuitofwind.app

import io.github.esseckers.pursuitofwind.app.language.AppLocale
import io.github.esseckers.pursuitofwind.data.model.AppLanguage
import io.github.esseckers.pursuitofwind.data.model.outcomeOf
import io.github.esseckers.pursuitofwind.data.model.valueOrNull
import io.github.esseckers.pursuitofwind.data.repository.PreferencesRepository
import io.github.esseckers.pursuitofwind.feature.alerts.domain.entity.AlertsCheckResultEntity
import io.github.esseckers.pursuitofwind.feature.alerts.presentation.WindowAlertsCheck
import kotlinx.coroutines.flow.first
import org.koin.core.Koin

/**
 * One background check for new session windows, as the platform's scheduler runs it: in the language the rider picked
 * for the app, which a process started without a screen has not applied yet.
 *
 * @return false when the forecast could not be fetched, so the scheduler may try again before the next scheduled check.
 */
internal suspend fun checkForNewWindows(koin: Koin): Boolean {
    val language = outcomeOf { koin.get<PreferencesRepository>().language.first() }.valueOrNull() ?: AppLanguage.SYSTEM
    koin.get<AppLocale>().use(language.tag)
    return koin.get<WindowAlertsCheck>().run() == AlertsCheckResultEntity.DONE
}
