package io.github.esseckers.pursuitofwind.data.internal.mapping

import co.touchlab.kermit.Logger
import io.github.esseckers.pursuitofwind.contract.MemberRoleDto
import io.github.esseckers.pursuitofwind.contract.RideStatusDto
import io.github.esseckers.pursuitofwind.contract.ThunderRiskDto
import io.github.esseckers.pursuitofwind.data.model.MemberRole
import io.github.esseckers.pursuitofwind.data.model.RideStatus
import io.github.esseckers.pursuitofwind.engine.model.ThunderRisk

// The cache stores the API's names of these enums. A name this version does not know (written by a newer version of
// the app, before a downgrade) reads as the most cautious value instead of failing the flow that reads it.

/** A cached member's role; an unknown one is a plain member, who can do the least. */
internal fun cachedMemberRole(stored: String): MemberRole = cached(stored, MemberRoleDto.entries, MemberRoleDto.MEMBER).toModel()

/** A cached ride's status; an unknown one shows as cancelled, on which nothing can be done. */
internal fun cachedRideStatus(stored: String): RideStatus = cached(stored, RideStatusDto.entries, RideStatusDto.CANCELLED).toModel()

/** A cached hour's thunder risk; an unknown one counts as high, so the decision errs on the safe side. */
internal fun cachedThunderRisk(stored: String): ThunderRisk = cached(stored, ThunderRiskDto.entries, ThunderRiskDto.HIGH).toModel()

private fun <T : Enum<T>> cached(
    stored: String,
    entries: List<T>,
    cautious: T,
): T =
    entries.firstOrNull { it.name == stored } ?: cautious.also {
        Logger.withTag("Cache").w { "Unknown cached value '$stored', read as ${it.name}" }
    }
