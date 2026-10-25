package io.github.esseckers.pursuitofwind.data.internal.mapping

import co.touchlab.kermit.Logger
import io.github.esseckers.pursuitofwind.contract.MemberRoleDto
import io.github.esseckers.pursuitofwind.contract.RideStatusDto
import io.github.esseckers.pursuitofwind.contract.SkillLevelDto
import io.github.esseckers.pursuitofwind.contract.ThunderRiskDto
import io.github.esseckers.pursuitofwind.contract.WindUnitDto
import io.github.esseckers.pursuitofwind.data.model.MemberRole
import io.github.esseckers.pursuitofwind.data.model.RideStatus
import io.github.esseckers.pursuitofwind.engine.model.ThunderRisk

// The cache stores the API's names of these enums. A name this version does not know (written by a newer version of
// the app, before a downgrade), or none, reads as the most cautious value instead of failing the flow that reads it.

/** A cached member's role; an unknown one is a plain member, who can do the least. */
internal fun cachedMemberRole(stored: String): MemberRole = cached(stored, MemberRoleDto.entries, MemberRoleDto.MEMBER).toModel()

/** A cached ride's status; an unknown one shows as cancelled, on which nothing can be done. */
internal fun cachedRideStatus(stored: String): RideStatus = cached(stored, RideStatusDto.entries, RideStatusDto.CANCELLED).toModel()

/** A cached hour's thunder risk; an unknown one counts as high, so the decision errs on the safe side. */
internal fun cachedThunderRisk(stored: String): ThunderRisk = cached(stored, ThunderRiskDto.entries, ThunderRiskDto.HIGH).toModel()

/** A cached profile's skill level; an unknown one is a beginner's, for whom the engine decides most carefully. */
internal fun cachedSkillLevel(stored: String?): SkillLevelDto = cached(stored, SkillLevelDto.entries, SkillLevelDto.BEGINNER)

/** A cached profile's wind unit; an unknown one is knots, the app's default. */
internal fun cachedWindUnit(stored: String?): WindUnitDto = cached(stored, WindUnitDto.entries, WindUnitDto.KNOTS)

private fun <T : Enum<T>> cached(
    stored: String?,
    entries: List<T>,
    cautious: T,
): T =
    entries.firstOrNull { it.name == stored } ?: cautious.also {
        Logger.withTag("Cache").w { "Unknown cached value '$stored', read as ${it.name}" }
    }
