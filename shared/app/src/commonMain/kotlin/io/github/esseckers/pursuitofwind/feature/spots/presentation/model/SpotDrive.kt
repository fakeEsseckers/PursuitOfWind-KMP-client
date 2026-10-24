package io.github.esseckers.pursuitofwind.feature.spots.presentation.model

/** How a followed spot's drive changes on the Spots tab: by a quarter hour, up to a day trip with the plus button. */
internal object SpotDrive {
    /** The steps the drive changes in. */
    const val STEP_MINUTES = 15

    /** The longest drive the plus button reaches: a day trip. A longer one followed elsewhere can still step down. */
    const val MAX_STEPPED_MINUTES = 720

    /** The longest drive the API takes. */
    const val MAX_MINUTES = 1440

    /** One step longer than [minutes], up to [MAX_STEPPED_MINUTES]. */
    fun longer(minutes: Int): Int = (minutes + STEP_MINUTES).coerceAtMost(maxOf(minutes, MAX_STEPPED_MINUTES))

    /** One step shorter than [minutes], down to zero. */
    fun shorter(minutes: Int): Int = (minutes - STEP_MINUTES).coerceAtLeast(0)
}
