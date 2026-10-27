package io.github.esseckers.pursuitofwind.data.internal.local

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.PrimaryKey

// Cache tables. Instants are epoch milliseconds; nested lists are JSON in the contract's format (ContractJson), which
// keeps the schema flat without type converters.

/** A spot from the catalog or from the rider's followed spots. Sectors are JSON lists of `SectorDto`. */
@Entity(tableName = "spots")
internal data class SpotEntity(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val timeZone: String,
    val rideableSectors: String,
    val dangerSectors: String,
)

@Entity(tableName = "my_spots")
internal data class MySpotEntity(
    @PrimaryKey val spotId: String,
    val driveMinutes: Int,
    val favorite: Boolean,
)

/** A followed spot joined with its details. */
internal data class MySpotRow(
    @Embedded val spot: SpotEntity,
    val driveMinutes: Int,
    val favorite: Boolean,
)

/** One forecast hour of one spot; [models] is a JSON list of `ModelWindDto`. */
@Entity(tableName = "forecast_hours", primaryKeys = ["spotId", "start"])
internal data class ForecastHourEntity(
    val spotId: String,
    val start: Long,
    val models: String,
    val precipitationMm: Double,
    val temperatureC: Double,
    val thunderRisk: String,
    val isDaylight: Boolean,
)

@Entity(tableName = "crews")
internal data class CrewEntity(
    @PrimaryKey val id: String,
    val name: String,
    val inviteCode: String,
    val memberCount: Int,
    val createdAt: Long,
)

@Entity(tableName = "members", primaryKeys = ["crewId", "userId"])
internal data class MemberEntity(
    val crewId: String,
    val userId: String,
    val displayName: String,
    val role: String,
    val joinedAt: Long,
)

/**
 * A ride as the server last described it ([version], [seatsFree], [passengers] as a JSON list of `PersonDto`), plus
 * a seat change made on this device that the server has not answered yet ([pendingSeat] by [pendingUserId]).
 *
 * The pending change is an overlay: server updates (responses, feed events, reloads) replace the server fields and
 * keep the overlay; clearing the overlay shows the server state again. So a rollback can never undo someone else's
 * change that arrived while the request was in flight.
 */
@Entity(tableName = "rides")
internal data class RideEntity(
    @PrimaryKey val id: String,
    val version: Int,
    val crewId: String,
    val spotId: String,
    val driverId: String,
    val driverName: String,
    val windowStart: Long,
    val windowEnd: Long,
    val departAt: Long,
    val meetingPoint: String,
    val seatsTotal: Int,
    val seatsFree: Int,
    val passengers: String,
    val status: String,
    val createdAt: Long,
    val pendingSeat: String? = null,
    val pendingUserId: String? = null,
    val pendingUserName: String? = null,
) {
    /** This row with the pending change of [other] (the row it replaces). */
    fun withOverlayOf(other: RideEntity?): RideEntity =
        copy(pendingSeat = other?.pendingSeat, pendingUserId = other?.pendingUserId, pendingUserName = other?.pendingUserName)

    companion object {
        /** Values of [pendingSeat]. */
        const val TAKE = "TAKE"
        const val RELEASE = "RELEASE"
    }
}

/** When something was last fetched ([updatedAt]) and, for crew feeds, the newest event already applied ([cursor]). */
@Entity(tableName = "sync_state")
internal data class SyncStateEntity(
    @PrimaryKey val key: String,
    val updatedAt: Long?,
    val cursor: String? = null,
)

/** Keys of [SyncStateEntity]. */
internal object SyncKeys {
    const val CATALOG = "catalog"
    const val MY_SPOTS = "my_spots"
    const val CREWS = "crews"
    const val FORECAST_PREFIX = "forecast/"

    fun forecast(spotId: String): String = "$FORECAST_PREFIX$spotId"

    fun members(crewId: String): String = "members/$crewId"

    fun rides(crewId: String): String = "rides/$crewId"

    fun feed(crewId: String): String = "feed/$crewId"
}
