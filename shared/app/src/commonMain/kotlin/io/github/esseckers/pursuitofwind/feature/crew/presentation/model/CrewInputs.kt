package io.github.esseckers.pursuitofwind.feature.crew.presentation.model

import io.github.esseckers.pursuitofwind.core.refresh.RefreshStatus
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.Crew
import io.github.esseckers.pursuitofwind.feature.crew.domain.entity.CrewDetailsEntity
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Instant

/**
 * What [CrewState] is computed from: the cached [crews] (null until they arrive), the crew the rider [chosen], a crew
 * just made or joined that is [arriving] until the list holds it (or the cache turns out not to after its first
 * refresh), the crew [shown] on screen, the [details] of that crew, how the refresh is going, the rides being
 * [cancelling], the open [dialog], and the time [now], which tells rides that have left.
 *
 * The reducer works out [shown]: the [arriving] crew while the list does not hold it yet (a list read before it was
 * saved can still come in and would otherwise put the old crew back for a moment), else [crewToShow].
 */
internal data class CrewInputs(
    val crews: Cached<List<Crew>>? = null,
    val chosen: String? = null,
    val arriving: String? = null,
    val shown: String? = null,
    val details: CrewDetailsEntity? = null,
    val refresh: RefreshStatus = RefreshStatus(),
    val cancelling: Set<String> = emptySet(),
    val dialog: CrewDialog? = null,
    val now: Instant? = null,
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

/** The crew on screen, once the cached list holds it. */
internal fun CrewInputs.shownCrew(): Crew? = crews?.value?.firstOrNull { it.id == shown }

/** The crew the rider [chosen] while it is one of [crews], else the first; null without crews. */
internal fun crewToShow(
    crews: List<Crew>,
    chosen: String?,
): Crew? = crews.firstOrNull { it.id == chosen } ?: crews.firstOrNull()
