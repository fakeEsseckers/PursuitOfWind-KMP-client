package io.github.esseckers.pursuitofwind.engine.model

/**
 * Why an hour, a window or a day is not a clear GO.
 *
 * Declaration order is severity order, most severe first, so `sorted()` lists reasons the way the UI shows them.
 * The first eleven reasons belong to hours; [TOO_FAR] and [WINDOW_TOO_SHORT] only appear on decisions.
 */
public enum class Reason {
    THUNDERSTORM,
    OFFSHORE_DANGER,
    DARK,
    TOO_STRONG,
    OVERPOWERED_FOR_QUIVER,
    GUSTY,
    DIRECTION_UNRIDEABLE,
    TOO_LIGHT,
    UNDERPOWERED_FOR_QUIVER,
    RAIN,
    COLD,

    /** The spot is farther than the rider's maximum drive. */
    TOO_FAR,

    /** There is wind, but not long enough to be worth the drive. */
    WINDOW_TOO_SHORT,
}
