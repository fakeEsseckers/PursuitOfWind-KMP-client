package io.github.esseckers.pursuitofwind.core.domain.mapper

import io.github.esseckers.pursuitofwind.core.domain.entity.RideActionEntity
import io.github.esseckers.pursuitofwind.core.domain.entity.RideRoleEntity
import io.github.esseckers.pursuitofwind.data.model.Ride
import io.github.esseckers.pursuitofwind.data.model.RideStatus
import kotlin.time.Instant

/** Who takes part in a crew's ride and what the rider can do on it, the same for Today and the Crew tab. */
internal object RideRulesMapper {
    /** What [userId] is on [ride]: its driver, one of its passengers, or neither. */
    fun role(
        ride: Ride,
        userId: String?,
    ): RideRoleEntity =
        when {
            userId == null -> RideRoleEntity.NONE
            ride.driver.userId == userId -> RideRoleEntity.DRIVER
            ride.passengers.any { it.userId == userId } -> RideRoleEntity.PASSENGER
            else -> RideRoleEntity.NONE
        }

    /** True once the car has left, from [Ride.departAt] on. */
    fun hasDeparted(
        ride: Ride,
        now: Instant,
    ): Boolean = ride.departAt <= now

    /** True once the ride's window is over; such a ride no longer shows. */
    fun hasEnded(
        ride: Ride,
        now: Instant,
    ): Boolean = ride.windowEnd <= now

    /**
     * What [userId] can do on [ride] at [now] (unknown: not left yet): nothing on a cancelled ride or one that has left;
     * otherwise the driver cancels, a passenger gives the seat up, and anyone else takes a free seat.
     */
    fun action(
        ride: Ride,
        userId: String?,
        now: Instant?,
    ): RideActionEntity? {
        val departed = now != null && hasDeparted(ride, now)
        return if (ride.status == RideStatus.CANCELLED || departed) {
            null
        } else {
            when (role(ride, userId)) {
                RideRoleEntity.DRIVER -> RideActionEntity.CANCEL
                RideRoleEntity.PASSENGER -> RideActionEntity.LEAVE
                RideRoleEntity.NONE -> RideActionEntity.TAKE_SEAT.takeIf { ride.seatsFree > 0 }
            }
        }
    }
}
