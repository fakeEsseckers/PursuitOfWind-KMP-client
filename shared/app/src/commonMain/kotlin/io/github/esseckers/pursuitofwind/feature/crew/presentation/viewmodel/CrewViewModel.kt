package io.github.esseckers.pursuitofwind.feature.crew.presentation.viewmodel

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.viewModelScope
import io.github.esseckers.pursuitofwind.contract.ProblemCode
import io.github.esseckers.pursuitofwind.core.domain.entity.RideActionEntity
import io.github.esseckers.pursuitofwind.core.domain.mapper.RideRulesMapper
import io.github.esseckers.pursuitofwind.core.launchFinishing
import io.github.esseckers.pursuitofwind.core.mvi.MviViewModel
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.crew_invite_invalid
import io.github.esseckers.pursuitofwind.core.resources.crew_ride_was_cancelled
import io.github.esseckers.pursuitofwind.core.resources.crew_seat_taken
import io.github.esseckers.pursuitofwind.core.ticks
import io.github.esseckers.pursuitofwind.core.ui.message
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Crew
import io.github.esseckers.pursuitofwind.data.model.Outcome
import io.github.esseckers.pursuitofwind.feature.crew.domain.repository.CrewsRepository
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewDialog
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewEffect
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewIntent
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewState
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.crewToShow
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.ride
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.rideAction
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.shownCrew
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * The Crew tab in MVI. While it is shown, the crews, the details of the crew on screen and the [clock] (every [TICK])
 * come in as results, and the crew's feed is fetched every [POLL], so members' rides and seats appear as they happen.
 * The reducer works out which crew is on screen (a crew just made or joined, else the rider's pick while it is still
 * theirs, else the first), and [bind] follows it through [select]. The first time it shows, it refreshes everything.
 *
 * Seat changes show at once; the data layer puts them back when they fail, and the reason comes as
 * [CrewEffect.Failed], such as a last seat someone took first. The repository finishes seat changes and cancellations
 * even when the rider leaves the tab meanwhile.
 */
