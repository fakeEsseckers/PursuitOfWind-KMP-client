package io.github.esseckers.pursuitofwind.feature.crew.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import io.github.esseckers.pursuitofwind.contract.ProblemCode
import io.github.esseckers.pursuitofwind.core.mvi.MviViewModel
import io.github.esseckers.pursuitofwind.core.mvi.launchFinishing
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.crew_invite_invalid
import io.github.esseckers.pursuitofwind.core.resources.crew_ride_was_cancelled
import io.github.esseckers.pursuitofwind.core.resources.crew_seat_taken
import io.github.esseckers.pursuitofwind.core.ui.message
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Crew
import io.github.esseckers.pursuitofwind.data.model.Outcome
import io.github.esseckers.pursuitofwind.feature.crew.domain.mapper.CrewChoiceMapper
import io.github.esseckers.pursuitofwind.feature.crew.domain.repository.CrewsRepository
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewDialog
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewEffect
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewIntent
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewState
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.RideAction
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.ride
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.rideAction
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.shownCrew
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The Crew tab in MVI. While it is shown, the crews, the details of the crew on screen and the [clock] come in as
 * results, and the crew's feed is fetched every [poll], so members' rides and seats appear as they happen. The first
 * time it shows, it refreshes everything.
 *
 * Seat changes show at once; the data layer puts them back when they fail, and the reason comes as
 * [CrewEffect.Failed], such as a last seat someone took first. Seat changes and cancellations finish even when the
 * rider leaves the tab meanwhile.
 */
internal class CrewViewModel(
    private val repository: CrewsRepository,
    private val clock: Clock,
    riderZone: TimeZone,
    private val poll: Duration = 30.seconds,
) : MviViewModel<CrewState, CrewIntent, CrewResult, CrewEffect>(CrewState(), CrewReducer(riderZone)) {
    /** The id of the crew on screen, which [bind] follows. Not read from the state: that would keep [bind] running. */
    private val shown = MutableStateFlow<String?>(null)

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
            repository.crews
                .onEach { shown.value = CrewChoiceMapper.shownCrew(it.value, currentState.inputs.chosen)?.id }
                .map { CrewResult.CrewsChanged(it) },
            shown.filterNotNull().flatMapLatest { repository.details(it) }.map { CrewResult.DetailsChanged(it) },
            ticks().map { CrewResult.Ticked(it) },
        ).onStart { if (!currentState.inputs.refresh.finished) refresh() }

    /** One refresh at a time; a pull while one runs does nothing. */
    private fun refresh() {
        if (currentState.inputs.refresh.refreshing) return
        dispatchResult(CrewResult.RefreshStarted)
        viewModelScope.launchFinishing(
            unfinished = AppError.Unknown("Refresh did not finish"),
            work = { repository.refreshAll(currentState.inputs.chosen) },
        ) { error -> dispatchResult(CrewResult.RefreshFinished(error)) }
    }

    private fun choose(crewId: String) {
        dispatchResult(CrewResult.Chosen(crewId))
        shown.value = currentState.inputs.shownCrew()?.id
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
                    dispatchResult(CrewResult.Chosen(crew.id))
                    shown.value = crew.id
                    dispatchResult(CrewResult.DialogShown(null))
                    postEffect(if (created) CrewEffect.Created(crew.name, crew.inviteCode) else CrewEffect.Joined(crew.name))
                    repository.refresh(crew.id)
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
            val ride = currentState.ride(rideId)?.takeIf { it.action == wanted && !it.pending } ?: return
            when (wanted) {
                RideAction.TAKE_SEAT -> {
                    launchWrite { report(repository.takeSeat(ride.id)) }
                }

                RideAction.LEAVE -> {
                    launchWrite { report(repository.leaveRide(ride.id)) }
                }

                RideAction.CANCEL -> {
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
        launchWrite {
            val error = repository.cancelRide(dialog.rideId)
            dispatchResult(CrewResult.CancelFinished(dialog.rideId))
            report(error)
        }
    }

    /** Tells the rider why a change of a ride failed, if it did. */
    private fun report(error: AppError?) {
        if (error != null) postEffect(CrewEffect.Failed(error.crewFailure()))
    }

    /** A change the rider made: it finishes even when the rider leaves the tab and this ViewModel is cleared. */
    private fun launchWrite(block: suspend () -> Unit) {
        viewModelScope.launch { withContext(NonCancellable) { block() } }
    }

    /** The clock's time now and after every [poll], each later one after fetching the crew's feed again. */
    private fun ticks(): Flow<Instant> =
        flow {
            emit(clock.now())
            while (true) {
                delay(poll)
                shown.value?.let { repository.refresh(it) }
                emit(clock.now())
            }
        }
}

/** What to tell the rider when an action on the Crew tab failed with this error. */
internal fun AppError.crewFailure(): StringResource =
    when {
        this == AppError.Conflict(ProblemCode.SEATS_FULL) -> Res.string.crew_seat_taken
        this == AppError.Conflict(ProblemCode.RIDE_CANCELLED) -> Res.string.crew_ride_was_cancelled
        this is AppError.Rejected && code == ProblemCode.INVITE_INVALID -> Res.string.crew_invite_invalid
        else -> message()
    }
