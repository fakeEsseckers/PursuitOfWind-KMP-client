package io.github.esseckers.pursuitofwind.app.language

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/** Gives the app's content a configuration in the rider's language; the Activity's own keeps the phone's. */
internal class AndroidAppLocale : AppLocale {
    @Composable
    override fun provide(languageTag: String?): ProvidedValue<*> {
        val configuration = LocalConfiguration.current
        val locale = languageTag?.let(Locale::forLanguageTag) ?: configuration.locales[0]
        Locale.setDefault(locale)
        return LocalConfiguration provides Configuration(configuration).apply { setLocale(locale) }
    }
}
