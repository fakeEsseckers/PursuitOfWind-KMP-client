package io.github.esseckers.pursuitofwind.contract

import kotlinx.serialization.Serializable

@Serializable
public enum class SkillLevelDto {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED,
}

/** How the app shows wind speeds. The API itself always uses m/s. */
@Serializable
public enum class WindUnitDto {
    KNOTS,
    METERS_PER_SECOND,
    KILOMETERS_PER_HOUR,
    BEAUFORT,
}

/**
 * `GET` / `PUT /v1/me/profile`. Wind limits in m/s; absent limits mean "use the level default". Riding hours are whole
 * hours in each spot's local time; absent ones mean 9 to 21.
 */
@Serializable
public data class RiderProfileDto(
    val displayName: String,
    val weightKg: Double,
    val level: SkillLevelDto,
    val quiverM2: List<Double>,
    val minWindMps: Double? = null,
    val maxWindMps: Double? = null,
    val minSessionMinutes: Int,
    val maxDriveMinutes: Int,
    val ridingFromHour: Int = ApiLimits.RiderProfile.RIDING_FROM_HOUR_DEFAULT,
    val ridingToHour: Int = ApiLimits.RiderProfile.RIDING_TO_HOUR_DEFAULT,
    val windUnit: WindUnitDto,
)
