package io.github.esseckers.pursuitofwind.core.ui

import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.error_network
import io.github.esseckers.pursuitofwind.core.resources.error_server
import io.github.esseckers.pursuitofwind.core.resources.error_storage
import io.github.esseckers.pursuitofwind.core.resources.error_unauthorized
import io.github.esseckers.pursuitofwind.core.resources.error_unknown
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.WindUnit
import io.github.esseckers.pursuitofwind.engine.model.WindSpeed
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import kotlin.math.roundToInt
import kotlin.time.Instant

/** 24-hour wall-clock time of this instant in [zone], such as `08:30`. */
internal fun Instant.clockTime(zone: TimeZone): String {
    val time = toLocalDateTime(zone).time
    return "${time.hour.twoDigits()}:${time.minute.twoDigits()}"
}

private fun Int.twoDigits(): String = toString().padStart(2, '0')

/** A kite size without a needless fraction: `9`, `10.5`, or `10,5` with a comma as [decimalSeparator]. */
internal fun formatKiteSize(
    squareMeters: Double,
    decimalSeparator: Char = '.',
): String = formatTenths(squareMeters, decimalSeparator)

/** A non-negative [value] rounded to tenths, without a needless fraction: `80`, `62.5`. */
internal fun formatTenths(
    value: Double,
    decimalSeparator: Char = '.',
): String {
    val tenths = (value * TENTHS).roundToInt()
    return if (tenths % TENTHS == 0) (tenths / TENTHS).toString() else "${tenths / TENTHS}$decimalSeparator${tenths % TENTHS}"
}

private const val TENTHS = 10

/** This speed rounded to a whole number of [unit]. */
internal fun WindSpeed.rounded(unit: WindUnit): Int =
    when (unit) {
        WindUnit.KNOTS -> toKnots().roundToInt()
        WindUnit.METERS_PER_SECOND -> metersPerSecond.roundToInt()
        WindUnit.KILOMETERS_PER_HOUR -> toKmh().roundToInt()
        WindUnit.BEAUFORT -> toBeaufort()
    }

/** What to tell the rider about [this] error. Screens never show raw errors. */
internal fun AppError.message(): StringResource =
    when (this) {
        AppError.Network -> Res.string.error_network
        AppError.Unauthorized -> Res.string.error_unauthorized
        AppError.Storage -> Res.string.error_storage
        is AppError.Server -> Res.string.error_server
        is AppError.Conflict, is AppError.Rejected, is AppError.Unknown -> Res.string.error_unknown
    }
