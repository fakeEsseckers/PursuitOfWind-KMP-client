package io.github.esseckers.pursuitofwind.data.fake

import io.github.esseckers.pursuitofwind.contract.MemberRoleDto
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.hours

class SeedTest {
    private val default = Seed.default(THURSDAY_MORNING)
    private val ride = default.rides.single()
    private val crew = default.crews.single()

    @Test
    fun `the default seed is valid`() {
        Seed.default(THURSDAY_MORNING)
    }

    @Test
    fun `rides cannot have more passengers than seats or the driver as a passenger`() {
        assertFailsWith<IllegalArgumentException> { ride.copy(seatsTotal = 1, passengerIds = listOf("usr_mira", "usr_jonas")) }
        assertFailsWith<IllegalArgumentException> { ride.copy(passengerIds = listOf("usr_mira", "usr_mira")) }
        assertFailsWith<IllegalArgumentException> { ride.copy(passengerIds = listOf(ride.driverId)) }
        assertFailsWith<IllegalArgumentException> { ride.copy(seatsTotal = 9) }
        assertFailsWith<IllegalArgumentException> { ride.copy(departAt = ride.windowStart + 1.hours) }
    }

    @Test
    fun `rides must point at known spots and crews and only carry members`() {
        assertFailsWith<IllegalArgumentException> { default.copy(rides = listOf(ride.copy(spotId = "atlantis"))) }
        assertFailsWith<IllegalArgumentException> { default.copy(rides = listOf(ride.copy(crewId = "crw_nope"))) }
        assertFailsWith<IllegalArgumentException> { default.copy(rides = listOf(ride.copy(passengerIds = listOf("usr_stranger")))) }
    }

    @Test
    fun `ids and invite codes are unique`() {
        assertFailsWith<IllegalArgumentException> { default.copy(spots = default.spots + default.spots.first()) }
        assertFailsWith<IllegalArgumentException> { default.copy(rides = listOf(ride, ride)) }
        assertFailsWith<IllegalArgumentException> {
            default.copy(crews = listOf(crew, crew.copy(id = "crw_other", inviteCode = crew.inviteCode.lowercase())))
        }
        assertFailsWith<IllegalArgumentException> {
            crew.copy(members = crew.members + SeedMember("usr_mira", "Mira again", MemberRoleDto.MEMBER))
        }
    }
}
