package io.github.esseckers.pursuitofwind.app.language

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.key
import io.github.esseckers.pursuitofwind.data.model.AppLanguage

/**
 * Switches the platform's locale for the app to [languageTag] (BCP 47), or back to the phone's with null, the way the
 * Kotlin Multiplatform docs describe ("Resource environment"): Android gets a configuration in the new locale, iOS the
 * `AppleLanguages` default, the JVM its default locale.
 */
@Composable
internal expect fun appLocale(languageTag: String?): ProvidedValue<*>

/**
 * [content] in [language]. Compose Multiplatform resources read the platform's locale, which nothing observes, so a new
 * language composes [content] again and every string is read anew. Keep state that must survive the change (the back
 * stack, the entries' ViewModels) outside [content].
 */
@Composable
internal fun AppLanguageScope(
    language: AppLanguage,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(appLocale(language.tag)) {
        key(language) { content() }
    }
}
