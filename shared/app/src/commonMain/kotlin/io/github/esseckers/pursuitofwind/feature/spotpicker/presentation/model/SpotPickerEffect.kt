package io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model

/** One-off events of the spot picker, handled by the app outside the screen. */
internal sealed interface SpotPickerEffect {
    /** The choices are saved, or there was nothing to save. */
    data object Finished : SpotPickerEffect
}
