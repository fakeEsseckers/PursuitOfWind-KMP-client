package io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.model

/** How the drive time changes in the picker. */
internal object DriveLimits {
    const val DEFAULT_MINUTES = 60
    const val STEP_MINUTES = 15

    /** The longest drive the plus button reaches: a day trip. */
    const val MAX_MINUTES = 720

    /** The longest drive the API takes. A spot followed elsewhere with more can still step down from it. */
    const val API_MAX_MINUTES = 1440
}
