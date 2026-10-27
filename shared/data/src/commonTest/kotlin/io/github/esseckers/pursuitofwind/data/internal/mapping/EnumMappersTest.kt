package io.github.esseckers.pursuitofwind.data.internal.mapping

import io.github.esseckers.pursuitofwind.contract.MemberRoleDto
import io.github.esseckers.pursuitofwind.contract.RideStatusDto
import io.github.esseckers.pursuitofwind.contract.SkillLevelDto
import io.github.esseckers.pursuitofwind.contract.ThunderRiskDto
import io.github.esseckers.pursuitofwind.contract.WindUnitDto
import io.github.esseckers.pursuitofwind.data.model.MemberRole
import io.github.esseckers.pursuitofwind.data.model.RideStatus
import io.github.esseckers.pursuitofwind.data.model.WindUnit
import io.github.esseckers.pursuitofwind.engine.model.SkillLevel
import io.github.esseckers.pursuitofwind.engine.model.ThunderRisk
import kotlin.test.Test
import kotlin.test.assertEquals

class EnumMappersTest {
    @Test
    fun `every value goes to the API and back unchanged`() {
        SkillLevel.entries.forEach { assertEquals(it, it.toDto().toModel()) }
        WindUnit.entries.forEach { assertEquals(it, it.toDto().toModel()) }
        SkillLevelDto.entries.forEach { assertEquals(it, it.toModel().toDto()) }
        WindUnitDto.entries.forEach { assertEquals(it, it.toModel().toDto()) }
    }

    @Test
    fun `values the API sends keep their name in the domain`() {
        assertEquals(MemberRoleDto.entries.map { it.name }, MemberRoleDto.entries.map { it.toModel().name })
        assertEquals(RideStatusDto.entries.map { it.name }, RideStatusDto.entries.map { it.toModel().name })
        assertEquals(ThunderRiskDto.entries.map { it.name }, ThunderRiskDto.entries.map { it.toModel().name })
    }

    @Test
    fun `cached names read back as what was stored`() {
        assertEquals(MemberRole.entries, MemberRoleDto.entries.map { cachedMemberRole(it.name) })
        assertEquals(RideStatus.entries, RideStatusDto.entries.map { cachedRideStatus(it.name) })
        assertEquals(ThunderRisk.entries, ThunderRiskDto.entries.map { cachedThunderRisk(it.name) })
    }

    @Test
    fun `a cached name this version does not know reads as the most cautious value`() {
        assertEquals(MemberRole.MEMBER, cachedMemberRole("ADMIN"))
        assertEquals(RideStatus.CANCELLED, cachedRideStatus("POSTPONED"))
        assertEquals(ThunderRisk.HIGH, cachedThunderRisk("EXTREME"))
    }
}
