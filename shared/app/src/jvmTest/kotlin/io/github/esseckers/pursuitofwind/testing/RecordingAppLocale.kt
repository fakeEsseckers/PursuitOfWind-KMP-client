package io.github.esseckers.pursuitofwind.testing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.esseckers.pursuitofwind.app.language.AppLocale
import java.util.Locale

/**
 * The app's locale in tests: keeps every language it was asked for, and sets the JVM's default locale for an explicit
 * one, which is what resources read. Tests set the default themselves, so the phone's language (null) leaves it alone.
 */
internal class RecordingAppLocale : AppLocale {
    val tags = mutableListOf<String?>()

    @Composable
    override fun provide(languageTag: String?): ProvidedValue<*> {
        remember(languageTag) { tags += languageTag }
        if (languageTag != null) Locale.setDefault(Locale.forLanguageTag(languageTag))
        return LocalLanguageTag provides languageTag
    }
}

private val LocalLanguageTag = staticCompositionLocalOf<String?> { null }
