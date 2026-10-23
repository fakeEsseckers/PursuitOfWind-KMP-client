package io.github.esseckers.pursuitofwind.app.language

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import platform.Foundation.NSUserDefaults

private const val LANGUAGES_KEY = "AppleLanguages"

/** The language the app itself last wrote to [LANGUAGES_KEY], so it never removes a choice made in iOS Settings. */
private const val OWN_CHOICE_KEY = "PursuitOfWindLanguage"

private val LocalLanguageTag = staticCompositionLocalOf<String?> { null }

/**
 * Resources on iOS follow `AppleLanguages` in the app's defaults, which is also where iOS Settings keeps a language
 * picked for this app. The app writes its own choice there and, back on the phone's language, removes it only while it
 * is still the app's own choice.
 */
@Composable
internal actual fun appLocale(languageTag: String?): ProvidedValue<*> {
    remember(languageTag) { writeLanguage(languageTag) }
    return LocalLanguageTag provides languageTag
}

private fun writeLanguage(languageTag: String?) {
    val defaults = NSUserDefaults.standardUserDefaults
    val written = defaults.stringForKey(OWN_CHOICE_KEY)
    if (languageTag != null) {
        defaults.setObject(listOf(languageTag), forKey = LANGUAGES_KEY)
        defaults.setObject(languageTag, forKey = OWN_CHOICE_KEY)
    } else if (written != null) {
        if (defaults.arrayForKey(LANGUAGES_KEY)?.firstOrNull() == written) defaults.removeObjectForKey(LANGUAGES_KEY)
        defaults.removeObjectForKey(OWN_CHOICE_KEY)
    }
}
