package io.github.esseckers.pursuitofwind.feature.crew.presentation.viewmodel

import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.Crew
import io.github.esseckers.pursuitofwind.feature.crew.domain.entity.CrewDetailsEntity
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewDialog
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Instant

/** What happened, as the reducer sees it. */
internal sealed interface CrewResult {
    /** The rider's crews are [cached]. */
    data class CrewsChanged(
        val cached: Cached<List<Crew>>,
    ) : CrewResult

    data class DetailsChanged(
        val details: CrewDetailsEntity,
    ) : CrewResult

    data object RefreshStarted : CrewResult

    data class RefreshFinished(
        val error: AppError?,
    ) : CrewResult

    /** Which crew the rider wants on screen. */
    sealed interface Choice : CrewResult

    /** The rider picked [crewId]. */
    data class Chosen(
        val crewId: String,
    ) : Choice

    /** The rider created or joined [crewId]: it shows at once, before the cached list holds it. */
    data class Arrived(
        val crewId: String,
    ) : Choice

    /** The first refresh of [crewId], which [Arrived], is done: from here the cached list decides what shows. */
    data class ArrivalSettled(
        val crewId: String,
    ) : Choice

    data class Ticked(
        val now: Instant,
    ) : CrewResult

    /** [dialog] opens over the tab; null closes the one that is open. */
    data class DialogShown(
        val dialog: CrewDialog?,
    ) : CrewResult

    /** The rider closes the open dialog; one that is sending stays until its request ends. */
    data object DialogDismissed : CrewResult

    /** The open create or join dialog sends its request. */
    data object DialogSending : CrewResult

    /** The open create or join dialog failed with [reason] and stays open. */
    data class DialogFailed(
        val reason: StringResource,
    ) : CrewResult

    data class CancelStarted(
        val rideId: String,
    ) : CrewResult

    data class CancelFinished(
        val rideId: String,
    ) : CrewResult
}
