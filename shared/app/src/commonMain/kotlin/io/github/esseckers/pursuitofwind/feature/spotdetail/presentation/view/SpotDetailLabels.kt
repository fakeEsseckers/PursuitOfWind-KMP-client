package io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.view

import androidx.compose.runtime.Composable
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
import io.github.esseckers.pursuitofwind.core.ui.dayLabel
import io.github.esseckers.pursuitofwind.core.ui.windUnitLabel
import io.github.esseckers.pursuitofwind.data.model.WindUnit
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.ChartHourUi
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.model.Compass
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** `18 kn`. */
@Composable
internal fun windText(
    value: Int,
    unit: WindUnit,
): String = stringResource(Res.string.wind_single, value, stringResource(windUnitLabel(unit)))

/** What a screen reader says for one hour of the chart: day, time, wind, gusts, direction and verdict. */
@Composable
internal fun hourDescription(
    hour: ChartHourUi,
    unit: WindUnit,
): String =
    stringResource(
        Res.string.chart_hour,
        dayLabel(hour.day),
        hour.label,
        windText(hour.mean, unit),
        windText(hour.gust, unit),
        stringResource(compassWords(Compass.of(hour.bearingDeg))),
        stringResource(verdictLabel(hour.verdict)),
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
