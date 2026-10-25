package io.github.esseckers.pursuitofwind.app.language

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import io.github.esseckers.pursuitofwind.data.model.AppLanguage
import org.koin.compose.koinInject

/**
 * [content] in [language]. Compose Multiplatform resources read the platform's locale, which nothing observes, so a new
 * language composes [content] again and every string is read anew. Keep state that must survive the change (the back
 * stack, the entries' ViewModels) outside [content].
 */
@Composable
internal fun AppLanguageScope(
    language: AppLanguage,
    locale: AppLocale = koinInject(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(locale.provide(language.tag)) {
        key(language) { content() }
    }
}
