package io.github.esseckers.pursuitofwind.data.internal.local

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal interface SpotDao {
    @Query("SELECT * FROM spots ORDER BY name")
    fun spots(): Flow<List<SpotEntity>>

    @Query(
        "SELECT spots.*, my_spots.driveMinutes, my_spots.favorite FROM my_spots " +
            "JOIN spots ON spots.id = my_spots.spotId ORDER BY spots.name",
    )
    fun mySpots(): Flow<List<MySpotRow>>

    @Upsert
    suspend fun upsertSpots(spots: List<SpotEntity>)

    @Upsert
    suspend fun upsertMySpots(mySpots: List<MySpotEntity>)

    @Query("DELETE FROM my_spots WHERE spotId = :spotId")
    suspend fun deleteMySpot(spotId: String)

    @Query("DELETE FROM my_spots")
    suspend fun deleteMySpots()
}

@Dao
internal interface ForecastDao {
    @Query("SELECT * FROM forecast_hours WHERE spotId = :spotId ORDER BY start")
    fun hours(spotId: String): Flow<List<ForecastHourEntity>>

    @Upsert
    suspend fun upsert(hours: List<ForecastHourEntity>)

    @Query("DELETE FROM forecast_hours WHERE spotId = :spotId")
    suspend fun deleteSpot(spotId: String)
}

@Dao
internal interface CrewDao {
    @Query("SELECT * FROM crews ORDER BY createdAt")
    fun crews(): Flow<List<CrewEntity>>

    @Query("SELECT * FROM crews WHERE id = :crewId")
    suspend fun crew(crewId: String): CrewEntity?

    @Upsert
    suspend fun upsertCrews(crews: List<CrewEntity>)

    @Query("DELETE FROM crews")
    suspend fun deleteCrews()

    @Query("SELECT * FROM members WHERE crewId = :crewId ORDER BY joinedAt")
    fun members(crewId: String): Flow<List<MemberEntity>>

    @Upsert
    suspend fun upsertMembers(members: List<MemberEntity>)

    @Query("SELECT COUNT(*) FROM members WHERE crewId = :crewId")
    suspend fun countMembers(crewId: String): Int

    @Query("UPDATE crews SET memberCount = :count WHERE id = :crewId")
    suspend fun setMemberCount(
        crewId: String,
        count: Int,
    )

    @Query("DELETE FROM members")
    suspend fun deleteAllMembers()
}

@Dao
internal interface RideDao {
    @Query("SELECT * FROM rides WHERE crewId = :crewId ORDER BY departAt")
    fun rides(crewId: String): Flow<List<RideEntity>>

    @Query("SELECT * FROM rides WHERE id = :rideId")
    suspend fun ride(rideId: String): RideEntity?

    @Upsert
    suspend fun upsert(rides: List<RideEntity>)

    @Query("SELECT id FROM rides WHERE crewId = :crewId")
    suspend fun rideIds(crewId: String): List<String>

    @Query("DELETE FROM rides WHERE id IN (:ids)")
    suspend fun deleteRides(ids: List<String>)

    @Query("UPDATE rides SET pendingSeat = NULL, pendingUserId = NULL, pendingUserName = NULL WHERE id = :rideId")
    suspend fun clearPending(rideId: String)

    @Query("DELETE FROM rides")
    suspend fun deleteAll()
}

@Dao
internal interface SyncStateDao {
    @Query("SELECT * FROM sync_state WHERE `key` = :key")
    fun observe(key: String): Flow<SyncStateEntity?>

    @Query("SELECT * FROM sync_state WHERE `key` = :key")
    suspend fun get(key: String): SyncStateEntity?

    @Upsert
    suspend fun upsert(state: SyncStateEntity)

    @Query("DELETE FROM sync_state WHERE `key` = :key")
    suspend fun delete(key: String)

    /** Everything but the catalog and forecasts, which do not depend on the user. */
    @Query("DELETE FROM sync_state WHERE `key` != 'catalog' AND `key` NOT LIKE 'forecast/%'")
    suspend fun deleteUserState()
}
