package io.github.esseckers.pursuitofwind.app.language

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import platform.Foundation.NSUserDefaults

/**
 * Resources on iOS follow `AppleLanguages` in the app's [defaults], which is also where iOS Settings keeps a language
 * picked for this app. The app writes its own choice there and, back on the phone's language, removes it only while it
 * is still the app's own choice.
 */
internal class IosAppLocale(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : AppLocale {
    @Composable
    override fun provide(languageTag: String?): ProvidedValue<*> {
        remember(languageTag) { write(languageTag) }
        return LocalLanguageTag provides languageTag
    }

    private fun write(languageTag: String?) {
        val written = defaults.stringForKey(OWN_CHOICE_KEY)
        if (languageTag != null) {
            defaults.setObject(listOf(languageTag), forKey = LANGUAGES_KEY)
            defaults.setObject(languageTag, forKey = OWN_CHOICE_KEY)
        } else if (written != null) {
            if (defaults.arrayForKey(LANGUAGES_KEY)?.firstOrNull() == written) defaults.removeObjectForKey(LANGUAGES_KEY)
            defaults.removeObjectForKey(OWN_CHOICE_KEY)
        }
    }

    private companion object {
        const val LANGUAGES_KEY = "AppleLanguages"

        /** The language the app itself last wrote to [LANGUAGES_KEY], so it never removes a choice made in iOS Settings. */
        const val OWN_CHOICE_KEY = "PursuitOfWindLanguage"
    }
}

/** The language the content is shown in. Nothing reads it: iOS reads its defaults, but the scope needs a value to provide. */
private val LocalLanguageTag = staticCompositionLocalOf<String?> { null }
