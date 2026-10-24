package io.github.esseckers.pursuitofwind.feature.crew.presentation.model

import io.github.esseckers.pursuitofwind.engine.model.SpotId
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource

/** One-off events of the Crew tab, handled by the app outside the screen. */
internal sealed interface CrewEffect {
    /** Open the system share sheet with an invitation to [crewName] and its [inviteCode]. */
    data class ShareInvite(
        val crewName: String,
        val inviteCode: String,
    ) : CrewEffect

    /** The rider created [crewName]; the snackbar offers to share its [inviteCode]. */
    data class Created(
        val crewName: String,
        val inviteCode: String,
    ) : CrewEffect

    data class Joined(
        val crewName: String,
    ) : CrewEffect

    /** Something the rider did on a ride failed; [reason] says why, such as a seat someone else took first. */
    data class Failed(
        val reason: StringResource,
    ) : CrewEffect

    data class OpenSpot(
        val spotId: SpotId,
        val date: LocalDate,
    ) : CrewEffect
}
