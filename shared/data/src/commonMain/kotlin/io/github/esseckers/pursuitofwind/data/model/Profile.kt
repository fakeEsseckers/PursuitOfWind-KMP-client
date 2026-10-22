package io.github.esseckers.pursuitofwind.data.model

import io.github.esseckers.pursuitofwind.engine.model.RiderProfile

/**
 * The rider's own profile. Everything the decision engine needs is in [rider], so screens and background checks pass
 * it to the engine as it is; [displayName] and [windUnit] are for people.
 */
public data class Profile(
    val displayName: String,
    val rider: RiderProfile,
    val windUnit: WindUnit,
)

/** How the app shows wind speeds. Values are always stored in m/s. */
public enum class WindUnit {
    KNOTS,
    METERS_PER_SECOND,
    KILOMETERS_PER_HOUR,
    BEAUFORT,
}
