package io.github.esseckers.pursuitofwind.app.language

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

private val LocalLanguageTag = staticCompositionLocalOf<String?> { null }

/** The JVM runs only tests. They set the default locale themselves, so only an explicit choice changes it. */
@Composable
internal actual fun appLocale(languageTag: String?): ProvidedValue<*> {
    if (languageTag != null) Locale.setDefault(Locale.forLanguageTag(languageTag))
    return LocalLanguageTag provides languageTag
}
