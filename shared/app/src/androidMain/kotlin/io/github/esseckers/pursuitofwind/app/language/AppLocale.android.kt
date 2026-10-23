package io.github.esseckers.pursuitofwind.app.language

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

@Composable
internal actual fun appLocale(languageTag: String?): ProvidedValue<*> {
    // The Activity's own configuration keeps the phone's locale; only the copy below gets the rider's choice.
    val configuration = LocalConfiguration.current
    val locale = languageTag?.let(Locale::forLanguageTag) ?: configuration.locales[0]
    Locale.setDefault(locale)
    return LocalConfiguration provides Configuration(configuration).apply { setLocale(locale) }
}
