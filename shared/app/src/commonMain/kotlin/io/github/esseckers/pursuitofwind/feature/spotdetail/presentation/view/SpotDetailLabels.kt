package io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.esseckers.pursuitofwind.core.designsystem.component.verdictLabel
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.chart_hour
import io.github.esseckers.pursuitofwind.core.resources.compass_e_long
import io.github.esseckers.pursuitofwind.core.resources.compass_n_long
import io.github.esseckers.pursuitofwind.core.resources.compass_ne_long
import io.github.esseckers.pursuitofwind.core.resources.compass_nw_long
import io.github.esseckers.pursuitofwind.core.resources.compass_s_long
import io.github.esseckers.pursuitofwind.core.resources.compass_se_long
import io.github.esseckers.pursuitofwind.core.resources.compass_sw_long
import io.github.esseckers.pursuitofwind.core.resources.compass_w_long
import io.github.esseckers.pursuitofwind.core.resources.wind_single
import io.github.esseckers.pursuitofwind.core.ui.Compass
import io.github.esseckers.pursuitofwind.core.ui.DayLabel
import io.github.esseckers.pursuitofwind.core.ui.dayLabel
import io.github.esseckers.pursuitofwind.core.ui.windUnitLabel
import io.github.esseckers.pursuitofwind.data.model.WindUnit
import io.github.esseckers.pursuitofwind.engine.model.Verdict
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.ChartHourUi
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** `18 kn`. */
@Composable
internal fun windText(
    value: Int,
    unit: WindUnit,
): String = stringResource(Res.string.wind_single, value, stringResource(windUnitLabel(unit)))

/**
 * The words every hour of the chart shares: the wind [unit], the names of its [days], the [compass] directions and the
 * [verdicts]. Looked up once per chart, so an hour's description takes one string lookup, not eight.
 */
internal data class HourWords(
    val unit: String,
    val days: Map<DayLabel, String>,
    val compass: Map<Compass, String>,
    val verdicts: Map<Verdict, String>,
)

/** [HourWords] for the hours of a chart in [unit], in the app's language; the same instance while they are equal. */
@Composable
internal fun rememberHourWords(
    hours: List<ChartHourUi>,
    unit: WindUnit,
): HourWords {
    val unitLabel = stringResource(windUnitLabel(unit))
    val days = remember(hours) { hours.map { it.day }.distinct() }.associateWith { dayLabel(it) }
    val compass = Compass.entries.associateWith { stringResource(compassWords(it)) }
    val verdicts = Verdict.entries.associateWith { stringResource(verdictLabel(it)) }
    return remember(unitLabel, days, compass, verdicts) { HourWords(unitLabel, days, compass, verdicts) }
}

/** What a screen reader says for one hour of the chart: day, time, wind, gusts, direction and verdict. */
@Composable
internal fun hourDescription(
    hour: ChartHourUi,
    words: HourWords,
): String =
    stringResource(
        Res.string.chart_hour,
        words.days.getValue(hour.day),
        hour.label,
        hour.mean,
        words.unit,
        hour.gust,
        words.compass.getValue(Compass.of(hour.bearingDeg)),
        words.verdicts.getValue(hour.verdict),
    )

/** `from the northwest`, for screen readers. */
internal fun compassWords(compass: Compass): StringResource =
    when (compass) {
        Compass.N -> Res.string.compass_n_long
        Compass.NE -> Res.string.compass_ne_long
        Compass.E -> Res.string.compass_e_long
        Compass.SE -> Res.string.compass_se_long
        Compass.S -> Res.string.compass_s_long
        Compass.SW -> Res.string.compass_sw_long
        Compass.W -> Res.string.compass_w_long
        Compass.NW -> Res.string.compass_nw_long
    }
