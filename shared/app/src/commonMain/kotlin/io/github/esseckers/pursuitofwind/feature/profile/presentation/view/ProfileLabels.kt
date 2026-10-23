package io.github.esseckers.pursuitofwind.feature.profile.presentation.view

import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.language_english
import io.github.esseckers.pursuitofwind.core.resources.language_russian
import io.github.esseckers.pursuitofwind.core.resources.language_system
import io.github.esseckers.pursuitofwind.core.resources.level_advanced
import io.github.esseckers.pursuitofwind.core.resources.level_beginner
import io.github.esseckers.pursuitofwind.core.resources.level_intermediate
import io.github.esseckers.pursuitofwind.core.resources.profile_error_name
import io.github.esseckers.pursuitofwind.core.resources.profile_error_weight
import io.github.esseckers.pursuitofwind.core.resources.profile_error_weight_range
import io.github.esseckers.pursuitofwind.core.resources.profile_error_wind_number
import io.github.esseckers.pursuitofwind.core.resources.profile_error_wind_order
import io.github.esseckers.pursuitofwind.core.resources.unit_name_beaufort
import io.github.esseckers.pursuitofwind.core.resources.unit_name_kilometers_per_hour
import io.github.esseckers.pursuitofwind.core.resources.unit_name_knots
import io.github.esseckers.pursuitofwind.core.resources.unit_name_meters_per_second
import io.github.esseckers.pursuitofwind.data.model.AppLanguage
import io.github.esseckers.pursuitofwind.data.model.WindUnit
import io.github.esseckers.pursuitofwind.engine.model.SkillLevel
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileError
import org.jetbrains.compose.resources.StringResource

// Words for the profile form's values and errors.

internal fun levelLabel(level: SkillLevel): StringResource =
    when (level) {
        SkillLevel.BEGINNER -> Res.string.level_beginner
        SkillLevel.INTERMEDIATE -> Res.string.level_intermediate
        SkillLevel.ADVANCED -> Res.string.level_advanced
    }

/** Each language by its own name, so a rider who cannot read the current one still finds theirs. */
internal fun languageLabel(language: AppLanguage): StringResource =
    when (language) {
        AppLanguage.SYSTEM -> Res.string.language_system
        AppLanguage.ENGLISH -> Res.string.language_english
        AppLanguage.RUSSIAN -> Res.string.language_russian
    }

internal fun unitName(unit: WindUnit): StringResource =
    when (unit) {
        WindUnit.KNOTS -> Res.string.unit_name_knots
        WindUnit.METERS_PER_SECOND -> Res.string.unit_name_meters_per_second
        WindUnit.KILOMETERS_PER_HOUR -> Res.string.unit_name_kilometers_per_hour
        WindUnit.BEAUFORT -> Res.string.unit_name_beaufort
    }

internal fun errorText(error: ProfileError): StringResource =
    when (error) {
        ProfileError.NAME_MISSING -> Res.string.profile_error_name
        ProfileError.WEIGHT_MISSING -> Res.string.profile_error_weight
        ProfileError.WEIGHT_OUT_OF_RANGE -> Res.string.profile_error_weight_range
        ProfileError.WIND_NOT_A_NUMBER -> Res.string.profile_error_wind_number
        ProfileError.WIND_NOT_ABOVE_MIN -> Res.string.profile_error_wind_order
    }
