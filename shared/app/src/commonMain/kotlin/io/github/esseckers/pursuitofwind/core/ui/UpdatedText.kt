package io.github.esseckers.pursuitofwind.core.ui

import androidx.compose.runtime.Composable
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.updated_days_ago
import io.github.esseckers.pursuitofwind.core.resources.updated_hours_ago
import io.github.esseckers.pursuitofwind.core.resources.updated_just_now
import io.github.esseckers.pursuitofwind.core.resources.updated_minutes_ago
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** "Updated 12 min ago" for cached data of that [age]. */
@Composable
internal fun updatedText(age: Duration): String =
    when {
        age < 1.minutes -> stringResource(Res.string.updated_just_now)
        age < 1.hours -> age.inWholeMinutes.toInt().let { pluralStringResource(Res.plurals.updated_minutes_ago, it, it) }
        age < 1.days -> age.inWholeHours.toInt().let { pluralStringResource(Res.plurals.updated_hours_ago, it, it) }
        else -> age.inWholeDays.toInt().let { pluralStringResource(Res.plurals.updated_days_ago, it, it) }
    }
