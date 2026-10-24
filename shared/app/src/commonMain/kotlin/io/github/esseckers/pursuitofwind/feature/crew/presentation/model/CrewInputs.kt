package io.github.esseckers.pursuitofwind.feature.crew.presentation.model

import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.Crew
import io.github.esseckers.pursuitofwind.feature.crew.domain.model.CrewDetails
import io.github.esseckers.pursuitofwind.feature.crew.domain.model.shownCrew
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Instant

/**
 * What [CrewState] is computed from: the cached [crews] (null until they arrive), the crew the rider [chosen], the
 * [details] of the crew on screen, how the refresh is going, the rides being [cancelling], the open [dialog], and the
 * time [now], which tells rides that have left.
 */
internal data class CrewInputs(
    val crews: Cached<List<Crew>>? = null,
    val chosen: String? = null,
    val details: CrewDetails? = null,
    val refresh: CrewRefresh = CrewRefresh(),
    val cancelling: Set<String> = emptySet(),
    val dialog: CrewDialog? = null,
    val now: Instant? = null,
)

/** Whether a refresh runs, whether one has [finished] since the tab opened, and how the last one failed. */
internal data class CrewRefresh(
    val refreshing: Boolean = false,
    val finished: Boolean = false,
    val error: AppError? = null,
)

/** A dialog over the tab; [sending] while its request runs, when it stays open. */
internal sealed interface CrewDialog {
    val sending: Boolean

    /** Naming a new crew; [error] when creating it failed. */
    data class Create(
        override val sending: Boolean = false,
        val error: StringResource? = null,
    ) : CrewDialog

    /** Entering an invite code; [error] when joining failed. */
    data class Join(
        override val sending: Boolean = false,
        val error: StringResource? = null,
    ) : CrewDialog

    /** The driver confirms cancelling [rideId] to [spotName], which [passengers] were counting on. */
    data class ConfirmCancel(
        val rideId: String,
        val spotName: String,
        val passengers: Int,
    ) : CrewDialog {
        override val sending: Boolean get() = false
    }
}

/** The crew on screen ([shownCrew]). */
internal fun CrewInputs.shownCrew(): Crew? = shownCrew(crews?.value.orEmpty(), chosen)
