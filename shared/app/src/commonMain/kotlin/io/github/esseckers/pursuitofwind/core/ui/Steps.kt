package io.github.esseckers.pursuitofwind.core.ui

/** Which way the rider moves a stepper. */
internal enum class Step {
    DOWN,
    UP,
}

/**
 * How a stepper moves a value: by [step], between [min] and [max]. A value outside the range, such as a longer drive
 * saved on another device, shows as it is and only ever steps back towards the range.
 */
internal data class StepRange(
    val min: Int,
    val max: Int,
    val step: Int,
) {
    /** [value] moved one [step] the way [to] points, stopping at the end of the range. */
    fun next(
        value: Int,
        to: Step,
    ): Int =
        when (to) {
            Step.DOWN -> (value - step).coerceAtLeast(minOf(value, min))
            Step.UP -> (value + step).coerceAtMost(maxOf(value, max))
        }

    /** The stepper for [value]: the value, and which of its buttons move it. */
    fun stepper(value: Int): StepperUi = StepperUi(value, canDecrease = value > min, canIncrease = value < max)
}

/** A stepper as the view shows it: its [value], and whether it can go down or up from there. */
internal data class StepperUi(
    val value: Int,
    val canDecrease: Boolean,
    val canIncrease: Boolean,
)

/** How the drive to a spot changes, on the Spots tab, in the picker and on the map: by a quarter hour, up to a day trip. */
internal val SPOT_DRIVE_STEPS = StepRange(min = 0, max = 720, step = 15)

/** The drive a spot starts with when the rider follows it, until they change it. */
internal const val DEFAULT_SPOT_DRIVE_MINUTES = 60
