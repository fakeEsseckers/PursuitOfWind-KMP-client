package io.github.esseckers.pursuitofwind.core.domain.mapper

import io.github.esseckers.pursuitofwind.core.domain.entity.RideActionEntity
import io.github.esseckers.pursuitofwind.core.domain.entity.RideRoleEntity
import io.github.esseckers.pursuitofwind.data.model.Person
import io.github.esseckers.pursuitofwind.data.model.RideStatus
import io.github.esseckers.pursuitofwind.feature.today.SATURDAY
import io.github.esseckers.pursuitofwind.feature.today.at
import io.github.esseckers.pursuitofwind.feature.today.ride
import io.github.esseckers.pursuitofwind.feature.today.withPassengers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class RideRulesMapperTest {
    private val alex = Person("usr_alex", "Alex")
    private val mira = Person("usr_mira", "Mira")

    /** Andrei's ride, leaving Saturday at 09:00 for the 13:00–17:00 window. */
    private val ride = ride("rid_1")
    private val before = at(SATURDAY, 8)

    @Test
    fun `the rider drives or rides along or neither`() {
        assertEquals(RideRoleEntity.DRIVER, RideRulesMapper.role(ride, "usr_andrei"))
        assertEquals(RideRoleEntity.PASSENGER, RideRulesMapper.role(ride.withPassengers(alex), alex.userId))
        assertEquals(RideRoleEntity.NONE, RideRulesMapper.role(ride.withPassengers(mira), alex.userId))
        assertEquals(RideRoleEntity.NONE, RideRulesMapper.role(ride, null))
    }

    @Test
    fun `a ride has left from its departure on and ended from the end of its window`() {
        assertFalse(RideRulesMapper.hasDeparted(ride, ride.departAt - 1.minutes))
        assertTrue(RideRulesMapper.hasDeparted(ride, ride.departAt))
        assertFalse(RideRulesMapper.hasEnded(ride, ride.windowEnd - 1.minutes))
        assertTrue(RideRulesMapper.hasEnded(ride, ride.windowEnd))
    }

    @Test
    fun `what the rider can do depends on their role and the seats`() {
        val rows =
            listOf(
                Triple(ride, "usr_andrei", RideActionEntity.CANCEL),
                Triple(ride.withPassengers(alex), alex.userId, RideActionEntity.LEAVE),
                Triple(ride, alex.userId, RideActionEntity.TAKE_SEAT),
                Triple(ride.withPassengers(mira, Person("usr_jonas", "Jonas"), Person("usr_lena", "Lena")), alex.userId, null),
            )

        rows.forEach { (offered, user, expected) ->
            assertEquals(
                expected,
                RideRulesMapper.action(offered, user, before),
                "$user on $offered",
            )
        }
    }

    @Test
    fun `nothing can be done on a cancelled ride or one that has left even by its driver`() {
        assertEquals(null, RideRulesMapper.action(ride.copy(status = RideStatus.CANCELLED), alex.userId, before))
        assertEquals(null, RideRulesMapper.action(ride, "usr_andrei", ride.departAt))
        assertEquals(RideActionEntity.TAKE_SEAT, RideRulesMapper.action(ride, alex.userId, now = null))
    }
}
