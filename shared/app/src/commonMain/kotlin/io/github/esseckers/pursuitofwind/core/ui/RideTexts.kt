package io.github.esseckers.pursuitofwind.core.ui

import androidx.compose.runtime.Composable
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.ride_departed
import io.github.esseckers.pursuitofwind.core.resources.ride_driver
import io.github.esseckers.pursuitofwind.core.resources.ride_full
import io.github.esseckers.pursuitofwind.core.resources.ride_pending
import io.github.esseckers.pursuitofwind.core.resources.ride_seats_free
import io.github.esseckers.pursuitofwind.core.resources.ride_you_departed
import io.github.esseckers.pursuitofwind.core.resources.ride_you_drive
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Who drives a ride and when it leaves, or left once [departed]: "Andrei drives, leaves at 08:30", "You left at 08:30". */
@Composable
internal fun rideDriverText(
    driverName: String,
    youDrive: Boolean,
    departed: Boolean,
    departAt: String,
): String =
    when {
        youDrive && departed -> stringResource(Res.string.ride_you_departed, departAt)
        youDrive -> stringResource(Res.string.ride_you_drive, departAt)
        departed -> stringResource(Res.string.ride_departed, driverName, departAt)
        else -> stringResource(Res.string.ride_driver, driverName, departAt)
    }

/** A ride's free seats: a change on its way ([pending]), full, or how many are left. */
@Composable
internal fun rideSeatsText(
    seatsFree: Int,
    pending: Boolean,
): String =
    when {
        pending -> stringResource(Res.string.ride_pending)
        seatsFree == 0 -> stringResource(Res.string.ride_full)
        else -> pluralStringResource(Res.plurals.ride_seats_free, seatsFree, seatsFree)
    }
