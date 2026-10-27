package io.github.esseckers.pursuitofwind.app.language

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue

/**
 * Switches the platform's locale for the app, the way the Kotlin Multiplatform docs describe ("Resource environment").
 * Each platform binds its own in its Koin platform module.
 */
internal interface AppLocale {
    /** What the app's content is given to show in [languageTag] (BCP 47), or in the phone's language with null. */
    @Composable
    fun provide(languageTag: String?): ProvidedValue<*>
}
