package io.github.esseckers.pursuitofwind.feature.today.presentation.model

/** What the rider can ask of the Today screen. */
internal sealed interface TodayIntent {
    /** Pull to refresh, or retry after an error. */
    data object Refresh : TodayIntent
}