internal class CrewViewModel(
    private val repository: CrewsRepository,
    private val clock: Clock,
    riderZone: TimeZone,
) : MviViewModel<CrewState, CrewIntent, CrewResult, CrewEffect>(CrewState(), CrewReducer(riderZone)) {
    override fun onIntent(intent: CrewIntent) {
        when (intent) {
            CrewIntent.Refresh -> refresh()
            is CrewIntent.ChooseCrew -> choose(intent.crewId)
            CrewIntent.StartCreate -> dispatchResult(CrewResult.DialogShown(CrewDialog.Create()))
            CrewIntent.StartJoin -> dispatchResult(CrewResult.DialogShown(CrewDialog.Join()))
            CrewIntent.CloseDialog -> dispatchResult(CrewResult.DialogDismissed)
            is CrewIntent.CreateCrew -> submit(created = true) { repository.create(intent.name.trim()) }
            is CrewIntent.JoinCrew -> submit(created = false) { repository.join(intent.inviteCode) }
            CrewIntent.ShareInvite -> currentState.inputs.shownCrew()?.let { postEffect(CrewEffect.ShareInvite(it.name, it.inviteCode)) }
            is CrewIntent.TakeSeat, is CrewIntent.LeaveRide, is CrewIntent.CancelRide, CrewIntent.ConfirmCancel -> onRide(intent)
            is CrewIntent.OpenSpot -> postEffect(CrewEffect.OpenSpot(intent.spotId, intent.date))
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun bind(): Flow<CrewResult> =
        merge(
            repository.crews.map { CrewResult.CrewsChanged(it) },
            select { it.inputs.shown }.filterNotNull().flatMapLatest { repository.details(it) }.map { CrewResult.DetailsChanged(it) },
            clock.ticks(TICK).map { CrewResult.Ticked(it) },
            polling(),
        ).onStart { if (!currentState.inputs.refresh.finished) refresh() }

    /**
     * One refresh at a time; a pull while one runs does nothing. The crew whose members and rides it fetches is picked
     * here from the fresh list, by the same rule as the crew on screen.
     */
    private fun refresh() {
        if (currentState.inputs.refresh.refreshing) return
        dispatchResult(CrewResult.RefreshStarted)
        viewModelScope.launchFinishing(
            unfinished = AppError.Unknown("Refresh did not finish"),
            work = { repository.refreshAll { crews -> crewToShow(crews, currentState.inputs.chosen)?.id } },
        ) { error -> dispatchResult(CrewResult.RefreshFinished(error)) }
    }

    private fun choose(crewId: String) {
        dispatchResult(CrewResult.Chosen(crewId))
        viewModelScope.launch { repository.refresh(crewId) }
    }

    /** Creates or joins a crew from the open dialog, which stays open with the reason when that fails. */
    private fun submit(
        created: Boolean,
        request: suspend () -> Outcome<Crew>,
    ) {
        if (currentState.inputs.dialog?.sending == true) return
        dispatchResult(CrewResult.DialogSending)
        viewModelScope.launch {
            when (val outcome = request()) {
                is Outcome.Success -> {
                    val crew = outcome.value
                    // Shown at once, though the cached list may not hold it yet: the tab waits for it, not for the old one.
                    dispatchResult(CrewResult.Arrived(crew.id))
                    dispatchResult(CrewResult.DialogShown(null))
                    postEffect(if (created) CrewEffect.Created(crew.name, crew.inviteCode) else CrewEffect.Joined(crew.name))
                    repository.refresh(crew.id)
                    // Not in the cache even now (left on another device, another account): the list decides.
                    if (repository.crews
                            .first()
                            .value
                            .none { it.id == crew.id }
                    ) {
                        dispatchResult(CrewResult.ArrivalMissing(crew.id))
                    }
                }

                is Outcome.Failure -> {
                    dispatchResult(CrewResult.DialogFailed(outcome.error.crewFailure()))
                }
            }
        }
    }

    /**
     * Takes or leaves a seat, or asks to confirm a cancellation, when that is what the ride offers now (not while a
     * change of it is pending). The data layer shows a seat change at once.
     */
    private fun onRide(intent: CrewIntent) {
        if (intent == CrewIntent.ConfirmCancel) {
            cancel()
        } else {
            val (rideId, wanted) = intent.rideAction() ?: return
            // What the cached ride allows at the clock's time now, not at the screen's last tick: it may have left since.
            val details = currentState.inputs.details
            val ride =
                currentState.ride(rideId)?.takeIf { shown ->
                    val cached = details?.rides?.value?.firstOrNull { it.id == rideId }
                    !shown.pending && cached != null && RideRulesMapper.action(cached, details.userId, clock.now()) == wanted
                } ?: return
            when (wanted) {
                RideActionEntity.TAKE_SEAT -> {
                    viewModelScope.launch { report(repository.takeSeat(ride.id)) }
                }

                RideActionEntity.LEAVE -> {
                    viewModelScope.launch { report(repository.leaveRide(ride.id)) }
                }

                RideActionEntity.CANCEL -> {
                    dispatchResult(
                        CrewResult.DialogShown(CrewDialog.ConfirmCancel(ride.id, ride.spotName, ride.passengers.size)),
                    )
                }
            }
        }
    }

    private fun cancel() {
        val dialog = currentState.inputs.dialog as? CrewDialog.ConfirmCancel ?: return
        dispatchResult(CrewResult.DialogShown(null))
        dispatchResult(CrewResult.CancelStarted(dialog.rideId))
        viewModelScope.launchFinishing(
            unfinished = AppError.Unknown("Cancelling did not finish"),
            work = { repository.cancelRide(dialog.rideId) },
        ) { error ->
            dispatchResult(CrewResult.CancelFinished(dialog.rideId))
            report(error)
        }
    }

    /** Tells the rider why a change of a ride failed, if it did. */
    private fun report(error: AppError?) {
        if (error != null) postEffect(CrewEffect.Failed(error.crewFailure()))
    }

    /**
     * Fetches the feed of the crew on screen every [POLL]; what changed comes in through the cache. Apart from the clock,
     * so a slow fetch never holds back rides turning "departed".
     */
    private fun polling(): Flow<CrewResult> =
        flow {
            while (true) {
                delay(POLL)
                currentState.inputs.shown?.let { repository.refresh(it) }
            }
        }

    private companion object {
        /** How often the crew's feed is fetched while the tab is shown, so members' rides and seats appear. */
        val POLL = 30.seconds

        /** How often the clock is read, so rides turn "departed" without a fetch. */
        val TICK = 30.seconds
    }
}

/** What to tell the rider when an action on the Crew tab failed with this error. Internal only for its tests. */
@VisibleForTesting
internal fun AppError.crewFailure(): StringResource =
    when {
        this == AppError.Conflict(ProblemCode.SEATS_FULL) -> Res.string.crew_seat_taken
        this == AppError.Conflict(ProblemCode.RIDE_CANCELLED) -> Res.string.crew_ride_was_cancelled
        this is AppError.Rejected && code == ProblemCode.INVITE_INVALID -> Res.string.crew_invite_invalid
        else -> message()
    }
