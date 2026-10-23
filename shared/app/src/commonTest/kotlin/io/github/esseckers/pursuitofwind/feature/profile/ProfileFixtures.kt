package io.github.esseckers.pursuitofwind.feature.profile

import io.github.esseckers.pursuitofwind.data.model.Profile
import io.github.esseckers.pursuitofwind.data.model.WindUnit
import io.github.esseckers.pursuitofwind.engine.model.RiderProfile
import io.github.esseckers.pursuitofwind.engine.model.SkillLevel
import io.github.esseckers.pursuitofwind.engine.model.WindSpeed
import kotlin.time.Duration.Companion.minutes

/** Alex as saved: 80 kg, intermediate, kites 7 / 9 / 12 m², knots, the default session and drive. */
fun savedAlex(
    unit: WindUnit = WindUnit.KNOTS,
    minWind: WindSpeed? = null,
    maxWind: WindSpeed? = null,
): Profile =
    Profile(
        displayName = "Alex",
        rider =
            RiderProfile(
                weightKg = 80.0,
                level = SkillLevel.INTERMEDIATE,
                quiverM2 = listOf(7.0, 9.0, 12.0),
                minWind = minWind,
                maxWind = maxWind,
                minSession = 120.minutes,
                maxDrive = 180.minutes,
            ),
        windUnit = unit,
    )
