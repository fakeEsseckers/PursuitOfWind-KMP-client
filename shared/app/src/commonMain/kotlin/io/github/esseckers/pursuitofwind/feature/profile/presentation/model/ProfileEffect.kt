package io.github.esseckers.pursuitofwind.feature.profile.presentation.model

/** One-off events of the profile form, handled by the app outside the screen. */
internal sealed interface ProfileEffect {
    /** The profile is saved (or was already saved as shown). */
    data object Saved : ProfileEffect
}
