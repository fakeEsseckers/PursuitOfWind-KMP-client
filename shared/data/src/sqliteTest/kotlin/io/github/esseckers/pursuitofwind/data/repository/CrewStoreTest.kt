package io.github.esseckers.pursuitofwind.data.repository

import io.github.esseckers.pursuitofwind.contract.CrewMemberDto
import io.github.esseckers.pursuitofwind.contract.MemberRoleDto
import io.github.esseckers.pursuitofwind.contract.PersonDto
import io.github.esseckers.pursuitofwind.contract.RideDto
import io.github.esseckers.pursuitofwind.contract.RideStatusDto
import io.github.esseckers.pursuitofwind.data.fake.THURSDAY_MORNING
import io.github.esseckers.pursuitofwind.data.internal.local.CrewEntity
import io.github.esseckers.pursuitofwind.data.internal.local.PursuitOfWindDatabase
import io.github.esseckers.pursuitofwind.data.internal.local.RideEntity
import io.github.esseckers.pursuitofwind.data.internal.sync.CrewStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours

/** The cache's rules for crew members and rides, without a network. */
class CrewStoreTest {
    private val andrei = PersonDto("usr_andrei", "Andrei")
    private val alex = PersonDto("usr_alex", "Alex")
    private val ride =
        RideDto(
            id = "rid_1",
            version = 2,
            crewId = "crw_weekend",
            spotId = "salt-lagoon",
            driver = andrei,
            windowStart = THURSDAY_MORNING + 5.hours,
            windowEnd = THURSDAY_MORNING + 9.hours,
            departAt = THURSDAY_MORNING + 2.hours,
            meetingPoint = "Station",
            seatsTotal = 3,
            seatsFree = 3,
            passengers = emptyList(),
            status = RideStatusDto.SCHEDULED,
            createdAt = THURSDAY_MORNING,
        )

    private fun withStore(block: suspend (CrewStore, PursuitOfWindDatabase) -> Unit) =
        runTest {
            val database = PursuitOfWindDatabase.build(inMemoryDatabaseBuilder(), StandardTestDispatcher(testScheduler))
            try {
                block(CrewStore(database), database)
            } finally {
                database.close()
            }
        }

    private suspend fun PursuitOfWindDatabase.ride(id: String = ride.id): RideEntity? = rides().ride(id)

    @Test
    fun `a ride only moves forward`() =
        withStore { store, database ->
            store.saveNewer(ride)
            store.saveNewer(ride.copy(version = 1, seatsFree = 0))
            assertEquals(3, database.ride()?.seatsFree)

            store.saveNewer(ride.copy(version = 3, seatsFree = 2))
            assertEquals(2, database.ride()?.seatsFree)
        }

    @Test
    fun `a copy as new as the cached one replaces it and a newer one too and both keep the pending seat`() =
        withStore { store, database ->
            store.saveNewer(ride)
            store.markPending(ride.id, alex.userId, alex.displayName, take = true)

            store.saveNewer(ride.copy(seatsFree = 2))
            assertEquals(2, database.ride()?.seatsFree)
            assertEquals(RideEntity.TAKE, database.ride()?.pendingSeat)

            store.saveNewer(ride.copy(version = 3, seatsFree = 1))
            assertEquals(1, database.ride()?.seatsFree)
            assertEquals(RideEntity.TAKE, database.ride()?.pendingSeat)
            assertEquals(alex.userId, database.ride()?.pendingUserId)
        }

    @Test
    fun `a change from the feed applies only when it is newer and keeps the pending seat`() =
        withStore { store, database ->
            store.saveNewer(ride)
            store.markPending(ride.id, alex.userId, alex.displayName, take = true)

            store.putChange(ride.id, version = 2) { it.copy(seatsFree = 0) }
            assertEquals(3, database.ride()?.seatsFree)
            store.putChange(ride.id, version = 3) { it.copy(seatsFree = 1) }
            store.putChange("rid_unknown", version = 9) { it.copy(seatsFree = 0) }

            assertEquals(1, database.ride()?.seatsFree)
            assertEquals(3, database.ride()?.version)
            assertEquals(RideEntity.TAKE, database.ride()?.pendingSeat)
            assertNull(database.ride("rid_unknown"))
        }

    @Test
    fun `a pending seat is shown only where the change can work and goes again`() =
        withStore { store, database ->
            store.saveNewer(ride)

            store.markPending(ride.id, andrei.userId, andrei.displayName, take = true)
            assertNull(database.ride()?.pendingSeat)
            store.markPending(ride.id, alex.userId, alex.displayName, take = false)
            assertNull(database.ride()?.pendingSeat)
            store.markPending(ride.id, alex.userId, alex.displayName, take = true)
            assertEquals(RideEntity.TAKE, database.ride()?.pendingSeat)

            store.clearPending(ride.id)
            assertNull(database.ride()?.pendingSeat)
        }

    @Test
    fun `a reload drops only the rides it could have listed`() =
        withStore { store, database ->
            store.saveNewer(ride)
            store.saveNewer(ride.copy(id = "rid_ended"))
            val cachedBefore = store.rideIds("crw_weekend")
            // Arrives while the reload is out, so the reload's answer cannot know it.
            store.saveNewer(ride.copy(id = "rid_new"))

            store.saveRides("crw_weekend", cachedBefore, listOf(ride), THURSDAY_MORNING)

            assertEquals(listOf("rid_1", "rid_new"), store.rideIds("crw_weekend").sorted())
            assertNull(database.ride("rid_ended"))
        }

    @Test
    fun `members are added and counted`() =
        withStore { store, database ->
            database.crews().upsertCrews(listOf(CrewEntity("crw_weekend", "Weekend Crew", "WIND-4KQ7", 1, 0)))
            val owner = CrewMemberDto(andrei.userId, andrei.displayName, MemberRoleDto.OWNER, THURSDAY_MORNING)

            store.saveMembers("crw_weekend", listOf(owner), THURSDAY_MORNING)
            store.putMember("crw_weekend", CrewMemberDto(alex.userId, alex.displayName, MemberRoleDto.MEMBER, THURSDAY_MORNING))
            store.putMember("crw_weekend", owner)

            assertEquals(2, database.crews().countMembers("crw_weekend"))
            assertEquals(
                2,
                database
                    .crews()
                    .crews()
                    .first()
                    .single()
                    .memberCount,
            )
        }
}
